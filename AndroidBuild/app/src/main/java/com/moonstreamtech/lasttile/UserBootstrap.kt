package com.moonstreamtech.lasttile

import android.content.Context
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Manages Firebase Anonymous Auth sign-in and first-time user-doc
 * creation in Firestore.
 *
 * Called once from [LastTileApplication.onCreate]; all subsequent
 * callers (score submission, leaderboard fetch) observe [authState]
 * to decide whether the backend is available.
 *
 * Offline / sign-in failure → [AuthState.Offline]. The game keeps
 * working with local-only scores; the pending best score is cached in
 * SharedPreferences and synced on the next successful auth.
 */
object UserBootstrap {

    private const val TAG = "UserBootstrap"
    private const val PREFS_NAME = "lasttile_state"
    private const val PREF_PENDING_SCORE = "pending_best_score"

    // Long-lived scope tied to the Application lifecycle via SupervisorJob
    // so a single coroutine failure doesn't cancel sibling work.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    sealed class AuthState {
        object Initializing : AuthState()
        data class Ready(val uid: String) : AuthState()
        object Offline : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Convenience getter used by submitBestScore to avoid repeated casts.
    val uid: String?
        get() = (_authState.value as? AuthState.Ready)?.uid

    // Application context stored once during init. Safe to hold as a
    // field — Application context is a singleton and never leaks.
    private lateinit var appContext: Context

