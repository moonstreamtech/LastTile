package com.moonstreamtech.lasttile.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.moonstreamtech.lasttile.AdConfig
import com.moonstreamtech.lasttile.GameState
import com.moonstreamtech.lasttile.LeaderboardEntry
import com.moonstreamtech.lasttile.LocalLeaderboard
import com.moonstreamtech.lasttile.R
import com.moonstreamtech.lasttile.RewardedAdManager
import com.moonstreamtech.lasttile.Tile
import com.moonstreamtech.lasttile.TutorialController
import com.moonstreamtech.lasttile.TutorialState
import com.moonstreamtech.lasttile.TutorialStep
import com.moonstreamtech.lasttile.UserBootstrap
import com.moonstreamtech.lasttile.UsernameRepository
import com.moonstreamtech.lasttile.FirebaseLeaderboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val BgTop = Color(0xFF141B24)
private val BgBottom = Color(0xFF05070A)
private val CardBg = Color(0xFF1A232E)
private val CardAccent = Color(0xFF2A3848)
private val TextPrimary = Color(0xFFECF1F8)
private val TextSecondary = Color(0xFF8FA0B6)
private val AccentAmber = Color(0xFFFFB74D)
private val AccentGold = Color(0xFFFFD54F)
private val AccentPurple = Color(0xFFBA68C8)
private val AccentBlue = Color(0xFF64B5F6)
private val AccentGreen = Color(0xFF81C784)

