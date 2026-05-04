package com.moonstreamtech.lasttile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames

/**
 * Online leaderboard wrapper around Google Play Games Services v2.
 *
 * Every public call gates on an isAuthenticated check first; if the
 * session isn't authenticated, signIn() is attempted once and the
 * call retries. Failures are logged with TAG="GpgsLeaderboard" and
 * surfaced through the typed Result so the UI can display the
 * existing leaderboard_sign_in_hint toast without crashing.
 *
 * Sign-in is never auto-prompted at launch — it only fires when a
 * user-driven action (game-over, restart, opening the leaderboard
 * panel) reaches one of these methods.
 */
object GpgsLeaderboard {
    /**
     * Leaderboard ID issued by Play Console > Play Games Services > Leaderboards.
     * While this is the placeholder, [submitScore] short-circuits and
     * [getLeaderboardIntent] returns null so the UI shows a "not configured"
     * notice instead of opening a broken Google sheet.
     */
    const val LEADERBOARD_ID: String = "CgkIrZ-Ng5IHEAIQAQ"

    private const val TAG = "GpgsLeaderboard"

    sealed class SubmitResult {
        data object Success : SubmitResult()
        data object NotConfigured : SubmitResult()
        data class Failed(val reason: String) : SubmitResult()
    }

    sealed class IntentResult {
        data class Ready(val intent: Intent) : IntentResult()
        data object NotConfigured : IntentResult()
        data class Failed(val reason: String) : IntentResult()
    }

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
     * Resolves the GPGS leaderboard activity intent, signing the user
     * in first if needed. Caller still has to handle a Failed return —
     * the user may dismiss the sign-in prompt.
     */
    fun getLeaderboardIntent(
        activity: Activity,
        onResult: (IntentResult) -> Unit
    ) {
        if (!isConfigured()) {
            onResult(IntentResult.NotConfigured)
            return
        }
        val signInClient = PlayGames.getGamesSignInClient(activity)
        runCatching {
            signInClient.isAuthenticated
                .addOnSuccessListener { authResult ->
                    if (authResult.isAuthenticated) {
                        Log.i(TAG, "isAuthenticated: true")
                        getLeaderboardIntentInternal(activity, onResult)
                    } else {
                        attemptSignInThenIntent(signInClient, activity, onResult)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "isAuthenticated check failed", e)
                    onResult(IntentResult.Failed(e.message ?: "auth_check_failed"))
                }
        }.onFailure { e ->
            Log.w(TAG, "getLeaderboardIntent threw", e)
            onResult(IntentResult.Failed(e.message ?: "unknown"))
        }
    }

    private fun attemptSignInThenIntent(
        signInClient: GamesSignInClient,
        activity: Activity,
        onResult: (IntentResult) -> Unit
    ) {
        signInClient.signIn()
            .addOnSuccessListener { signInResult ->
                Log.i(TAG, "signIn success")
                if (signInResult.isAuthenticated) {
                    getLeaderboardIntentInternal(activity, onResult)
                } else {
                    onResult(IntentResult.Failed("sign_in_failed"))
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "signIn failed", e)
                onResult(IntentResult.Failed("sign_in_failed"))
            }
    }

    private fun getLeaderboardIntentInternal(
        activity: Activity,
        onResult: (IntentResult) -> Unit
    ) {
        runCatching {
            PlayGames.getLeaderboardsClient(activity)
                .getLeaderboardIntent(LEADERBOARD_ID)
                .addOnSuccessListener { intent ->
                    Log.i(TAG, "getLeaderboardIntent success")
                    onResult(IntentResult.Ready(intent))
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getLeaderboardIntent failed", e)
                    onResult(IntentResult.Failed(e.message ?: "unknown"))
                }
        }.onFailure { e ->
            Log.w(TAG, "getLeaderboardIntent threw", e)
            onResult(IntentResult.Failed(e.message ?: "unknown"))
        }
    }

    private fun authClientOrNull(context: Context): GamesSignInClient? {
        val activity = context.toActivityOrNull() ?: return null
        return PlayGames.getGamesSignInClient(activity)
    }

    private fun Context.toActivityOrNull(): Activity? = this as? Activity
}
