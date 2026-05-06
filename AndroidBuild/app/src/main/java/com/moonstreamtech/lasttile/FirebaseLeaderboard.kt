package com.moonstreamtech.lasttile

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject

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
 * Cache is two-layered to survive process death:
 *   - In-memory `cachedResult` is fastest; populated on every successful
 *     fetch and read by every loadLeaderboard call within the same
 *     process.
 *   - SharedPreferences-backed disk cache mirrors the same JSON-encoded
 *     snapshot. On cold start the in-memory cache is empty, so the
 *     first loadLeaderboard reads from disk; if the disk cache is fresh
 *     (< [CACHE_TTL_MS]) it is restored and the Firestore fetch is
 *     skipped entirely. This is the cost-saving path for users who
 *     reopen the app within an hour.
 */
object FirebaseLeaderboard {
    private const val TAG = "FirebaseLeaderboard"
    private const val TOP_LIMIT = 100
    private const val CACHE_TTL_MS = 60 * 60 * 1000L  // 60 minutes
    private const val PREFS_NAME = "lasttile_state"
    // v1 schema. Bump to _v2 if the JSON shape ever changes — old keys
    // will simply return null and trigger a fresh fetch.
    private const val DISK_CACHE_KEY = "leaderboard_cache_v1"
    private const val DISK_CACHE_AT_KEY = "leaderboard_cache_at_v1"
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
     * Invalidates both in-memory and disk caches on a successful write so
     * the next leaderboard open reflects the new score immediately.
     *
     * No-ops when auth is not ready (uid is null). Failures are logged
     * silently — [UserBootstrap.retryPendingScore] handles any score
     * that was queued during an earlier offline session.
     */
    fun submitBestScore(context: Context, score: Long) {
        if (score <= 0L) return
        val uid = UserBootstrap.uid ?: run {
            Log.i(TAG, "submit: no auth uid, skipping")
            return
        }
        val appContext = context.applicationContext
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
                        // Invalidate both caches so the next leaderboard
                        // open shows fresh data including this submission.
                        cachedResult = null
                        clearDiskCache(appContext)
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
     * Drop both caches. Called by username-change success so the new
     * displayName surfaces on the next panel open even if the process
     * is killed before the in-flight forceRefresh completes.
     */
    fun invalidateCache(context: Context) {
        cachedResult = null
        clearDiskCache(context.applicationContext)
    }

    /**
     * Returns cached result if fresh (< [CACHE_TTL_MS]); otherwise fetches
     * top 100 + player rank from Firestore. Pass [forceRefresh] = true to
     * bypass the cache (e.g. when the user taps the Refresh button).
     *
     * Cache lookup order: in-memory → disk → network. On a fresh fetch the
     * result is written to both layers.
     */
    suspend fun loadLeaderboard(
        context: Context,
        forceRefresh: Boolean = false
    ): LoadResult {
        val appContext = context.applicationContext
        if (!forceRefresh) {
            val cached = cachedResult
            if (cached != null && isFresh(cached.fetchedAtMs)) {
                Log.i(TAG, "loadLeaderboard: returning in-memory cache (${cached.topEntries.size} entries)")
                return cached
            }
            // In-memory miss — try disk. On cold start this is the only
            // place a recent fetch can come from without spending a read.
            val disk = readDiskCache(appContext)
            if (disk != null && isFresh(disk.fetchedAtMs)) {
                cachedResult = disk
                Log.i(TAG, "loadLeaderboard: returning disk cache (${disk.topEntries.size} entries)")
                return disk
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
            writeDiskCache(appContext, uid, result)
            Log.i(TAG, "loadLeaderboard: ${topEntries.size} top entries, " +
                "playerInTop=$playerInTop, playerRank=${playerEntry?.rank}")
            result
        } catch (e: Exception) {
            Log.w(TAG, "loadLeaderboard failed", e)
            LoadResult.Failure(e.message ?: "unknown")
        }
    }

    private fun isFresh(fetchedAtMs: Long): Boolean =
        System.currentTimeMillis() - fetchedAtMs < CACHE_TTL_MS

    // ── Disk cache helpers ────────────────────────────────────────────────
    //
    // Manual JSON via org.json (already on the classpath via GameState's
    // save format) avoids a serialization-library dependency for what is
    // a small, schema-stable payload.

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readDiskCache(context: Context): LoadResult.Success? {
        return try {
            val p = prefs(context)
            val json = p.getString(DISK_CACHE_KEY, null) ?: return null
            val fetchedAtMs = p.getLong(DISK_CACHE_AT_KEY, 0L)
            if (fetchedAtMs <= 0L) return null
            val obj = JSONObject(json)
            // Discard cache snapshots that belong to a different uid.
            // This can only happen if the user wiped app data and the
            // disk cache survived (it does not — but defending here keeps
            // future refactors honest).
            val cachedUid = obj.optString("uid", "")
            val currentUid = UserBootstrap.uid
            if (currentUid != null && cachedUid != currentUid) {
                Log.i(TAG, "disk cache uid mismatch ($cachedUid vs $currentUid), discarding")
                return null
            }
            val topArr = obj.getJSONArray("topEntries")
            val topEntries = (0 until topArr.length()).map { i ->
                entryFromJson(topArr.getJSONObject(i), currentUid)
            }
            val playerEntry = if (obj.isNull("playerEntry")) {
                null
            } else {
                entryFromJson(obj.getJSONObject("playerEntry"), currentUid)
            }
            LoadResult.Success(
                topEntries = topEntries,
                playerEntry = playerEntry,
                playerInTop = obj.optBoolean("playerInTop", false),
                fetchedAtMs = fetchedAtMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "readDiskCache failed; ignoring stale entry", e)
            null
        }
    }

    private fun writeDiskCache(context: Context, uid: String, result: LoadResult.Success) {
        try {
            val obj = JSONObject().apply {
                put("uid", uid)
                put("topEntries", JSONArray().apply {
                    result.topEntries.forEach { put(entryToJson(it)) }
                })
                if (result.playerEntry != null) {
                    put("playerEntry", entryToJson(result.playerEntry))
                } else {
                    put("playerEntry", JSONObject.NULL)
                }
                put("playerInTop", result.playerInTop)
            }
            prefs(context).edit()
                .putString(DISK_CACHE_KEY, obj.toString())
                .putLong(DISK_CACHE_AT_KEY, result.fetchedAtMs)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "writeDiskCache failed; cache will refetch next time", e)
        }
    }

    private fun clearDiskCache(context: Context) {
        prefs(context).edit()
            .remove(DISK_CACHE_KEY)
            .remove(DISK_CACHE_AT_KEY)
            .apply()
    }

    private fun entryToJson(e: LeaderboardEntry): JSONObject = JSONObject().apply {
        put("uid", e.uid)
        put("displayName", e.displayName)
        put("numericId", e.numericId)
        put("bestScore", e.bestScore)
        if (e.rank != null) put("rank", e.rank) else put("rank", JSONObject.NULL)
        // isCurrentPlayer is recomputed at read time from the live uid.
    }

    private fun entryFromJson(o: JSONObject, currentUid: String?): LeaderboardEntry {
        val uid = o.getString("uid")
        return LeaderboardEntry(
            uid = uid,
            displayName = o.optString("displayName", "?"),
            numericId = o.optString("numericId", "#000000"),
            bestScore = o.optLong("bestScore", 0L),
            rank = if (o.isNull("rank")) null else o.optInt("rank"),
            isCurrentPlayer = (currentUid != null && uid == currentUid)
        )
    }
}
