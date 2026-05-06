package com.moonstreamtech.lasttile

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v0.1.11: Drives the small update-available indicator in the header
 * bar. Uses Google Play's In-App Updates API (FLEXIBLE flow) so the
 * download runs in the background while the player keeps interacting
 * with the game.
 *
 * Single object scoped to the process. [init] is called once per
 * MainActivity creation; the AppUpdateManager itself is cached, but
 * the [ActivityResultLauncher] must be re-registered each time
 * because it is bound to the calling Activity's lifecycle and the
 * previous launcher dies with the previous Activity instance.
 *
 * Graceful failure: on devices without Play Services (some Huawei
 * stock builds) the [AppUpdateManager.getAppUpdateInfo] task fails.
 * We log at INFO and stay on [UpdateState.Idle] — no icon shown, no
 * toast, no crash. Same for any other unexpected failure path.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    sealed class UpdateState {
        object Idle : UpdateState()
        data class Available(val versionCode: Int) : UpdateState()
        object Downloaded : UpdateState()
    }

    private val _uiState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val uiState: StateFlow<UpdateState> = _uiState.asStateFlow()

    // The AppUpdateManager is app-context scoped so it survives
    // Activity recreations. The launcher is Activity-scoped and must
    // be re-registered on each init() call.
    private var manager: AppUpdateManager? = null
    private var pendingInfo: AppUpdateInfo? = null
    private var resultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    // Single InstallStateUpdatedListener; registered once per process
    // against the cached manager. Tracks the FLEXIBLE download from
    // any background thread Play Core happens to dispatch on (the
    // StateFlow .value setter is thread-safe).
    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            _uiState.value = UpdateState.Downloaded
        }
    }
    private var listenerRegistered = false

    /**
     * Wires up the manager + launcher and probes the Play update
     * channel. Safe to call on every MainActivity.onCreate; the
     * manager is created once per process and the launcher is
     * replaced on each call (the previous Activity's launcher
     * reference goes stale).
     */
    fun init(activity: ComponentActivity) {
        val mgr = manager
            ?: AppUpdateManagerFactory.create(activity.applicationContext).also {
                manager = it
            }
        if (!listenerRegistered) {
            mgr.registerListener(installListener)
            listenerRegistered = true
        }
        // Re-register on every onCreate. Previous Activity instance
        // (if any) is dead and so is its launcher; the registration
        // must happen before the new Activity reaches STARTED, which
        // onCreate satisfies.
        resultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            // RESULT_OK = user accepted, download started.
            // RESULT_CANCELED = user dismissed the consent sheet.
            // Other = Play Core internal failure. Either way, the
            // install listener will tell us when the download
            // actually completes; nothing to do synchronously here
            // beyond logging for support diagnostics.
            if (result.resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "Update flow ended without OK (code=${result.resultCode})")
            }
        }

        mgr.appUpdateInfo
            .addOnSuccessListener { info ->
                pendingInfo = info
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        // The user backgrounded the app while a previous
                        // download was finishing — surface the install
                        // CTA on the next foreground without making them
                        // re-tap the icon.
                        _uiState.value = UpdateState.Downloaded
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        _uiState.value = UpdateState.Available(info.availableVersionCode())
                    }
                    else -> {
                        // No update / not allowed — stay (or revert) to Idle.
                        _uiState.value = UpdateState.Idle
                    }
                }
            }
            .addOnFailureListener { e ->
                // Most common cause: device has no Play Services (some
                // Huawei builds) or the user is signed out of Play
                // Store. Either way the icon should not appear.
                Log.i(TAG, "appUpdateInfo unavailable; staying idle", e)
            }
    }

    /**
     * Launches the FLEXIBLE update flow. Called from the badge tap
     * handler when [uiState] is [UpdateState.Available].
     */
    fun startUpdate(activity: ComponentActivity) {
        val mgr = manager ?: return
        val info = pendingInfo ?: return
        val launcher = resultLauncher ?: return
        runCatching {
            mgr.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        }.onFailure { e ->
            Log.w(TAG, "startUpdateFlowForResult failed", e)
        }
    }

    /**
     * Triggers Play Core to install the downloaded APK and restart
     * the app. Called from the badge tap handler when [uiState] is
     * [UpdateState.Downloaded].
     */
    fun completeInstall() {
        runCatching { manager?.completeUpdate() }
            .onFailure { e -> Log.w(TAG, "completeUpdate failed", e) }
    }
}
