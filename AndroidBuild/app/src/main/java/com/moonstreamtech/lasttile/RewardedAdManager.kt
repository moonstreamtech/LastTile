package com.moonstreamtech.lasttile

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

// Eager-loaded singleton wrapper around AdMob's RewardedAd. Init is
// fire-and-forget from Application.onCreate; load failures retry with
// exponential backoff (1s → 2s → 4s, three attempts) and the next
// successful show triggers a fresh preload so a returning user still
// sees an instant ad on the second tap.
//
// Concurrency: a single in-flight load is tracked via `loading`, so
// rapid taps cannot stack up parallel loads. The callback flow is:
//
//   ShowResult.EARNED   → user watched to the end; reward callback fired
//   ShowResult.SKIPPED  → user dismissed before reward (no shields)
//   ShowResult.FAILED   → ad couldn't be loaded or shown
//
// The reward count itself comes from the AdMob console, but the
// game treats every successful watch as a fixed-size grant — the
// payout is GameState.SHIELD_REWARD_GRANT, not the SDK-reported
// reward amount, so misconfigured AdMob units cannot inflate or
// deflate the in-game economy.
object RewardedAdManager {
    private const val TAG = "RewardedAdManager"
    private val MAIN = Handler(Looper.getMainLooper())
    private val backoffStepsMs = longArrayOf(1_000L, 2_000L, 4_000L)

    private var appContext: Context? = null
    private var rewarded: RewardedAd? = null
    private var loading: Boolean = false
    private var retryAttempt: Int = 0
    private var pendingShow: ShowRequest? = null

    enum class ShowResult { EARNED, SKIPPED, FAILED }

    private data class ShowRequest(
        val activity: Activity,
        val onResult: (ShowResult) -> Unit
    )

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun preload(context: Context) {
        init(context)
        loadInternal()
    }

    fun isReady(): Boolean = rewarded != null

    // Show the ad if loaded; otherwise queue the request and surface
    // FAILED if the next load attempt also fails. Caller passes a
    // single callback that fires exactly once. Safe to call rapidly:
    // a second call while an ad is already showing forwards FAILED to
    // its callback (concurrent guard).
    fun show(activity: Activity, onResult: (ShowResult) -> Unit) {
        val ad = rewarded
        if (ad != null) {
            internalShow(activity, ad, onResult)
            return
        }
        if (pendingShow != null) {
            // Already a queued request awaiting load; reject this one
            // rather than letting two callbacks race.
            onResult(ShowResult.FAILED)
            return
        }
        pendingShow = ShowRequest(activity, onResult)
        if (!loading) loadInternal()
        // Bound the wait so a hung load doesn't leave the UI dialog
        // stuck on "Loading…". 6s is enough for any healthy network
        // path; beyond that we surface FAILED and let the user retry.
        MAIN.postDelayed({
            val pending = pendingShow ?: return@postDelayed
            pendingShow = null
            pending.onResult(ShowResult.FAILED)
        }, 6_000L)
    }

    private fun loadInternal() {
        val ctx = appContext ?: return
        if (loading) return
        if (rewarded != null) return
        loading = true
        runCatching {
            RewardedAd.load(
                ctx,
                AdConfig.rewardedUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.i(TAG, "Rewarded ad loaded.")
                        loading = false
                        retryAttempt = 0
                        rewarded = ad
                        // Drain any pending show request.
                        val pending = pendingShow
                        pendingShow = null
                        pending?.let { internalShow(it.activity, ad, it.onResult) }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Rewarded ad failed to load: ${error.code} ${error.message}")
                        loading = false
                        rewarded = null
                        if (retryAttempt < backoffStepsMs.size) {
                            val delay = backoffStepsMs[retryAttempt]
                            retryAttempt++
                            MAIN.postDelayed({ loadInternal() }, delay)
                        } else {
                            // Out of retries; surface FAILED to any caller
                            // waiting on a show, then let manual show()
                            // calls trigger a fresh attempt.
                            val pending = pendingShow
                            pendingShow = null
                            pending?.onResult?.invoke(ShowResult.FAILED)
                            retryAttempt = 0
                        }
                    }
                }
            )
        }.onFailure { e ->
            Log.w(TAG, "RewardedAd.load threw", e)
            loading = false
        }
    }

    private fun internalShow(
        activity: Activity,
        ad: RewardedAd,
        onResult: (ShowResult) -> Unit
    ) {
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                onResult(if (earned) ShowResult.EARNED else ShowResult.SKIPPED)
                // Eager reload for the next tap.
                loadInternal()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.code} ${error.message}")
                rewarded = null
                onResult(ShowResult.FAILED)
                loadInternal()
            }

            override fun onAdShowedFullScreenContent() {
                // Once the ad is showing we cannot start another load
                // until it dismisses; rewarded is cleared in
                // onAdDismissedFullScreenContent.
                rewarded = null
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { earned = true })
    }
}