    /**
     * Entry point. Called from [LastTileApplication.onCreate].
     *
     * Launches a background coroutine that:
     *   1. Signs in anonymously (reuses cached token if present).
     *   2. Creates the Firestore user doc on first-ever launch.
     *   3. Retries any score cached offline since the last session.
     *   4. Sets [authState] to [AuthState.Ready] or [AuthState.Offline].
     *
     * Idempotent: safe to call multiple times (the Firestore `set` at
     * step 2 is gated on `!snapshot.exists()`).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            runCatching {
                val auth = Firebase.auth
                // Re-use the cached anonymous session when available so
                // we skip the network round-trip on every subsequent launch.
                val user = auth.currentUser
                    ?: auth.signInAnonymously().await().user
                    ?: error("signInAnonymously returned a null user")

                Log.i(TAG, "Signed in anonymously, uid=${user.uid}")
                ensureUserDoc(user.uid)
                retryPendingScore(user.uid)
                _authState.value = AuthState.Ready(user.uid)
                Log.i(TAG, "Bootstrap complete")
            }.onFailure { e ->
                Log.w(TAG, "Bootstrap failed; entering offline mode", e)
                _authState.value = AuthState.Offline
            }
        }
    }

    // ── Score submission ─────────────────────────────────────────────────

    /**
     * Write [score] to `users/{uid}.bestScore` via a Firestore transaction
     * that only updates when the new score is strictly higher than the
     * existing one (monotone-increasing invariant).
     *
     * Fire-and-forget from the caller's perspective. If auth is not ready
     * (offline / still initialising) the score is queued in SharedPreferences
     * and replayed the next time [init] completes successfully.
     */
    fun submitBestScore(score: Long) {
        val currentUid = uid
        if (currentUid == null) {
            savePendingScore(score)
            return
        }
        scope.launch {
            runCatching { writeBestScore(currentUid, score) }
                .onFailure { e ->
                    Log.w(TAG, "submitBestScore failed, queuing score=$score", e)
                    savePendingScore(score)
                }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Creates the Firestore user doc on the very first launch for this
     * device's anonymous UID. Skips silently when the doc already exists
     * so subsequent launches are a single no-op `get`.
     */
    private suspend fun ensureUserDoc(uid: String) {
        val db = Firebase.firestore
        val ref = db.collection("users").document(uid)
        if (ref.get().await().exists()) {
            Log.d(TAG, "User doc already exists, skipping creation")
            return
        }
        val numericId = allocateNumericId(uid)
        val numericIdStr = "#${"%06d".format(numericId)}"
        ref.set(
            mapOf(
                "userId" to uid,
                "displayName" to numericIdStr,
                "numericId" to numericIdStr,
                "lastUsernameChangeAt" to null,
                "bestScore" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Log.i(TAG, "Created user doc uid=$uid displayName=$numericIdStr")
    }

    /**
     * Reserves a unique 6-digit numeric ID in `numericIds/NNNNNN` via a
     * Firestore transaction. Up to 5 attempts handle the (rare) case
     * where the randomly chosen number is already taken. On exhaustion
     * a final random value is returned unreserved — at current user
     * scale (< 10 K) the birthday probability of a 6-digit collision
     * run of 5 is negligible.
     *
     * v0.2.0: moved out of `usernames/{NNNNNN}` so that auto-assigned
     * "#NNNNNN" identifiers no longer occupy slots in the username
     * reservation collection. This lets a player pick a real name later
     * without their own auto-name reservation getting in the way and
     * keeps the username collection free of internal placeholders.
     */
    private suspend fun allocateNumericId(uid: String): Int {
        val db = Firebase.firestore

        // Local exception used purely to signal a collision inside the
        // transaction lambda without losing the cause through wrapping.
        class Collision : RuntimeException("numericId already reserved")

        repeat(5) { attempt ->
            val candidate = Random.nextInt(100_000, 1_000_000)
            val reservationRef = db.collection("numericIds").document("%06d".format(candidate))
            try {
                db.runTransaction { tx ->
                    if (tx.get(reservationRef).exists()) throw Collision()
                    // Field name "userId" matches the Firestore rules
                    // schema (firestore.rules at repo root) — strict
                    // rules expect every reservation doc to carry the
                    // claimant's uid in this exact key. Same convention
                    // as usernames/{name}.userId in [UsernameRepository].
                    tx.set(reservationRef, mapOf("userId" to uid))
                }.await()
                Log.i(TAG, "Allocated numericId=$candidate (attempt ${attempt + 1})")
                return candidate
            } catch (_: Collision) {
                Log.d(TAG, "numericId=$candidate already taken, retrying")
            }
        }
        // Unreserved fallback (extremely unlikely at current scale).
        val fallback = Random.nextInt(100_000, 1_000_000)
        Log.w(TAG, "All 5 numericId attempts collided; using unreserved fallback=$fallback")
        return fallback
    }

    /**
     * Monotone-increasing Firestore write: only updates `bestScore` when
     * [score] exceeds the value currently in the doc.
     */
    private suspend fun writeBestScore(uid: String, score: Long) {
        val db = Firebase.firestore
        val ref = db.collection("users").document(uid)
        db.runTransaction { tx ->
            val existing = tx.get(ref).getLong("bestScore") ?: 0L
            if (score > existing) {
                tx.update(ref, "bestScore", score)
                Log.i(TAG, "bestScore updated $existing → $score for uid=$uid")
            }
        }.await()
    }

    /**
     * If there is a pending score from a previous offline session, write it
     * to Firestore now that auth is ready, then clear the cached value.
     */
    private suspend fun retryPendingScore(uid: String) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getLong(PREF_PENDING_SCORE, 0L)
        if (pending <= 0L) return
        runCatching { writeBestScore(uid, pending) }
            .onSuccess {
                prefs.edit().remove(PREF_PENDING_SCORE).apply()
                Log.i(TAG, "Synced pending_best_score=$pending for uid=$uid")
            }
            .onFailure { e ->
                Log.w(TAG, "retryPendingScore failed; will retry next session", e)
            }
    }

    /** Persist [score] to SharedPreferences so it survives process death. */
    private fun savePendingScore(score: Long) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getLong(PREF_PENDING_SCORE, 0L)
        if (score > current) {
            prefs.edit().putLong(PREF_PENDING_SCORE, score).apply()
            Log.d(TAG, "Queued pending_best_score=$score (was $current)")
        }
    }
}
