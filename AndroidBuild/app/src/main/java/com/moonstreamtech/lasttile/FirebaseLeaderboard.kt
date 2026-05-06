package com.moonstreamtech.lasttile

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Score submission and leaderboard fetch from Firestore.
 *
 * Submit path is fire-and-forget; failures are logged but never affect
 * gameplay. Local leaderboard is the device's source of truth and is
 * unaffected by Firestore connectivity.
 *
 * Fetch path returns a [LoadResult] sealed class that the UI consumes.
 * Top 100 users by bestScore are queried in one call; the current
 * player's rank is computed via a count() aggregation when they are
 * outside the top 100 and have a non-zero score. Players with score 0
 * (fresh installs that haven't finished a run) get a [LeaderboardEntry]
 * with rank = null so the UI can pin them to the bottom with em-dash
 * placeholders — this keeps tutorial step 6 (tap-your-row-to-rename)
 * unblocked for first-time players.
 *
 * In-memory cache holds the most recent fetch for [CACHE_TTL_MS] (60
 * minutes per the user requirement). The cache is per-process — kill
 * the app and the cache is gone, which is intentional.
 */
object FirebaseLeaderboard {
    private const val TAG = "FirebaseLeaderboard"
    private const val TOP_LIMIT = 100
    private const val CACHE_TTL_MS = 60 * 60 * 1000L  // 60 minutes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class LeaderboardEntry(
        val uid: String,
        val displayName: String,
        val numericId: String,
        val bestScore: Long,
        // null when bestScore == 0 (player has no submitted run yet).
        // The UI renders an em-dash placeholder for null ranks.
        val rank: Int?,
        val isCurrentPlayer: Boolean
    )

    sealed class LoadResult {
        data class Success(
            val topEntries: List<LeaderboardEntry>,
            val playerEntry: LeaderboardEntry?,  // null only when no auth
            val playerInTop: Boolean,            // true if current player is in topEntries
            val fetchedAtMs: Long
        ) : LoadResult()
        data class Failure(val reason: String) : LoadResult()
    }

    @Volatile private var cachedResult: LoadResult.Success? = null

    // Defensive read for numericId: old docs stored it as Long/Int; new docs
    // store it as a "#NNNNNN" String. Both are accepted so the leaderboard
    // never crashes on already-written documents.
    private fun readNumericId(doc: com.google.firebase.firestore.DocumentSnapshot): String {
        return when (val raw = doc.get("numericId")) {
            is String -> raw
            is Long -> "#${"%06d".format(raw)}"
            is Number -> "#${"%06d".format(raw.toLong())}"
            else -> "#000000"
        }
    }

    /**
     * Write [score] to Firestore via a monotone-increasing transaction.
     * Invalidates the in-memory cache on a successful write so the next
     * leaderboard open reflects the new score immediately.
     *
     * No-ops when auth is not ready (uid is null). Failures are logged
     * silently — [UserBootstrap.retryPendingScore] handles any score
     * that was queued during an earlier offline session.
     */
    fun submitBestScore(score: Long) {
        if (score <= 0L) return
        val uid = UserBootstrap.uid ?: run {
            Log.i(TAG, "submit: no auth uid, skipping")
            return
        }
        scope.launch {
            try {
                val firestore = Firebase.firestore
                val userRef = firestore.collection("users").document(uid)
                firestore.runTransaction { tx ->
                    val snap = tx.get(userRef)
                    if (!snap.exists()) {
                        Log.w(TAG, "submit: user doc missing for $uid")
                        return@runTransaction null
                    }
                    val current = snap.getLong("bestScore") ?: 0L
                    if (score > current) {
                        tx.update(userRef, "bestScore", score)
                        Log.i(TAG, "Updated bestScore $current → $score")
                        // Invalidate cache so the next leaderboard open
                        // shows fresh data including this submission.
                        cachedResult = null
                    } else {
                        Log.i(TAG, "Skip submit: score $score <= current $current")
                    }
                    null
                }.await()
            } catch (e: Exception) {
                Log.w(TAG, "submitBestScore failed", e)
            }
        }
    }

    /**
     * Returns cached result if fresh (< [CACHE_TTL_MS]); otherwise fetches
     * top 100 + player rank from Firestore. Pass [forceRefresh] = true to
     * bypass the cache (e.g. when the user taps the Refresh button).
     */
    suspend fun loadLeaderboard(forceRefresh: Boolean = false): LoadResult {
        if (!forceRefresh) {
            val cached = cachedResult
            val ageMs = if (cached != null) System.currentTimeMillis() - cached.fetchedAtMs else -1L
            if (cached != null && ageMs < CACHE_TTL_MS) {
                Log.i(TAG, "loadLeaderboard: returning cached result (${cached.topEntries.size} entries)")
                return cached
            }
        }

        return try {
            val firestore = Firebase.firestore
            val uid = Firebase.auth.currentUser?.uid
                ?: return LoadResult.Failure("not authenticated")

            // Top 100 query. Excludes bestScore == 0 so users who installed
            // but never finished a run don't fill the board with zero rows.
            val topSnapshot = firestore.collection("users")
                .whereGreaterThan("bestScore", 0L)
                .orderBy("bestScore", Query.Direction.DESCENDING)
                .limit(TOP_LIMIT.toLong())
                .get()
                .await()

            val topEntries = topSnapshot.documents.mapIndexed { idx, doc ->
                LeaderboardEntry(
                    uid = doc.id,
                    displayName = doc.getString("displayName") ?: "?",
                    numericId = readNumericId(doc),
                    bestScore = doc.getLong("bestScore") ?: 0L,
                    rank = idx + 1,
                    isCurrentPlayer = (doc.id == uid)
                )
            }

            val playerInTop = topEntries.any { it.uid == uid }

            // Player entry is ALWAYS constructed from the user doc (regardless
            // of bestScore) so that tutorial step 6 — which spotlights the
            // player's row inside the leaderboard dialog — has a target on a
            // fresh install. Players with score 0 get rank = null and are
            // pinned at the bottom with em-dash placeholders in the UI.
            val playerEntry: LeaderboardEntry? = if (playerInTop) {
                topEntries.first { it.uid == uid }
            } else {
                val playerDoc = firestore.collection("users").document(uid).get().await()
                if (!playerDoc.exists()) {
                    null
                } else {
                    val playerScore = playerDoc.getLong("bestScore") ?: 0L
                    val playerRank: Int? = if (playerScore <= 0L) {
                        // No submitted run yet → no rank, skip the count()
                        // aggregation (saves a billed read on every fresh
                        // first-launch leaderboard open).
                        null
                    } else {
                        val countSnap = firestore.collection("users")
                            .whereGreaterThan("bestScore", playerScore)
                            .count()
                            .get(AggregateSource.SERVER)
                            .await()
                        countSnap.count.toInt() + 1
                    }
                    LeaderboardEntry(
                        uid = uid,
                        displayName = playerDoc.getString("displayName") ?: "?",
                        numericId = readNumericId(playerDoc),
                        bestScore = playerScore,
                        rank = playerRank,
                        isCurrentPlayer = true
                    )
                }
            }

            val result = LoadResult.Success(
                topEntries = topEntries,
                playerEntry = playerEntry,
                playerInTop = playerInTop,
                fetchedAtMs = System.currentTimeMillis()
            )
            cachedResult = result
            Log.i(TAG, "loadLeaderboard: ${topEntries.size} top entries, " +
                "playerInTop=$playerInTop, playerRank=${playerEntry?.rank}")
            result
        } catch (e: Exception) {
            Log.w(TAG, "loadLeaderboard failed", e)
            LoadResult.Failure(e.message ?: "unknown")
        }
    }
}
