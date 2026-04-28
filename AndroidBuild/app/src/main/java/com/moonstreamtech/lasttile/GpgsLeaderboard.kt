package com.moonstreamtech.lasttile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.games.PlayGames

/**
 * Online leaderboard wrapper around Google Play Games Services v2.
 *
 * Designed to be safe at runtime even before Play Console registration:
 * every public call is wrapped in [runCatching] and returns a typed result
 * the UI can branch on without crashing. The local leaderboard remains the
 * primary store; this class is purely additive.
 *
 * Replace [LEADERBOARD_ID] with the value generated in the Play Console
 * once the Play Games Services entry exists.
 */
object GpgsLeaderboard {
    /**
     * Leaderboard ID issued by Play Console > Play Games Services > Leaderboards.
     * While this is the placeholder, [submitScore] short-circuits and
     * [getLeaderboardIntent] returns null so the UI shows a "not configured"
     * notice instead of opening a broken Google sheet.
     */
    const val LEADERBOARD_ID: String = "PLACEHOLDER_LEADERBOARD_ID"

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
     * Fire-and-forget score submission. We never block the UI on this; the
     * local leaderboard is already the source of truth for the device.
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
        runCatching {
            PlayGames.getLeaderboardsClient(context.toActivityOrNull() ?: return@runCatching null)
                ?.submitScoreImmediate(LEADERBOARD_ID, score)
                ?.addOnSuccessListener { onResult(SubmitResult.Success) }
                ?.addOnFailureListener { e ->
                    Log.w(TAG, "submitScoreImmediate failed", e)
                    onResult(SubmitResult.Failed(e.message ?: "unknown"))
                }
        }.onFailure { e ->
            Log.w(TAG, "submitScore threw", e)
            onResult(SubmitResult.Failed(e.message ?: "unknown"))
        }
    }

    /**
     * Resolves the GPGS leaderboard activity intent so the UI can `startActivity`
     * it. Caller still has to handle a null/Failed return — sign-in may also
     * silently reject in offline mode.
     */
    fun getLeaderboardIntent(
        activity: Activity,
        onResult: (IntentResult) -> Unit
    ) {
        if (!isConfigured()) {
            onResult(IntentResult.NotConfigured)
            return
        }
        runCatching {
            PlayGames.getLeaderboardsClient(activity)
                .getLeaderboardIntent(LEADERBOARD_ID)
                .addOnSuccessListener { intent -> onResult(IntentResult.Ready(intent)) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getLeaderboardIntent failed", e)
                    onResult(IntentResult.Failed(e.message ?: "unknown"))
                }
        }.onFailure { e ->
            Log.w(TAG, "getLeaderboardIntent threw", e)
            onResult(IntentResult.Failed(e.message ?: "unknown"))
        }
    }

    private fun Context.toActivityOrNull(): Activity? = this as? Activity
}
