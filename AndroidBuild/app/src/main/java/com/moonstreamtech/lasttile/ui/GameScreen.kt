package com.moonstreamtech.lasttile.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moonstreamtech.lasttile.GameState
import com.moonstreamtech.lasttile.GpgsLeaderboard
import com.moonstreamtech.lasttile.LeaderboardEntry
import com.moonstreamtech.lasttile.LocalLeaderboard
import com.moonstreamtech.lasttile.R
import com.moonstreamtech.lasttile.Tile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val state = remember(activity) {
        GameState(
            prefs = prefs,
            leaderboard = leaderboard,
            onRunSubmitted = { finalScore ->
                GpgsLeaderboard.submitScore(context, finalScore.toLong())
            }
        )
    }
    var showLeaderboard by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    if (state.combo > 1) "×${state.combo}" else "—",
                    highlight = state.combo > 1
                )
            }

            Spacer(Modifier.height(20.dp))

            BoardView(state)

            Spacer(Modifier.height(14.dp))

            StatusLine(state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
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
                    onClick = { showLeaderboard = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardAccent,
                        contentColor = TextPrimary
                    )
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
                activity = activity,
                onDismiss = { showLeaderboard = false },
                onClear = {
                    leaderboard.clear()
                    showLeaderboard = false
                }
            )
        }
    }
}

@Composable
private fun BottomAdBanner() {
    // TODO: enable when AdMob is integrated.
    // Layout slot is reserved (50.dp) but currently hidden so the app
    // truthfully ships with no ads. Swap the height back and drop a
    // BannerAdView in here when the SDK lands.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.dp)
    )
}

private enum class LeaderboardTab { GLOBAL, LOCAL }

@Composable
private fun LeaderboardDialog(
    entries: List<LeaderboardEntry>,
    activity: Activity?,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    var tab by remember { mutableStateOf(LeaderboardTab.LOCAL) }
    var globalStatus by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
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

                when (tab) {
                    LeaderboardTab.LOCAL -> LocalLeaderboardContent(entries)
                    LeaderboardTab.GLOBAL -> GlobalLeaderboardContent(
                        activity = activity,
                        status = globalStatus,
                        onStatusChange = { globalStatus = it }
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
    activity: Activity?,
    status: String?,
    onStatusChange: (String?) -> Unit
) {
    val configured = GpgsLeaderboard.isConfigured()
    val context = LocalContext.current
    val activityUnavailableMsg = stringResource(R.string.leaderboard_activity_unavailable)
    val openingMsg = stringResource(R.string.leaderboard_opening)
    val notConfiguredShortMsg = stringResource(R.string.leaderboard_not_configured_short)
    val signInHintMsg = stringResource(R.string.leaderboard_sign_in_hint)
    Text(
        text = stringResource(R.string.leaderboard_powered_by),
        color = TextSecondary,
        fontSize = 10.sp,
        letterSpacing = 2.sp
    )
    Spacer(Modifier.height(12.dp))

    if (!configured) {
        Text(
            text = stringResource(R.string.leaderboard_not_configured_long),
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        return
    }

    Text(
        text = stringResource(R.string.leaderboard_compete),
        color = TextSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            val a = activity
            if (a == null) {
                onStatusChange(activityUnavailableMsg)
                return@Button
            }
            onStatusChange(openingMsg)
            GpgsLeaderboard.getLeaderboardIntent(a) { result ->
                when (result) {
                    is GpgsLeaderboard.IntentResult.Ready -> {
                        onStatusChange(null)
                        runCatching { a.startActivity(result.intent) }
                            .onFailure {
                                onStatusChange(
                                    context.getString(
                                        R.string.leaderboard_open_failed,
                                        it.message ?: ""
                                    )
                                )
                            }
                    }
                    GpgsLeaderboard.IntentResult.NotConfigured ->
                        onStatusChange(notConfiguredShortMsg)
                    is GpgsLeaderboard.IntentResult.Failed ->
                        onStatusChange(signInHintMsg)
                }
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentBlue,
            contentColor = Color(0xFF0B1A24)
        )
    ) {
        Text(
            stringResource(R.string.btn_view_online_leaderboard),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
    if (status != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = status,
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
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
            text = "#$rank",
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
                text = "t${entry.turn} · ${entry.maxSize}×${entry.maxSize}",
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
private fun BoardView(state: GameState) {
    val cellSize: Dp = 48.dp
    val tilePadding = 3.dp
    val cellSlot = cellSize + tilePadding * 2

    var draggedFrom by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val cellSlotPx = with(density) { cellSlot.toPx() }

    val viewCount = VIEWPORT_SIZE
    val needsPan = state.size > viewCount
    val maxOrigin = (state.size - viewCount).coerceAtLeast(0)

    var originRow by remember(state.size) {
        mutableStateOf(((state.size - viewCount) / 2).coerceIn(0, maxOrigin))
    }
    var originCol by remember(state.size) {
        mutableStateOf(((state.size - viewCount) / 2).coerceIn(0, maxOrigin))
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
                .padding(8.dp)
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

                            TileView(
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
                                onClick = { state.tap(r, c) }
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
    onClick: () -> Unit
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

    val borderColor = when {
        selected -> AccentGold
        justOpened -> AccentPurple
        pressed -> Color(0xFF000000).copy(alpha = 0.55f)
        draggable && !isBeingDragged -> Color(0xFF80CBC4).copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val borderWidth = when {
        selected -> 4.dp
        justOpened -> 3.dp
        pressed -> 2.dp
        draggable -> 2.dp
        else -> 0.dp
    }

    val dragModifier = if (draggable && unlocked) {
        Modifier.pointerInput(Unit) {
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
                text = "🔒",
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
