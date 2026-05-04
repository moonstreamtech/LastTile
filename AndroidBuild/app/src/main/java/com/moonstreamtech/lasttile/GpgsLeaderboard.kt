package com.moonstreamtech.lasttile

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
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
 *
 * v0.1.5 diagnostic: every important branch also writes a one-line
 * event to gpgs-debug.log under the app-specific external files dir
 * (`/storage/emulated/0/Android/data/<pkg>/files/gpgs-debug.log`),
 * which is browsable via the device's Files app without root or USB
 * debugging. To be reverted once the failure mode is identified.
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
    private const val DEBUG_LOG_FILE = "gpgs-debug.log"

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
            debugLog(context, "submit: not configured")
            Log.i(TAG, "Skipping submitScore: leaderboard ID is placeholder.")
            onResult(SubmitResult.NotConfigured)
            return
        }
        val activity = context.toActivityOrNull()
        if (activity == null) {
            debugLog(context, "submit: no Activity")
            Log.w(TAG, "submitScore: no Activity, skipping")
            onResult(SubmitResult.Failed("no_activity"))
            return
        }
        val signInClient = authClientOrNull(context)
        if (signInClient == null) {
            debugLog(context, "submit: no Activity")
            Log.w(TAG, "submitScore: no GamesSignInClient, skipping")
            onResult(SubmitResult.Failed("no_sign_in_client"))
            return
        }
        runCatching {
            signInClient.isAuthenticated
                .addOnSuccessListener { authResult ->
                    if (authResult.isAuthenticated) {
                        debugLog(activity, "submit: already authenticated, calling submitScoreImmediate")
                        Log.i(TAG, "isAuthenticated: true")
                        submitScoreInternal(activity, score, onResult)
                    } else {
                        debugLog(activity, "submit: not authenticated, calling signIn")
                        attemptSignInThenSubmit(signInClient, activity, score, onResult)
                    }
                }
                .addOnFailureListener { e ->
                    debugLog(activity, "submit: isAuthenticated failed: ${e.message}")
                    Log.w(TAG, "isAuthenticated check failed", e)
                    onResult(SubmitResult.Failed(e.message ?: "auth_check_failed"))
                }
        }.onFailure { e ->
            debugLog(context, "submit: threw: ${e.javaClass.simpleName}: ${e.message}")
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
                    debugLog(activity, "submit: signIn success, calling submitScoreImmediate")
                    submitScoreInternal(activity, score, onResult)
                } else {
                    debugLog(activity, "submit: signIn returned but still not authenticated")
                    onResult(SubmitResult.Failed("sign_in_failed"))
                }
            }
            .addOnFailureListener { e ->
                debugLog(activity, "submit: signIn failed: ${e.message}")
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
                    debugLog(activity, "submit: SUCCESS score=$score")
                    Log.i(TAG, "submitScore success: $score")
                    onResult(SubmitResult.Success)
                }
                .addOnFailureListener { e ->
                    debugLog(activity, "submit: submitScoreImmediate failed: ${e.message}")
                    Log.w(TAG, "submitScoreImmediate failed", e)
                    onResult(SubmitResult.Failed(e.message ?: "unknown"))
                }
        }.onFailure { e ->
            debugLog(activity, "submit: threw: ${e.javaClass.simpleName}: ${e.message}")
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
     * The LeaderboardScores result type is left to Kotlin inference
     * because its package path differs between the legacy
     * play-services-games artifact and play-services-games-v2; using
     * inference keeps this file working across both. The underlying
     * buffer is always released in a finally block to avoid the
     * Play Games SDK leak warning.
     */
    suspend fun loadTopScores(
        context: Context,
        maxResults: Int = 10
    ): LoadResult {
        debugLog(context, "load: enter, maxResults=$maxResults")
        if (!isConfigured()) {
            debugLog(context, "load: not configured")
            return LoadResult.Failure("not_configured")
        }
        val activity = context.toActivityOrNull()
        if (activity == null) {
            debugLog(context, "load: no Activity")
            Log.w(TAG, "loadTopScores: no Activity, skipping")
            return LoadResult.Failure("no_activity")
        }
        val signInClient = PlayGames.getGamesSignInClient(activity)
        return try {
            if (!ensureAuthenticated(context, signInClient)) {
                Log.w(TAG, "loadTopScores: sign-in failed")
                return LoadResult.Failure("sign_in_failed")
            }
            debugLog(context, "load: authenticated, fetching")
            val annotated = PlayGames.getLeaderboardsClient(activity)
                .loadTopScores(
                    LEADERBOARD_ID,
                    LeaderboardVariant.TIME_SPAN_ALL_TIME,
                    LeaderboardVariant.COLLECTION_PUBLIC,
                    maxResults,
                    false
                )
                .await()
            val scores = annotated.get()
                ?: return LoadResult.Failure("no_scores_response").also {
                    debugLog(context, "load: SDK call failed: annotated.get() returned null")
                }
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
                debugLog(context, "load: SDK returned ${entries.size} entries")
                Log.i(TAG, "loadTopScores success: ${entries.size} entries")
                LoadResult.Success(entries.toList())
            } finally {
                scores.release()
            }
        } catch (e: Exception) {
            val trace = Log.getStackTraceString(e)
            debugLog(
                context,
                "load: threw: ${e.javaClass.simpleName}: ${e.message}; stacktrace: $trace"
            )
            Log.w(TAG, "loadTopScores threw", e)
            LoadResult.Failure("exception: ${e.message ?: "unknown"}")
        }
    }

    private suspend fun ensureAuthenticated(
        context: Context,
        signInClient: GamesSignInClient
    ): Boolean {
        val authResult = try {
            signInClient.isAuthenticated.await()
        } catch (e: Exception) {
            debugLog(context, "load: isAuthenticated check failed: ${e.message}")
            Log.w(TAG, "isAuthenticated check failed", e)
            return false
        }
        if (authResult.isAuthenticated) {
            debugLog(context, "load: authenticated, fetching")
            Log.i(TAG, "isAuthenticated: true")
            return true
        }
        debugLog(context, "load: not authenticated, attempting sign-in")
        val signInResult = try {
            signInClient.signIn().await()
        } catch (e: Exception) {
            debugLog(context, "load: sign-in failed: ${e.message}")
            Log.w(TAG, "signIn failed", e)
            return false
        }
        Log.i(TAG, "signIn success")
        if (!signInResult.isAuthenticated) {
            debugLog(context, "load: signIn returned but still not authenticated")
        }
        return signInResult.isAuthenticated
    }

    private fun authClientOrNull(context: Context): GamesSignInClient? {
        val activity = context.toActivityOrNull() ?: return null
        return PlayGames.getGamesSignInClient(activity)
    }

    // Compose's LocalContext.current is not always an Activity directly:
    // it's typically a ContextWrapper (or a chain of wrappers from theme,
    // tooling, or library code) wrapping the hosting Activity. A plain
    // `as? Activity` cast returns null in that case, which is what
    // produced the v0.1.5 "load: no Activity" failure observed in the
    // diagnostic log even though the same Compose tree's submitScore
    // path (called from GameState with the Activity-context already)
    // worked fine. Walk the wrapper chain to find the Activity.
    internal fun Context.toActivityOrNull(): Activity? {
        var ctx: Context? = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Append a one-line, timestamped diagnostic event to
     * [DEBUG_LOG_FILE] under the app-specific external files dir
     * (`/storage/emulated/0/Android/data/<pkg>/files/gpgs-debug.log`)
     * so the user can review GPGS outcomes via the device's Files app
     * without root or USB debugging. Wrapped in runCatching — any IO
     * failure is silently dropped because diagnostics must never
     * affect the call path they observe. To be removed alongside the
     * rest of the diagnostic patch once the failure mode is found.
     *
     * Visibility is `internal` so LastTileApplication can also write
     * one-line traces from PlayGamesSdk.initialize without duplicating
     * the file-append boilerplate.
     */
    internal fun debugLog(context: Context, message: String) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return@runCatching
            val file = java.io.File(dir, DEBUG_LOG_FILE)
            val timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US
            ).format(java.util.Date())
            file.appendText("[$timestamp] $message\n")
        }
    }
}