// Warm yellow used for the tutorial spotlight pulse on highlighted
// board cells and the KALKAN card during step 4. Picked to match the
// existing Restart button accent in the screenshots.
private val TutorialSpotlight = Color(0xFFF4C062)

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember {
        context.getSharedPreferences("lasttile_state", Context.MODE_PRIVATE)
    }
    val leaderboardPrefs = remember {
        context.getSharedPreferences("lasttile_leaderboard", Context.MODE_PRIVATE)
    }
    val leaderboard = remember { LocalLeaderboard(leaderboardPrefs) }
    val tutorial = remember(prefs) { TutorialController(prefs) }
    val state = remember(activity) {
        GameState(
            prefs = prefs,
            leaderboard = leaderboard,
            onRunSubmitted = { finalScore ->
                FirebaseLeaderboard.submitBestScore(finalScore.toLong())
            },
            isTutorialActive = { tutorial.state.active },
            currentTutorialStep = {
                if (tutorial.state.active) tutorial.state.currentStep else null
            }
        )
    }
    // Auto-start the tutorial on the first launch after install. Small
    // delay so the GameScreen has rendered before the overlay covers it
    // — without the delay the dim layer paints into a half-laid-out
    // tree and the cut-out region misses on the first frame.
    //
    // First-launch tutorials run in mandatory mode so step 6 forces the
    // user to pick a name before the overlay can be dismissed.
    LaunchedEffect(Unit) {
        if (!tutorial.hasSeenTutorial) {
            kotlinx.coroutines.delay(500L)
            tutorial.start(mandatory = true)
        }
    }
    // Whenever the tutorial step changes, script the board for the new
    // step. Done is the terminal state — when we reach it the user is
    // back to a normal run, so kick off a fresh restart() so the saved
    // tutorial layout doesn't bleed into the post-tutorial game. The
    // tutorialHasBeenActive flag scopes the restart to "tutorial just
    // ended in this session", preventing a cold launch with active=false
    // from wiping a legitimate saved game.
    var tutorialHasBeenActive by remember { mutableStateOf(false) }
    LaunchedEffect(tutorial.state.currentStep, tutorial.state.active) {
        if (tutorial.state.active) {
            state.setupForTutorial(tutorial.state.currentStep)
            tutorialHasBeenActive = true
        } else if (tutorialHasBeenActive) {
            // Restore before restart so the player's pre-tutorial
            // shield count is what survives onto the post-tutorial run
            // (restart doesn't touch shieldCount, but ordering keeps
            // the intent obvious).
            state.restoreSavedShieldForTutorial()
            state.restart()
            tutorialHasBeenActive = false
        }
    }
    // Auto-advance hooks. Two stages so the user gets a "celebrate the
    // success" beat before the step disappears:
    //
    //   1) Trigger detection — when the scripted action fires (combo
    //      went up for Merge, a frame cell unlocked for Frame, the
    //      shield count dropped for Shield) we mark the step completed
    //      but do NOT call next() yet. The overlay reads stepCompleted
    //      and swaps to a checkmark + localized "Nice merge / Frame
    //      opened / Hazard cleared" message.
    //   2) Delayed advance — once stepCompleted flips to true we wait
    //      ~2s and then advance. Hazard and Leaderboard are not in this
    //      pipeline; they advance on the explicit Got it tap.
    LaunchedEffect(state.combo, state.unlockedTargets, state.shieldCount, tutorial.state.currentStep) {
        if (!tutorial.state.active) return@LaunchedEffect
        if (tutorial.state.stepCompleted) return@LaunchedEffect
        val triggered = when (tutorial.state.currentStep) {
            TutorialStep.Merge -> state.combo > 0
            TutorialStep.Frame -> state.unlockedTargets.isNotEmpty()
            TutorialStep.Shield -> state.shieldCount == 0
            else -> false
        }
        if (triggered) tutorial.markStepCompleted()
    }
    LaunchedEffect(tutorial.state.stepCompleted, tutorial.state.currentStep) {
        if (!tutorial.state.active) return@LaunchedEffect
        if (!tutorial.state.stepCompleted) return@LaunchedEffect
        kotlinx.coroutines.delay(2000L)
        tutorial.next()
    }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showShieldDialog by remember { mutableStateOf(false) }
    var adInFlight by remember { mutableStateOf(false) }
    // Username dialog state. usernameDialogMandatory mirrors the
    // tutorial-mandatory mode so the dialog hides Cancel and blocks
    // outside-tap dismissal during the first-launch flow.
    var showUsernameDialog by remember { mutableStateOf(false) }
    var usernameDialogMandatory by remember { mutableStateOf(false) }
    var usernameDialogCurrent by remember { mutableStateOf("") }
    var usernameSaving by remember { mutableStateOf(false) }
    var usernameInlineError by remember { mutableStateOf<String?>(null) }
    // Bumped by the username-save success handler to force the global
    // leaderboard tab to refetch, so the new name appears immediately
    // after the player commits it.
    var leaderboardRefreshTick by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    // Drag-and-drop coordinates for the shield. Captured in window
    // (root-level) coordinates so the floating overlay and the cell
    // hit-test on drop both see the same frame as the BoardView's
    // onGloballyPositioned callback.
    var shieldDragWindowPos by remember { mutableStateOf<Offset?>(null) }
    var boardLayout by remember { mutableStateOf<BoardLayout?>(null) }

    val adUnavailableMsg = stringResource(R.string.shield_ad_unavailable)
    val adLoadingMsg = stringResource(R.string.shield_ad_loading)
    val adSkippedMsg = stringResource(R.string.shield_ad_skipped)
    val adRewardedMsg = stringResource(R.string.shield_ad_rewarded)
    val usernameSavedMsg = stringResource(R.string.username_change_saved)
    val usernameFailedMsg = stringResource(R.string.username_change_failed)
    val usernameTakenMsg = stringResource(R.string.username_taken)
    val usernameCooldownTemplate = stringResource(R.string.username_cooldown)

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun openShieldDialogOrTriggerAd() {
        showShieldDialog = true
    }

    fun playRewardedAd() {
        val act = activity ?: run {
            showToast(adUnavailableMsg)
            return
        }
        if (adInFlight) return
        adInFlight = true
        showToast(adLoadingMsg)
        RewardedAdManager.show(act) { result ->
            adInFlight = false
            when (result) {
                RewardedAdManager.ShowResult.EARNED -> {
                    state.grantShields(GameState.SHIELD_REWARD_GRANT)
                    showToast(adRewardedMsg)
                }
                RewardedAdManager.ShowResult.SKIPPED -> showToast(adSkippedMsg)
                RewardedAdManager.ShowResult.FAILED -> showToast(adUnavailableMsg)
            }
        }
    }

    fun handleShieldCardTap() {
        // The shield card's tap always opens the Earn Shield dialog
        // regardless of shieldCount. Applying a shield to a hazard is
        // exclusively a drag-and-drop gesture (handleShieldDragEnd).
        openShieldDialogOrTriggerAd()
    }

    // Shared pulse alpha for the tutorial spotlight. One transition
    // drives every glowing cell + the KALKAN card so they breathe in
    // sync, and the transition runs unconditionally — cheap, and means
    // the float is always defined for callers that gate on
    // tutorial.state.active themselves. 1200 ms with LinearEasing +
    // RepeatMode.Reverse matches the spec and reads as a slow breath.
    val tutorialPulseTransition = rememberInfiniteTransition(label = "tutorialPulse")
    val tutorialPulseAlpha by tutorialPulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tutorialPulseAlpha"
    )
    val tutorialSpotlightActive = tutorial.state.active &&
        !tutorial.state.stepCompleted &&
        tutorial.state.highlightedCells.isNotEmpty()
    val tutorialShieldCardHighlighted = tutorial.state.active &&
        !tutorial.state.stepCompleted &&
        tutorial.state.currentStep == TutorialStep.Shield

    fun handleShieldDragEnd() {
        val pos = shieldDragWindowPos
        shieldDragWindowPos = null
        if (pos == null) return
        val layout = boardLayout ?: return
        val cell = layout.cellAt(pos) ?: return
        if (state.shieldCount <= 0) {
            openShieldDialogOrTriggerAd()
            return
        }
        // applyShieldOn returns false silently for non-hazard targets;
        // that matches the spec ("nothing happens, count doesn't drop").
        state.applyShieldOn(cell.first, cell.second)
    }

    // Trace safeDrawing insets across recompositions so that future
    // reports of touch / layout drift can be diagnosed from logcat alone.
    // Cheap (one Log.d per recomposition where insets actually change).
    val density = LocalDensity.current
    val safeInsets = WindowInsets.safeDrawing
    SideEffect {
        val l = safeInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
        val t = safeInsets.getTop(density)
        val r = safeInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
        val b = safeInsets.getBottom(density)
        Log.d("LastTile-window", "safeDrawing px L=$l T=$t R=$r B=$b")
    }

    // Window-coordinate origin of the root Box's content area. The
    // floating shield drag overlay positions itself relative to this
    // origin so its IntOffset translates directly from window-space
    // touch positions emitted by the shield card's pointerInput.
    var rootContentOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    // Hardware back button is swallowed during the mandatory tutorial
    // so the user can't escape onto a half-bootstrapped game state.
    // Re-runs from the help button leave the back button working
    // normally (Android closes the activity).
    BackHandler(enabled = tutorial.state.active && tutorial.state.mandatory) {
        // Intentional no-op. The tutorial finishes via username save
        // on step 6, not via back press.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            // Activity is edge-to-edge (see MainActivity); pad the entire
            // game UI inside the safe-drawing region so the status bar,
            // gesture pill and any display cutout never overlap touchable
            // content. safeDrawing is a superset of systemBars (covers
            // displayCutout + IME too) and is the most defensive choice
            // for a single-screen game where every dp is touchable.
            // consumeWindowInsets prevents any nested layout from double-
            // padding when it queries WindowInsets again.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .consumeWindowInsets(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    rootContentOriginInWindow = coords.boundsInWindow().topLeft
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.title_wordmark),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 6.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tagline),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(stringResource(R.string.stat_score), state.score.toString())
                StatCard(stringResource(R.string.stat_turn), state.turn.toString())
                StatCard(
                    stringResource(R.string.stat_combo),
                    if (state.combo > 1) {
                        stringResource(R.string.combo_value_format, state.combo)
                    } else {
                        stringResource(R.string.combo_inactive)
                    },
                    highlight = state.combo > 1
                )
                ShieldStatCard(
                    count = state.shieldCount,
                    onTap = { handleShieldCardTap() },
                    onDragStart = { startWindow ->
                        if (state.shieldCount > 0) {
                            shieldDragWindowPos = startWindow
                        } else {
                            openShieldDialogOrTriggerAd()
                        }
                    },
                    onDragChange = { newWindow ->
                        if (shieldDragWindowPos != null) {
                            shieldDragWindowPos = newWindow
                        }
                    },
                    onDragFinish = { handleShieldDragEnd() },
                    highlighted = tutorialShieldCardHighlighted,
                    pulseAlpha = tutorialPulseAlpha
                )
            }

            Spacer(Modifier.height(20.dp))

            BoardView(
                state = state,
                onLayoutChange = { boardLayout = it },
                highlightedCells = tutorial.state.highlightedCells,
                spotlightActive = tutorialSpotlightActive,
                pulseAlpha = tutorialPulseAlpha
            )

            Spacer(Modifier.height(14.dp))

            StatusLine(state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact "?" help icon, 48dp Material touch target. Sits
                // to the left of Restart so the two main CTAs stay where
                // returning players expect them. Replaces the wide
                // "How to play" button from v0.1.7 — that button wrapped
                // its label to three lines because the row was too tight
                // for three full-width buttons. The "?" character is
                // inlined (no string resource) because it reads identically
                // across every supported locale, like the other universal
                // symbols already inlined in this file.
                val helpDescription = stringResource(R.string.btn_tutorial)
                IconButton(
                    onClick = { tutorial.start() },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = helpDescription }
                ) {
                    Text(
                        text = "?",
                        color = TextSecondary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { state.restart() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentAmber,
                        contentColor = Color(0xFF2B1810)
                    )
                ) {
                    Text(
                        stringResource(if (state.gameOver) R.string.btn_play_again else R.string.btn_restart),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = {
                        // v0.2.0: tutorial step 5 spotlights this button
                        // and requires a tap to advance to step 6 (which
                        // spotlights the player's row inside the now-open
                        // leaderboard). For non-Leaderboard tutorial
                        // steps in non-mandatory mode, the legacy
                        // graceful-skip behaviour is preserved.
                        if (tutorial.state.active) {
                            when (tutorial.state.currentStep) {
                                TutorialStep.Leaderboard -> tutorial.next()
                                TutorialStep.Username -> { /* leave at step 6 */ }
                                else -> if (!tutorial.state.mandatory) tutorial.skip()
                            }
                        }
                        showLeaderboard = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardAccent,
                        contentColor = TextPrimary
                    ),
                    modifier = if (tutorial.state.active &&
                        !tutorial.state.stepCompleted &&
                        tutorial.state.currentStep == TutorialStep.Leaderboard
                    ) {
                        Modifier.border(
                            3.dp,
                            TutorialSpotlight.copy(alpha = tutorialPulseAlpha),
                            RoundedCornerShape(20.dp)
                        )
                    } else Modifier
                ) {
                    Text(
                        stringResource(R.string.btn_leaderboard),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            }

            BottomAdBanner()
        }

        if (showLeaderboard) {
            LeaderboardDialog(
                entries = leaderboard.topScores(),
                onDismiss = {
                    // Mandatory tutorial step 6 spotlights the player's
                    // row inside this dialog. Closing the dialog mid-
                    // tutorial would orphan the spotlight target, so we
                    // refuse the dismiss until the user completes step 6.
                    if (tutorial.state.active &&
                        tutorial.state.mandatory &&
                        tutorial.state.currentStep == TutorialStep.Username
                    ) return@LeaderboardDialog
                    showLeaderboard = false
                },
                onClear = {
                    leaderboard.clear()
                    showLeaderboard = false
                },
                spotlightOwnRow = tutorial.state.active &&
                    !tutorial.state.stepCompleted &&
                    tutorial.state.currentStep == TutorialStep.Username,
                pulseAlpha = tutorialPulseAlpha,
                onOwnRowTap = { entry ->
                    usernameDialogCurrent = entry.displayName
                    usernameInlineError = null
                    usernameDialogMandatory = tutorial.state.active &&
                        tutorial.state.mandatory &&
                        tutorial.state.currentStep == TutorialStep.Username
                    showUsernameDialog = true
                },
                refreshTick = leaderboardRefreshTick
            )
        }

        if (showUsernameDialog) {
            UsernameDialog(
                isMandatory = usernameDialogMandatory,
                currentName = usernameDialogCurrent,
                isSaving = usernameSaving,
                inlineError = usernameInlineError,
                onDismiss = {
                    if (!usernameDialogMandatory) {
                        showUsernameDialog = false
                        usernameInlineError = null
                    }
                },
                onSave = { newName ->
                    if (usernameSaving) return@UsernameDialog
                    usernameInlineError = null
                    usernameSaving = true
                    coroutineScope.launch {
                        val result = UsernameRepository.changeUsername(newName)
                        usernameSaving = false
                        when (result) {
                            is UsernameRepository.ChangeResult.Success -> {
                                showUsernameDialog = false
                                usernameDialogCurrent = newName
                                // Force a refetch of the global tab so
                                // the new name is visible immediately.
                                leaderboardRefreshTick += 1
                                showToast(usernameSavedMsg)
                                // Mandatory step 6 finishes ONLY after a
                                // successful save. Mark the step done
                                // and let the existing 2s success-beat
                                // delay run before next() commits the
                                // tutorial_completed_once flag.
                                if (usernameDialogMandatory && tutorial.state.active) {
                                    tutorial.markStepCompleted()
                                }
                                usernameDialogMandatory = false
                            }
                            is UsernameRepository.ChangeResult.Taken -> {
                                usernameInlineError = usernameTakenMsg
                            }
                            is UsernameRepository.ChangeResult.CooldownActive -> {
                                showToast(
                                    String.format(
                                        usernameCooldownTemplate,
                                        result.daysRemaining
                                    )
                                )
                                showUsernameDialog = false
                            }
                            is UsernameRepository.ChangeResult.NetworkError -> {
                                showToast(usernameFailedMsg)
                            }
                        }
                    }
                }
            )
        }

        if (showShieldDialog) {
            ShieldRewardDialog(
                onDismiss = { showShieldDialog = false },
                onWatch = {
                    showShieldDialog = false
                    playRewardedAd()
                }
            )
        }

        // Floating shield indicator that follows the finger during a
        // long-press drag on the shield card. The Column above
        // captures its window origin so we can map the window-space
        // pointer coordinates back into local Box coordinates without
        // worrying about safe-drawing insets or scroll offsets.
        val dragPos = shieldDragWindowPos
        if (dragPos != null) {
            val density = LocalDensity.current
            val halfPx = with(density) { 24.dp.toPx() }
            val localX = (dragPos.x - rootContentOriginInWindow.x - halfPx).roundToInt()
            val localY = (dragPos.y - rootContentOriginInWindow.y - halfPx).roundToInt()
            Box(
                modifier = Modifier
                    .zIndex(20f)
                    .offset { IntOffset(localX, localY) }
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(AccentBlue.copy(alpha = 0.95f), AccentPurple.copy(alpha = 0.95f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.shield_drag_overlay_icon),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Tutorial overlay. Draws last in the root Box so it sits above
        // the board, the stat row, and the dialogs. The dim layer is
        // intentionally applied at low alpha (0.55) covering the whole
        // screen rather than cut around individual UI elements — the
        // simpler approach the spec offers when per-element cut-outs
        // would balloon the implementation. The instruction card sits
        // at the bottom and shows: step counter, instruction text, the
        // shield sub-hint when relevant, a Got it CTA, and a Skip text
        // button. A dialog open during the tutorial calls skip() at
        // its open site so the overlay doesn't collide with the dialog.
        if (tutorial.state.active) {
            TutorialOverlay(
                state = tutorial.state,
                onGotIt = { tutorial.next() },
                onSkip = { tutorial.skip() }
            )
        }
    }
}

// Layout snapshot of the BoardView so the GameScreen-level shield
// drag overlay can hit-test cells. originPx + cellSlotPx + viewport
// state are reported via onGloballyPositioned each time the layout
// settles (panning, board growth, viewport shifts).
data class BoardLayout(
    val rootOriginPx: Offset,
    val cellSlotPx: Float,
    val viewCount: Int,
    val originRow: Int,
    val originCol: Int,
    val padding: Float
) {
    fun cellAt(windowPos: Offset): Pair<Int, Int>? {
        val localX = windowPos.x - rootOriginPx.x - padding
        val localY = windowPos.y - rootOriginPx.y - padding
        if (localX < 0f || localY < 0f) return null
        val col = (localX / cellSlotPx).toInt()
        val row = (localY / cellSlotPx).toInt()
        if (row !in 0 until viewCount || col !in 0 until viewCount) return null
        return (originRow + row) to (originCol + col)
    }
}

@Composable
private fun ShieldStatCard(
    count: Int,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragChange: (Offset) -> Unit,
    onDragFinish: () -> Unit,
    // Tutorial spotlight on the KALKAN card during step 4. When
    // [highlighted] is true a 3.dp pulsing border is drawn around
    // the card with [pulseAlpha] driving the alpha. The card otherwise
    // renders identically — the existing tap and long-press-drag
    // gestures are unaffected.
    highlighted: Boolean = false,
    pulseAlpha: Float = 1f
) {
    var cardWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    val container = if (count == 0) {
        Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)))
    } else {
        Brush.verticalGradient(listOf(CardAccent, CardBg))
    }
    val highlightModifier = if (highlighted) {
        Modifier.border(
            3.dp,
            TutorialSpotlight.copy(alpha = pulseAlpha),
            RoundedCornerShape(14.dp)
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .then(highlightModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .onGloballyPositioned { coords ->
                cardWindowOrigin = coords.boundsInWindow().topLeft
            }
            // Long-press drag — drop on a hazard tile cures it. Tap
            // (clickable below) opens the Earn Shield rewarded-ad
            // dialog. The two gestures do not conflict because
            // clickable fires only on lift without movement past the
            // touch slop, while detectDragGesturesAfterLongPress
            // requires a hold.
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localStart ->
                        onDragStart(cardWindowOrigin + localStart)
                    },
                    onDragEnd = { onDragFinish() },
                    onDragCancel = { onDragFinish() },
                    onDrag = { change, _ ->
                        change.consume()
                        onDragChange(change.position + cardWindowOrigin)
                    }
                )
            }
            .clickable(onClick = onTap)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.stat_shield),
                fontSize = 10.sp,
                color = TextSecondary,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = count.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ShieldRewardDialog(onDismiss: () -> Unit, onWatch: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(CardAccent, CardBg)))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.shield_dialog_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.shield_dialog_body),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(R.string.shield_dialog_cancel),
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = onWatch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = Color(0xFF2B1810)
                        )
                    ) {
                        Text(
                            stringResource(R.string.shield_dialog_watch),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomAdBanner() {
    // Hosts an anchored adaptive AdMob banner. The slot reserves the
    // SDK-recommended height up front so the layout never jumps,
    // including before the ad loads or on devices without Google Play
    // Services where AdView construction fails and we fall back to a
    // transparent placeholder.
    //
    // Banner width is computed from the *container's* measured width,
    // not the screen width. On devices with display cutouts, foldables,
    // and any layout where the bottom row is narrower than the display,
    // feeding the SDK the screen width returns a creative wider than
    // the rendered slot — the SDK then letterboxes it with empty bands
    // on the sides. BoxWithConstraints exposes the parent-granted width
    // in dp, which is the actual container width after WindowInsets
    // padding has been consumed by the surrounding Column.
    val context = LocalContext.current
    val activity = context as? Activity
    val displayWidthDpFallback = LocalConfiguration.current.screenWidthDp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val containerWidthDp = if (maxWidth.value > 0f) {
            maxWidth.value.toInt()
        } else {
            displayWidthDpFallback
        }

        val adSize = remember(containerWidthDp, activity) {
            val ctx = activity ?: context
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, containerWidthDp)
        }
        val bannerModifier = Modifier
            .fillMaxWidth()
            .height(adSize.height.dp)

        val adView: AdView? = remember(context, adSize) {
            runCatching {
                AdView(context).apply {
                    // setAdSize MUST run before loadAd — the SDK captures
                    // the size at request time, so a later size change
                    // would leave the served creative out of sync with
                    // the reserved slot.
                    setAdSize(adSize)
                    adUnitId = AdConfig.bannerUnitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.i("BottomAdBanner", "Banner ad loaded.")
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.w(
                                "BottomAdBanner",
                                "Banner ad failed to load: ${error.code} ${error.message}"
                            )
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }.onFailure { e ->
                Log.w("BottomAdBanner", "AdView creation failed", e)
            }.getOrNull()
        }

        if (adView != null) {
            AndroidView(
                modifier = bannerModifier,
                factory = { adView }
            )
        } else {
            Box(modifier = bannerModifier)
        }
    }
}

private enum class LeaderboardTab { GLOBAL, LOCAL }

@Composable
private fun LeaderboardDialog(
    entries: List<LeaderboardEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    // v0.2.0: tutorial step 6 spotlights the player's own row inside
    // this dialog and prompts them to tap it to pick a name. The
    // spotlight + tap-to-name affordance also stays available for any
    // post-tutorial change-name action — the difference is whether the
    // dialog refuses to dismiss.
    spotlightOwnRow: Boolean = false,
    pulseAlpha: Float = 1f,
    onOwnRowTap: (FirebaseLeaderboard.LeaderboardEntry) -> Unit = {},
    refreshTick: Int = 0
) {
    // v0.2.0: default tab is GLOBAL so the username spotlight target
    // (player's own row in the global leaderboard) is visible without
    // an extra tap. Falls back to LOCAL if the global query is empty
    // — the user still sees their local scores.
    var tab by remember { mutableStateOf(LeaderboardTab.GLOBAL) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = !spotlightOwnRow,
            dismissOnClickOutside = !spotlightOwnRow
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(CardAccent, CardBg)))
                .padding(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.leaderboard_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LeaderboardTabButton(
                        label = stringResource(R.string.leaderboard_tab_global),
                        active = tab == LeaderboardTab.GLOBAL,
                        onClick = { tab = LeaderboardTab.GLOBAL }
                    )
                    LeaderboardTabButton(
                        label = stringResource(R.string.leaderboard_tab_local),
                        active = tab == LeaderboardTab.LOCAL,
                        onClick = { tab = LeaderboardTab.LOCAL }
                    )
                }
                Spacer(Modifier.height(14.dp))

                if (spotlightOwnRow) {
                    Text(
                        text = stringResource(R.string.tutorial_step_username_instruction),
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                when (tab) {
                    LeaderboardTab.LOCAL -> LocalLeaderboardContent(entries)
                    LeaderboardTab.GLOBAL -> GlobalLeaderboardContent(
                        spotlightOwnRow = spotlightOwnRow,
                        pulseAlpha = pulseAlpha,
                        onOwnRowTap = onOwnRowTap,
                        refreshTick = refreshTick
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = Color(0xFF2B1810)
                        )
                    ) { Text(stringResource(R.string.btn_close), fontWeight = FontWeight.Bold) }
                    if (tab == LeaderboardTab.LOCAL && entries.isNotEmpty()) {
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = onClear,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A1C1C),
                                contentColor = Color(0xFFEF9A9A)
                            )
                        ) { Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTabButton(label: String, active: Boolean, onClick: () -> Unit) {
    val container = if (active) AccentAmber else CardAccent
    val contentColor = if (active) Color(0xFF2B1810) else TextSecondary
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun LocalLeaderboardContent(entries: List<LeaderboardEntry>) {
    Text(
        text = stringResource(R.string.leaderboard_local_subtitle),
        color = TextSecondary,
        fontSize = 10.sp,
        letterSpacing = 2.sp
    )
    Spacer(Modifier.height(10.dp))
    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.leaderboard_empty),
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    } else {
        entries.forEachIndexed { idx, e ->
            LeaderboardRow(rank = idx + 1, entry = e)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun GlobalLeaderboardContent(
    spotlightOwnRow: Boolean = false,
    pulseAlpha: Float = 1f,
    onOwnRowTap: (FirebaseLeaderboard.LeaderboardEntry) -> Unit = {},
    refreshTick: Int = 0
) {
    var loadResult by remember { mutableStateOf<FirebaseLeaderboard.LoadResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }

    // Compose effective key from internal refresh button + external
    // refreshTick (bumped after a username change committed). Either
    // source forces a fresh fetch.
    val effectiveKey = refreshKey + refreshTick
    LaunchedEffect(effectiveKey) {
        isLoading = true
        loadResult = FirebaseLeaderboard.loadLeaderboard(forceRefresh = effectiveKey > 0)
        isLoading = false
    }

    Text(
        text = stringResource(R.string.leaderboard_powered_by),
        color = TextSecondary,
        fontSize = 10.sp,
        letterSpacing = 2.sp
    )
    Spacer(Modifier.height(12.dp))

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentAmber, strokeWidth = 3.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.leaderboard_loading),
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        loadResult is FirebaseLeaderboard.LoadResult.Failure -> {
            Text(
                text = stringResource(R.string.leaderboard_load_failed),
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { refreshKey += 1 }) {
                Text(
                    text = stringResource(R.string.leaderboard_retry),
                    color = AccentAmber,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        loadResult is FirebaseLeaderboard.LoadResult.Success -> {
            val success = loadResult as FirebaseLeaderboard.LoadResult.Success
            // playerEntry is null only when auth is missing or the user
            // doc is missing; both are exceptional. The empty-state path
            // is therefore only taken when there are no top entries and
            // also no player doc to pin.
            if (success.topEntries.isEmpty() && success.playerEntry == null) {
                Text(
                    text = stringResource(R.string.leaderboard_empty_global),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                RefreshRow(success.fetchedAtMs) { refreshKey += 1 }
            } else {
                LeaderboardListWithPinning(
                    success = success,
                    onRefresh = { refreshKey += 1 },
                    spotlightOwnRow = spotlightOwnRow,
                    pulseAlpha = pulseAlpha,
                    onOwnRowTap = onOwnRowTap
                )
            }
        }

        else -> {
            // Initial null state before first load resolves — should be
            // transient, but render a safe empty state just in case.
            Text(
                text = stringResource(R.string.leaderboard_load_failed),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun LeaderboardListWithPinning(
    success: FirebaseLeaderboard.LoadResult.Success,
    onRefresh: () -> Unit,
    spotlightOwnRow: Boolean = false,
    pulseAlpha: Float = 1f,
    onOwnRowTap: (FirebaseLeaderboard.LeaderboardEntry) -> Unit = {}
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val playerIndexInTop = remember(success) {
        success.topEntries.indexOfFirst { it.isCurrentPlayer }
    }

    // Derive pin state from scroll position without triggering
    // recomposition on every scroll pixel — only when the boundary
    // condition changes.
    val playerAboveViewport by remember {
        androidx.compose.runtime.derivedStateOf {
            playerIndexInTop >= 0 &&
                listState.firstVisibleItemIndex > playerIndexInTop
        }
    }

    // v0.2.0: the bottom pinned player row is ALWAYS shown when
    // playerEntry is non-null. That ensures tutorial step 6 has a
    // tap target on a fresh install (when the player is not in the
    // top 100 because their score is still 0). The top pin still
    // appears only when the player has scrolled past their own row
    // and that row is in the top 100, so the pinned row at the top
    // doubles as a "jump back to me" affordance.
    val showTopPin = success.playerInTop && playerAboveViewport

    Column(modifier = Modifier.fillMaxWidth()) {
        // Pinned to top edge when player has scrolled their row above viewport.
        if (showTopPin && success.playerEntry != null) {
            FirestoreLeaderboardRow(
                entry = success.playerEntry,
                isPinned = true,
                spotlightOwnRow = spotlightOwnRow,
                pulseAlpha = pulseAlpha,
                onOwnRowTap = onOwnRowTap
            )
            Spacer(Modifier.height(4.dp))
        }

        // Scrollable main list. Capped at 320 dp so the dialog doesn't
        // overflow on small phones — LazyColumn scrolls internally.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(success.topEntries.size) { idx ->
                    FirestoreLeaderboardRow(
                        entry = success.topEntries[idx],
                        isPinned = false,
                        spotlightOwnRow = spotlightOwnRow,
                        pulseAlpha = pulseAlpha,
                        onOwnRowTap = onOwnRowTap
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // Always show the player's own row pinned at the bottom. When
        // the player is in the top 100 they will appear twice — once
        // in the scrollable list and once pinned — and a thin
        // separator above the pin makes the duplication read as
        // intentional.
        if (success.playerEntry != null) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TextSecondary.copy(alpha = 0.25f))
            )
            Spacer(Modifier.height(8.dp))
            FirestoreLeaderboardRow(
                entry = success.playerEntry,
                isPinned = true,
                spotlightOwnRow = spotlightOwnRow,
                pulseAlpha = pulseAlpha,
                onOwnRowTap = onOwnRowTap
            )
        }

        Spacer(Modifier.height(8.dp))
        RefreshRow(success.fetchedAtMs, onRefresh)
    }
}

@Composable
private fun RefreshRow(fetchedAtMs: Long, onRefresh: () -> Unit) {
    val ageMin = ((System.currentTimeMillis() - fetchedAtMs) / 60_000L)
        .coerceAtLeast(0L).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.leaderboard_updated_ago, ageMin),
            color = TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        TextButton(onClick = onRefresh) {
            Text(
                text = stringResource(R.string.leaderboard_refresh),
                color = AccentAmber,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun FirestoreLeaderboardRow(
    entry: FirebaseLeaderboard.LeaderboardEntry,
    isPinned: Boolean,
    // v0.2.0: when [spotlightOwnRow] is true and this row belongs to
    // the current player, render a pulsing highlight border using
    // [pulseAlpha] from the shared tutorial transition. The row is
    // also clickable on the player's own line — taps open the rename
    // dialog regardless of whether the spotlight is active, so the
    // affordance stays available after the tutorial is done.
    spotlightOwnRow: Boolean = false,
    pulseAlpha: Float = 1f,
    onOwnRowTap: (FirebaseLeaderboard.LeaderboardEntry) -> Unit = {}
) {
    val accent = when (entry.rank) {
        1 -> AccentGold
        2 -> Color(0xFFCFD8DC)
        3 -> Color(0xFFD7A26B)
        else -> TextSecondary
    }
    val container = if (entry.isCurrentPlayer) {
        Brush.verticalGradient(listOf(Color(0xFF263444), Color(0xFF18222E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF1F2A38), Color(0xFF141C26)))
    }
    val spotlight = entry.isCurrentPlayer && spotlightOwnRow
    val borderColor: Color
    val borderWidth = when {
        spotlight -> 3.dp
        entry.isCurrentPlayer || isPinned -> 1.5.dp
        else -> 0.dp
    }
    borderColor = when {
        spotlight -> TutorialSpotlight.copy(alpha = pulseAlpha)
        entry.isCurrentPlayer || isPinned -> AccentAmber
        else -> Color.Transparent
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(container)
        .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
        .then(
            if (entry.isCurrentPlayer) {
                Modifier.clickable { onOwnRowTap(entry) }
            } else Modifier
        )
        .padding(horizontal = 12.dp, vertical = 8.dp)
    // Em-dash placeholder for ranks the user hasn't yet earned (fresh
    // install, score == 0). Shown in the rank column AND the score
    // column so a player without a submitted run reads as "—  Name  —"
    // rather than "#null  Name  0".
    val rankText = if (entry.rank != null) {
        stringResource(R.string.leaderboard_rank_format, entry.rank)
    } else {
        "—"
    }
    val scoreText = if (entry.bestScore > 0L) entry.bestScore.toString() else "—"
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rankText,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = entry.displayName,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
        if (entry.isCurrentPlayer) {
            // Trailing pencil glyph hints at the rename affordance.
            // Plain Unicode "✎" avoids pulling in the material-icons
            // dependency for a single one-off icon.
            Text(
                text = "✎",
                color = AccentAmber,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text(
            text = scoreText,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private val DATE_FMT = SimpleDateFormat("MMM d", Locale.US)

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    val accent = when (rank) {
        1 -> AccentGold
        2 -> Color(0xFFCFD8DC)
        3 -> Color(0xFFD7A26B)
        else -> TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1F2A38), Color(0xFF141C26))))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.leaderboard_rank_format, rank),
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(modifier = Modifier.padding(end = 10.dp)) {
            Text(
                text = entry.score.toString(),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = stringResource(
                    R.string.leaderboard_entry_meta,
                    entry.turn,
                    entry.maxSize
                ),
                color = TextSecondary,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (entry.timestamp > 0) DATE_FMT.format(Date(entry.timestamp)) else "",
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun StatusLine(state: GameState) {
    val cleansedCount = state.lastHazardsCleared
    val cleansedText = if (cleansedCount > 0) {
        pluralStringResource(R.plurals.cleansed_hazards, cleansedCount, cleansedCount)
    } else null
    val (text, color) = when {
        state.gameOver -> stringResource(R.string.status_game_over) to Color(0xFFEF5350)
        state.lastSplit != null -> stringResource(R.string.status_split) to AccentBlue
        state.unlockedTargets.isNotEmpty() -> stringResource(R.string.status_frame_opened) to AccentPurple
        state.selected != null -> {
            val sel = state.selected!!
            val tile = state.board[sel.first][sel.second]
            val canSplit = tile is Tile.Normal && tile.value >= 2
            val hint = stringResource(
                if (canSplit) R.string.status_hint_match_or_split else R.string.status_hint_match
            )
            hint to AccentGold
        }
        cleansedText != null -> cleansedText to AccentGreen
        else -> null to Color.Transparent
    }
    if (text != null) {
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontWeight = if (state.gameOver) FontWeight.Black else FontWeight.Bold,
            letterSpacing = if (state.gameOver) 4.sp else 0.5.sp
        )
        Spacer(Modifier.height(10.dp))
    } else {
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, highlight: Boolean = false) {
    val container = if (highlight) Brush.verticalGradient(listOf(Color(0xFF3C2A1A), Color(0xFF2A1F14))) else Brush.verticalGradient(listOf(CardAccent, CardBg))
    val valueColor = if (highlight) AccentAmber else TextPrimary
    Box(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondary,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = valueColor
            )
        }
    }
}

private const val VIEWPORT_SIZE = 7

@Composable
private fun BoardView(
    state: GameState,
    onLayoutChange: (BoardLayout) -> Unit = {},
    // Tutorial spotlight inputs. When [spotlightActive] is true,
    // unlocked cells outside [highlightedCells] paint a 0.45-alpha
    // black overlay on top of their content (dim) and the highlighted
    // cells render a pulsing yellow border driven by [pulseAlpha].
    // Default values keep BoardView's existing call sites working.
    highlightedCells: Set<Pair<Int, Int>> = emptySet(),
    spotlightActive: Boolean = false,
    pulseAlpha: Float = 1f
) {
    val cellSize: Dp = 48.dp
    val tilePadding = 3.dp
    val cellSlot = cellSize + tilePadding * 2
    val boardPadding = 8.dp

    var draggedFrom by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val cellSlotPx = with(density) { cellSlot.toPx() }
    val boardPaddingPx = with(density) { boardPadding.toPx() }

    val viewCount = VIEWPORT_SIZE
    val needsPan = state.size > viewCount
    val maxOrigin = (state.size - viewCount).coerceAtLeast(0)

    var originRow by remember(state.size) {
        mutableStateOf(((state.size - viewCount) / 2).coerceIn(0, maxOrigin))
    }
    var originCol by remember(state.size) {
        mutableStateOf(((state.size - viewCount) / 2).coerceIn(0, maxOrigin))
    }

    // Republish layout whenever viewport state changes so the parent
    // shield drag overlay knows where to hit-test.
    var boardWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(boardWindowOrigin, originRow, originCol, cellSlotPx) {
        onLayoutChange(
            BoardLayout(
                rootOriginPx = boardWindowOrigin,
                cellSlotPx = cellSlotPx,
                viewCount = viewCount,
                originRow = originRow,
                originCol = originCol,
                padding = boardPaddingPx
            )
        )
    }

    // Auto-follow: whenever the engine tags a new focus tile (last merge,
    // split origin, or selection), slide the viewport so that tile lands
    // near the center. Only nudges when the focus is actually outside the
    // visible window or near its edge, so the camera doesn't jitter on
    // every tap.
    val focus = state.lastFocus
    LaunchedEffect(focus, state.size) {
        if (!needsPan || focus == null) return@LaunchedEffect
        val (fr, fc) = focus
        val edgePad = 1
        val needsRecenter = fr < originRow + edgePad ||
            fr >= originRow + viewCount - edgePad ||
            fc < originCol + edgePad ||
            fc >= originCol + viewCount - edgePad
        if (!needsRecenter) return@LaunchedEffect
        originRow = (fr - viewCount / 2).coerceIn(0, maxOrigin)
        originCol = (fc - viewCount / 2).coerceIn(0, maxOrigin)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0A1018), Color(0xFF05090D))))
                .padding(boardPadding)
                .onGloballyPositioned { coords ->
                    boardWindowOrigin = coords.boundsInWindow().topLeft
                }
        ) {
            Column {
                for (rIdx in 0 until viewCount) {
                    val r = originRow + rIdx
                    Row {
                        for (cIdx in 0 until viewCount) {
                            val c = originCol + cIdx
                            val pos = r to c
                            val isUnlocked = state.unlocked[r][c]
                            val justOpened = pos in state.unlockedTargets
                            val tile = state.board[r][c]
                            val isDraggable = tile is Tile.Normal
                            val isBeingDragged = draggedFrom == pos
                            val isPressed = pos in state.pressedTiles

                            val isHighlighted = pos in highlightedCells
                            TileView(
                                pos = pos,
                                tile = tile,
                                unlocked = isUnlocked,
                                selected = state.selected == pos,
                                justOpened = justOpened,
                                pressed = isPressed,
                                size = cellSize,
                                padding = tilePadding,
                                draggable = isDraggable,
                                isBeingDragged = isBeingDragged,
                                dragOffset = if (isBeingDragged) dragOffset else Offset.Zero,
                                onDragStart = {
                                    draggedFrom = pos
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { delta -> dragOffset += delta },
                                onDragEnd = {
                                    val from = draggedFrom
                                    if (from != null) {
                                        val dr = (dragOffset.y / cellSlotPx).roundToInt()
                                        val dc = (dragOffset.x / cellSlotPx).roundToInt()
                                        val target = (from.first + dr) to (from.second + dc)
                                        if (!state.tryDragMerge(from, target)) {
                                            state.tryMove(from, target)
                                        }
                                    }
                                    draggedFrom = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    draggedFrom = null
                                    dragOffset = Offset.Zero
                                },
                                onClick = { state.tap(r, c) },
                                tutorialDim = spotlightActive && !isHighlighted,
                                tutorialHighlighted = spotlightActive && isHighlighted,
                                tutorialPulseAlpha = pulseAlpha
                            )
                        }
                    }
                }
            }
        }

        if (needsPan) {
            Spacer(Modifier.height(10.dp))
            MiniMap(
                state = state,
                viewCount = viewCount,
                originRow = originRow,
                originCol = originCol,
                onRecenter = { r, c ->
                    originRow = (r - viewCount / 2).coerceIn(0, maxOrigin)
                    originCol = (c - viewCount / 2).coerceIn(0, maxOrigin)
                }
            )
        }
    }
}

@Composable
private fun MiniMap(
    state: GameState,
    viewCount: Int,
    originRow: Int,
    originCol: Int,
    onRecenter: (Int, Int) -> Unit
) {
    val widthDp = 96.dp
    val cellDp = widthDp / state.size
    Box(
        modifier = Modifier
            .shadow(2.dp, RoundedCornerShape(6.dp), clip = false)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0A1018))
            .padding(2.dp)
    ) {
        Column {
            for (r in 0 until state.size) {
                Row {
                    for (c in 0 until state.size) {
                        val inViewport = r in originRow until originRow + viewCount &&
                            c in originCol until originCol + viewCount
                        val tile = state.board[r][c]
                        val isUnlocked = state.unlocked[r][c]
                        val color = miniMapColor(tile, isUnlocked)
                        Box(
                            modifier = Modifier
                                .size(cellDp)
                                .padding(0.5.dp)
                                .background(color)
                                .border(
                                    if (inViewport) 1.dp else 0.dp,
                                    if (inViewport) AccentGold.copy(alpha = 0.85f) else Color.Transparent
                                )
                                .clickable { onRecenter(r, c) }
                        )
                    }
                }
            }
        }
    }
}

private fun miniMapColor(tile: Tile, unlocked: Boolean): Color {
    if (!unlocked) return Color(0xFF0E141C)
    return when (tile) {
        Tile.Empty -> Color(0xFF1F2A38)
        is Tile.Normal -> Color(0xFFB7C4D6)
        is Tile.Fire -> Color(0xFFE53935)
        is Tile.Ice -> Color(0xFF29B6F6)
        is Tile.Poison -> Color(0xFFAB47BC)
    }
}

@Composable
private fun TileView(
    // pos is the tile's logical board coordinate. It is wired through as
    // a parameter (rather than only being captured by callback closures)
    // so that the pointerInput modifier below can be keyed on it. Without
    // this key, Modifier.pointerInput(Unit) would cache the suspend block
    // and its captured callbacks at first composition; subsequent
    // viewport pans (which shift the (originRow, originCol) origin in
    // BoardView) would silently invoke stale callbacks, animating a tile
    // 1-3 cells away from the one the player actually touched.
    pos: Pair<Int, Int>,
    tile: Tile,
    unlocked: Boolean,
    selected: Boolean,
    justOpened: Boolean,
    pressed: Boolean,
    size: Dp,
    padding: Dp,
    draggable: Boolean,
    isBeingDragged: Boolean,
    dragOffset: Offset,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onClick: () -> Unit,
    // Tutorial spotlight inputs. tutorialHighlighted overrides the
    // existing border with a pulsing warm-yellow stroke (driven by
    // tutorialPulseAlpha). tutorialDim paints a 0.45-alpha black scrim
    // on top of the tile content so non-target cells visually recede.
    // Both default off so non-tutorial composition is unchanged.
    tutorialDim: Boolean = false,
    tutorialHighlighted: Boolean = false,
    tutorialPulseAlpha: Float = 1f
) {
    val brush: Brush
    val label: String
    val subLabel: String

    if (!unlocked) {
        brush = Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF05080B)))
        label = ""
        subLabel = ""
    } else {
        when (tile) {
            Tile.Empty -> {
                brush = Brush.verticalGradient(listOf(Color(0xFF1C242F), Color(0xFF111821)))
                label = ""
                subLabel = ""
            }
            is Tile.Normal -> {
                brush = tileBrush(tile.value)
                label = tile.value.toString()
                subLabel = ""
            }
            is Tile.Fire -> {
                brush = Brush.verticalGradient(listOf(Color(0xFFFF6F60), Color(0xFFC62828)))
                label = tile.value.toString()
                subLabel = stringResource(R.string.hazard_fire_subtitle, 5 - tile.age)
            }
            is Tile.Ice -> {
                brush = Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFF0277BD)))
                label = tile.value.toString()
                subLabel = stringResource(R.string.hazard_ice_subtitle, 3 - tile.age)
            }
            is Tile.Poison -> {
                brush = Brush.verticalGradient(listOf(Color(0xFFCE93D8), Color(0xFF6A1B9A)))
                label = tile.value.toString()
                subLabel = stringResource(R.string.hazard_poison_subtitle, 7 - tile.age)
            }
        }
    }

    // Tutorial spotlight border takes precedence over the gameplay
    // borders (selected / justOpened / pressed / draggable). Without
    // this override the merge-pair cells on Step 1, which are also
    // draggable, would render the teal "draggable" border alongside
    // the pulse and the spotlight wouldn't read as the primary cue.
    val borderColor = when {
        tutorialHighlighted -> TutorialSpotlight.copy(alpha = tutorialPulseAlpha)
        selected -> AccentGold
        justOpened -> AccentPurple
        pressed -> Color(0xFF000000).copy(alpha = 0.55f)
        draggable && !isBeingDragged -> Color(0xFF80CBC4).copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val borderWidth = when {
        tutorialHighlighted -> 3.dp
        selected -> 4.dp
        justOpened -> 3.dp
        pressed -> 2.dp
        draggable -> 2.dp
        else -> 0.dp
    }

    // Key on `pos` (not Unit). When BoardView pans the viewport,
    // (originRow, originCol) shift and each TileView slot's pos changes;
    // re-keying forces pointerInput to relaunch its suspend block and
    // re-capture the latest onDragStart / onDrag / onDragEnd from the
    // surrounding closure. Otherwise a slot would keep firing the FIRST
    // composition's callbacks, which capture the pre-pan pos, and the
    // wrong tile would respond to the drag.
    val dragModifier = if (draggable && unlocked) {
        Modifier.pointerInput(pos) {
            detectDragGestures(
                onDragStart = { onDragStart() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragCancel() }
            )
        }
    } else {
        Modifier
    }

    val shadowDp = when {
        !unlocked -> 0.dp
        pressed && !isBeingDragged -> 0.dp
        label.isNotEmpty() -> 3.dp
        else -> 0.dp
    }

    Box(
        modifier = Modifier
            .padding(padding)
            .size(size)
            .zIndex(if (isBeingDragged) 10f else 0f)
            .graphicsLayer {
                if (isBeingDragged) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.08f
                    scaleY = 1.08f
                } else if (pressed) {
                    scaleX = 0.92f
                    scaleY = 0.92f
                    alpha = 0.78f
                }
            }
            .shadow(shadowDp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .then(dragModifier)
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!unlocked) {
            Text(
                text = stringResource(R.string.tile_locked_icon),
                color = Color.White.copy(alpha = 0.1f),
                fontSize = 11.sp
            )
        } else if (label.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (label.length >= 5) 9.sp else if (label.length >= 4) 12.sp else 17.sp,
                    letterSpacing = 0.5.sp
                )
                if (subLabel.isNotEmpty()) {
                    Text(
                        text = subLabel,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // Tutorial dim scrim. Painted last in the Box content so it
        // covers the label as well as the gradient — the cells the
        // player should not interact with read as visibly muted.
        // Locked cells skip this because the locked gradient is
        // already darker than the dim layer would make it; a second
        // overlay would just turn them into solid black squares.
        if (tutorialDim && unlocked) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }
    }
}

