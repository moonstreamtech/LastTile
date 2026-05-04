package com.moonstreamtech.lasttile

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardScores
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import kotlinx.coroutines.tasks.await

/**
 * Online leaderboard wrapper around Google Play Games Services v2.
 *
 * Every public call gates on an isAuthenticated check first; if the
 * session isn't authenticated, signIn() is attempted once and the
 * call retries. Failures are logged with TAG="GpgsLeaderboard" and
 * surfaced through the typed Result so the UI can render an inline
 * error state without crashing.
 *
 * Sign-in is never auto-prompted at launch — it only fires when a
 * user-driven action (game-over, restart, opening the leaderboard
 * panel) reaches one of these methods.
 */
object GpgsLeaderboard {
    /**
     * Leaderboard ID issued by Play Console > Play Games Services > Leaderboards.
     * While this is the placeholder, [submitScore] short-circuits and
     * [loadTopScores] returns a Failure so the UI shows a "not configured"
     * notice instead of opening a broken Google sheet.
     */
    const val LEADERBOARD_ID: String = "CgkIrZ-Ng5IHEAIQAQ"

    private const val TAG = "GpgsLeaderboard"
    private const val DEFAULT_DISPLAY_NAME = "Player"

    sealed class SubmitResult {
        data object Success : SubmitResult()
        data object NotConfigured : SubmitResult()
        data class Failed(val reason: String) : SubmitResult()
    }

    sealed class LoadResult {
        data class Success(val entries: List<GlobalEntry>) : LoadResult()
        data class Failure(val reason: String) : LoadResult()
    }

    data class GlobalEntry(
        val rank: Long,
        val displayName: String,
        val score: Long,
        val isCurrentPlayer: Boolean
    )

    fun isConfigured(): Boolean = LEADERBOARD_ID != "PLACEHOLDER_LEADERBOARD_ID"

    /**
     * Submits the score to the GPGS leaderboard, signing the user in
     * first if needed. Fire-and-forget from the caller's perspective —
     * the local leaderboard is already the source of truth for the
     * device, so a failure here never affects game state.
     */
    fun submitScore(
        context: Context,
        score: Long,
        onResult: (SubmitResult) -> Unit = {}
    ) {
        if (!isConfigured()) {
            Log.i(TAG, "Skipping submitScore: leaderboard ID is placeholder.")
            onResult(SubmitResult.NotConfigured)
            return
        }
        val activity = context.toActivityOrNull()
        if (activity == null) {
            Log.w(TAG, "submitScore: no Activity, skipping")
            onResult(SubmitResult.Failed("no_activity"))
            return
        }
        val signInClient = authClientOrNull(context)
        if (signInClient == null) {
            Log.w(TAG, "submitScore: no GamesSignInClient, skipping")
            onResult(SubmitResult.Failed("no_sign_in_client"))
            return
        }
        runCatching {
            signInClient.isAuthenticated
                .addOnSuccessListener { authResult ->
                    if (authResult.isAuthenticated) {
                        Log.i(TAG, "isAuthenticated: true")
                        submitScoreInternal(activity, score, onResult)
                    } else {
                        attemptSignInThenSubmit(signInClient, activity, score, onResult)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "isAuthenticated check failed", e)
                    onResult(SubmitResult.Failed(e.message ?: "auth_check_failed"))
                }
        }.onFailure { e ->
            Log.w(TAG, "submitScore threw", e)
            onResult(SubmitResult.Failed(e.message ?: "unknown"))
        }
    }

    private fun attemptSignInThenSubmit(
        signInClient: GamesSignInClient,
        activity: Activity,
        score: Long,
        onResult: (SubmitResult) -> Unit
    ) {
        signInClient.signIn()
            .addOnSuccessListener { signInResult ->
                Log.i(TAG, "signIn success")
                if (signInResult.isAuthenticated) {
                    submitScoreInternal(activity, score, onResult)
                } else {
                    onResult(SubmitResult.Failed("sign_in_failed"))
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "signIn failed", e)
                onResult(SubmitResult.Failed("sign_in_failed"))
            }
    }

