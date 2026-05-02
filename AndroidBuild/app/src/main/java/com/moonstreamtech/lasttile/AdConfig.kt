package com.moonstreamtech.lasttile

// AdMob id accessors. Values come from BuildConfig fields that the
// build script materialises differently per build type:
//   DEBUG:   real-id env, falling back to Google's public test ids.
//   RELEASE: real-id env only — Layer 1 (gradle taskGraph) refuses
//            to produce a release artifact when any of the three is
//            blank or contains the test publisher prefix.
//
// Crucially, the test publisher prefix string is NEVER present in
// release-variant BuildConfig output, so the post-build AAB scan
// (Layer 4) cannot mistake a properly-built release for a test
// build. Layer 2 (this object's verifyReleaseIntegrity) is a last
// safety net that crashes the process at Application.onCreate when
// any release id is somehow blank.
object AdConfig {
    val appId: String get() = BuildConfig.ADMOB_APP_ID
    val bannerUnitId: String get() = BuildConfig.ADMOB_BANNER_UNIT_ID
    val rewardedUnitId: String get() = BuildConfig.ADMOB_REWARDED_UNIT_ID

    fun verifyReleaseIntegrity() {
        if (BuildConfig.DEBUG) return
        require(appId.isNotBlank()) {
            "FATAL: AdMob app id is blank in release build."
        }
        require(bannerUnitId.isNotBlank()) {
            "FATAL: AdMob banner unit id is blank in release build."
        }
        require(rewardedUnitId.isNotBlank()) {
            "FATAL: AdMob rewarded unit id is blank in release build."
        }
    }
}