private fun tileBrush(value: Int): Brush {
    val (top, bottom) = when (value) {
        1 -> Color(0xFF4F626F) to Color(0xFF2E3A43)
        2 -> Color(0xFF607D8B) to Color(0xFF37474F)
        4 -> Color(0xFF78909C) to Color(0xFF455A64)
        8 -> Color(0xFF81C784) to Color(0xFF388E3C)
        16 -> Color(0xFF66BB6A) to Color(0xFF1B5E20)
        32 -> Color(0xFF43A047) to Color(0xFF1B5E20)
        64 -> Color(0xFFFFEE58) to Color(0xFFF57F17)
        128 -> Color(0xFFFFCA28) to Color(0xFFFF8F00)
        256 -> Color(0xFFFFB74D) to Color(0xFFE65100)
        512 -> Color(0xFFFF8A65) to Color(0xFFBF360C)
        1024 -> Color(0xFFFF7043) to Color(0xFF8E2D05)
        2048 -> Color(0xFFE57373) to Color(0xFFB71C1C)
        4096 -> Color(0xFFBA68C8) to Color(0xFF4A148C)
        else -> Color(0xFF9575CD) to Color(0xFF311B92)
    }
    return Brush.verticalGradient(listOf(top, bottom))
}

@Composable
private fun TutorialOverlay(
    state: TutorialState,
    onGotIt: () -> Unit,
    onSkip: () -> Unit
) {
    // v0.1.8: dim layer removed — full-opacity board + scoreboard read
    // better against the dark theme than the muddy 0.55-alpha black scrim
    // we shipped in v0.1.7. The instruction card carries the tutorial UI
    // alone, anchored at the bottom with safe-area padding so it never
    // collides with the system nav bar. The Column's fillMaxSize +
    // verticalArrangement = Bottom keeps the card pinned without consuming
    // the underlying click surface — pointerInput on the card itself
    // absorbs taps within its bounds.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(CardAccent, CardBg)))
                    .padding(18.dp)
                    // Absorb taps so they don't fall through to the
                    // dimmed game surface underneath the card.
                    .pointerInput(Unit) {}
            ) {
                // Animate the inner content (counter, text, buttons)
                // between steps and into the success beat. The card
                // surface itself stays fixed — only the content fades
                // and slides. Transitions into the success beat use a
                // scaleIn pop instead of slide so the checkmark feels
                // like a small celebration; outgoing content always
                // slides up + fades.
                AnimatedContent(
                    targetState = state.currentStep to state.stepCompleted,
                    transitionSpec = {
                        val targetIsSuccess = targetState.second
                        val enter = if (targetIsSuccess) {
                            fadeIn(tween(250)) +
                                scaleIn(initialScale = 0.7f, animationSpec = tween(250))
                        } else {
                            fadeIn(tween(250)) +
                                slideInVertically(tween(250)) { it / 4 }
                        }
                        val exit = fadeOut(tween(200)) +
                            slideOutVertically(tween(200)) { -it / 4 }
                        enter togetherWith exit
                    },
                    label = "tutorial-content"
                ) { (step, completed) ->
                    if (completed) {
                        TutorialSuccessContent(step = step)
                    } else {
                        TutorialStepContent(
                            step = step,
                            instructionTextRes = state.instructionTextRes,
                            ctaTextRes = state.ctaTextRes,
                            mandatory = state.mandatory,
                            onGotIt = onGotIt,
                            onSkip = onSkip
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStepContent(
    step: TutorialStep,
    instructionTextRes: Int,
    ctaTextRes: Int,
    mandatory: Boolean,
    onGotIt: () -> Unit,
    onSkip: () -> Unit
) {
    // fillMaxWidth + horizontalAlignment center the column inside the
    // AnimatedContent slot, which defaults to Alignment.TopStart and
    // would otherwise hug the leading edge — the "Nice merge!" line
    // and step counter were rendering left-aligned in v0.1.7-polish.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(
                R.string.tutorial_step_counter,
                step.index,
                TutorialStep.TOTAL_INTERACTIVE_STEPS
            ),
            color = TextSecondary,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        if (instructionTextRes != 0) {
            Text(
                text = stringResource(instructionTextRes),
                color = TextPrimary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        if (step == TutorialStep.Shield) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tutorial_step_shield_subhint),
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Skip is hidden during the mandatory first-launch flow —
            // the user has to complete step 6 to dismiss the overlay.
            if (!mandatory) {
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.tutorial_cta_skip),
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.size(12.dp))
            }
            if (ctaTextRes != 0) {
                Button(
                    onClick = onGotIt,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentAmber,
                        contentColor = Color(0xFF2B1810)
                    )
                ) {
                    Text(
                        stringResource(ctaTextRes),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialSuccessContent(step: TutorialStep) {
    // Success beat shown for the auto-advanced steps after the user
    // performs the scripted action. Replaces the step counter with a
    // checkmark glyph (no material-icons artifact in the dependency
    // graph, so a tinted Unicode "✓" stands in) and the instruction
    // line with a localized celebration message. Skip / Got it are
    // omitted on purpose — the overlay self-advances on a 2s timer
    // started by GameScreen, so giving the user a button to dismiss
    // would race the timer.
    val successRes = when (step) {
        TutorialStep.Merge -> R.string.tutorial_success_merge
        TutorialStep.Frame -> R.string.tutorial_success_frame
        TutorialStep.Shield -> R.string.tutorial_success_shield
        TutorialStep.Username -> R.string.username_change_saved
        else -> 0
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✓",
            color = AccentAmber,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        if (successRes != 0) {
            Text(
                text = stringResource(successRes),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