    private fun submitScoreInternal(
        activity: Activity,
        score: Long,
        onResult: (SubmitResult) -> Unit
    ) {
        runCatching {
            PlayGames.getLeaderboardsClient(activity)
                .submitScoreImmediate(LEADERBOARD_ID, score)
                .addOnSuccessListener {
                    Log.i(TAG, "submitScore success: $score")
                    onResult(SubmitResult.Success)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "submitScoreImmediate failed", e)
                    onResult(SubmitResult.Failed(e.message ?: "unknown"))
                }
        }.onFailure { e ->
            Log.w(TAG, "submitScoreImmediate threw", e)
            onResult(SubmitResult.Failed(e.message ?: "unknown"))
        }
    }

    /**
     * Suspend-friendly fetch of the top [maxResults] all-time public
     * scores. Reuses the same isAuthenticated → signIn handshake as
     * [submitScore], but bridges the SDK's Task<AnnotatedData<...>>
     * into a coroutine via .await().
     *
     * Always releases the underlying [LeaderboardScores] buffer in a
     * finally block to avoid the Play Games SDK leak warning.
     */
    suspend fun loadTopScores(
        context: Context,
        maxResults: Int = 10
    ): LoadResult {
        if (!isConfigured()) return LoadResult.Failure("not_configured")
        val activity = context.toActivityOrNull()
        if (activity == null) {
            Log.w(TAG, "loadTopScores: no Activity, skipping")
            return LoadResult.Failure("no_activity")
        }
        val signInClient = PlayGames.getGamesSignInClient(activity)
        return try {
            if (!ensureAuthenticated(signInClient)) {
                Log.w(TAG, "loadTopScores: sign-in failed")
                return LoadResult.Failure("sign_in_failed")
            }
            val annotated = PlayGames.getLeaderboardsClient(activity)
                .loadTopScores(
                    LEADERBOARD_ID,
                    LeaderboardVariant.TIME_SPAN_ALL_TIME,
                    LeaderboardVariant.COLLECTION_PUBLIC,
                    maxResults,
                    false
                )
                .await()
            val scores: LeaderboardScores = annotated.get()
                ?: return LoadResult.Failure("no_scores_response")
            try {
                val buffer = scores.scores
                val entries = ArrayList<GlobalEntry>(buffer.count)
                for (i in 0 until buffer.count) {
                    val s = buffer.get(i)
                    entries.add(
                        GlobalEntry(
                            rank = s.rank,
                            displayName = s.scoreHolderDisplayName ?: DEFAULT_DISPLAY_NAME,
                            score = s.rawScore,
                            // TODO: highlight the current player's row by
                            // comparing s.scoreHolder?.playerId against the
                            // current player's ID (PlayGames.getPlayersClient
                            // .getCurrentPlayer). Skipped for now to keep
                            // this PR scoped to the fetch + render flow.
                            isCurrentPlayer = false
                        )
                    )
                }
                Log.i(TAG, "loadTopScores success: ${entries.size} entries")
                LoadResult.Success(entries.toList())
            } finally {
                scores.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadTopScores threw", e)
            LoadResult.Failure("exception: ${e.message ?: "unknown"}")
        }
    }

    private suspend fun ensureAuthenticated(signInClient: GamesSignInClient): Boolean {
        val authResult = try {
            signInClient.isAuthenticated.await()
        } catch (e: Exception) {
            Log.w(TAG, "isAuthenticated check failed", e)
            return false
        }
        if (authResult.isAuthenticated) {
            Log.i(TAG, "isAuthenticated: true")
            return true
        }
        val signInResult = try {
            signInClient.signIn().await()
        } catch (e: Exception) {
            Log.w(TAG, "signIn failed", e)
            return false
        }
        Log.i(TAG, "signIn success")
        return signInResult.isAuthenticated
    }

    private fun authClientOrNull(context: Context): GamesSignInClient? {
        val activity = context.toActivityOrNull() ?: return null
        return PlayGames.getGamesSignInClient(activity)
    }

    private fun Context.toActivityOrNull(): Activity? = this as? Activity
}
