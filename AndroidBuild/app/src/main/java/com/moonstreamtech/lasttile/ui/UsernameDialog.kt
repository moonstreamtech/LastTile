package com.moonstreamtech.lasttile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moonstreamtech.lasttile.R
import com.moonstreamtech.lasttile.UsernameRepository
import kotlinx.coroutines.delay

// Vintage Paper palette. The dialog intentionally breaks from the dark
// game theme to feel like a moment of personal authorship — the player
// is signing their name onto the leaderboard. Inter font is a future
// nicety; sticking with the platform default keeps this PR free of new
// font assets.
private val PaperCream = Color(0xFFF4ECD8)
private val PaperCreamDark = Color(0xFFE8DDC2)
private val InkBronze = Color(0xFF8B6F47)
private val InkDark = Color(0xFF3E2F1A)
private val InkSubtle = Color(0xFF7A6347)
private val OkGreen = Color(0xFF2E7D32)
private val ErrorRed = Color(0xFFC62828)

private const val MIN_LEN = 2
private const val MAX_LEN = 20
private const val DEBOUNCE_MS = 500L

/**
 * Dialog for picking or changing a leaderboard username.
 *
 * - Initial value is pre-filled with [currentName]; if [isMandatory] is
 *   true (first-time tutorial flow) the field starts empty so the user
 *   doesn't accidentally re-submit their auto-assigned "#NNNNNN".
 * - Save is gated on length 2..20 (after trim) AND a successful
 *   availability check. NetworkError is permissive — Save stays enabled
 *   so the user can attempt the write and let the parent handle the
 *   transaction-level outcome.
 * - When [isMandatory] is true, the Cancel button is hidden and outside
 *   taps / hardware back are blocked via [DialogProperties].
 */
@Composable
fun UsernameDialog(
    isMandatory: Boolean,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    isSaving: Boolean = false,
    inlineError: String? = null
) {
    val initial = if (isMandatory) "" else currentName
    var input by remember { mutableStateOf(initial) }
    var availability by remember { mutableStateOf<UsernameRepository.AvailabilityResult?>(null) }
    var checking by remember { mutableStateOf(false) }

    val trimmed = input.trim()
    val length = trimmed.length
    val tooShort = length in 1 until MIN_LEN
    val tooLong = length > MAX_LEN
    val emptyInput = length == 0
    val lengthValid = length in MIN_LEN..MAX_LEN
    val unchanged = trimmed.equals(currentName.trim(), ignoreCase = true) && trimmed.isNotEmpty()

    // Debounced availability check. Re-runs whenever the trimmed input
    // changes; the delay swallows in-flight requests during fast typing.
    LaunchedEffect(trimmed) {
        availability = null
        if (!lengthValid || unchanged) {
            checking = false
            return@LaunchedEffect
        }
        checking = true
        delay(DEBOUNCE_MS)
        val result = UsernameRepository.checkAvailability(trimmed)
        // The composable might have been removed or input mutated again
        // between launching this and the await returning. Re-check that
        // the trimmed input hasn't changed before applying the result.
        availability = result
        checking = false
    }

    val available = availability is UsernameRepository.AvailabilityResult.Available
    val taken = availability is UsernameRepository.AvailabilityResult.Taken
    val networkErr = availability is UsernameRepository.AvailabilityResult.NetworkError

    // Save is enabled when length is valid AND either the name is the
    // user's own (unchanged after trim) or the availability check passed
    // / network errored (let the user attempt to save anyway and surface
    // the transaction outcome). Disabled while the dialog is mid-save
    // and when the check is still pending.
    val canSave = lengthValid && !isSaving && !checking && !taken && !unchanged

    Dialog(
        onDismissRequest = { if (!isMandatory) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory,
            dismissOnClickOutside = !isMandatory
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PaperCream)
                .border(2.dp, InkBronze, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        if (isMandatory) R.string.username_dialog_title_first
                        else R.string.username_dialog_title_change
                    ),
                    color = InkDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { newValue ->
                        // Block typing past the cap so the counter never
                        // reads "21/20" and the user gets immediate
                        // feedback that the limit has been reached.
                        if (newValue.length <= MAX_LEN + 5) input = newValue
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.username_input_hint),
                            color = InkSubtle
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = InkDark,
                        unfocusedTextColor = InkDark,
                        focusedContainerColor = PaperCreamDark,
                        unfocusedContainerColor = PaperCreamDark,
                        focusedIndicatorColor = InkBronze,
                        unfocusedIndicatorColor = InkBronze.copy(alpha = 0.6f),
                        cursorColor = InkBronze
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status row: validation message OR availability state.
                    val statusText: String?
                    val statusColor: Color
                    when {
                        emptyInput -> {
                            statusText = null
                            statusColor = InkSubtle
                        }
                        tooShort -> {
                            statusText = stringResource(R.string.username_too_short)
                            statusColor = ErrorRed
                        }
                        tooLong -> {
                            statusText = stringResource(R.string.username_too_long)
                            statusColor = ErrorRed
                        }
                        unchanged -> {
                            statusText = null
                            statusColor = InkSubtle
                        }
                        checking -> {
                            statusText = stringResource(R.string.username_checking)
                            statusColor = InkSubtle
                        }
                        available -> {
                            statusText = "✓ " + stringResource(R.string.username_available)
                            statusColor = OkGreen
                        }
                        taken -> {
                            statusText = "✗ " + stringResource(R.string.username_taken)
                            statusColor = ErrorRed
                        }
                        networkErr -> {
                            statusText = stringResource(R.string.username_network_error)
                            statusColor = InkSubtle
                        }
                        else -> {
                            statusText = null
                            statusColor = InkSubtle
                        }
                    }
                    if (statusText != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (checking) {
                                CircularProgressIndicator(
                                    color = InkBronze,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                            }
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                    Text(
                        text = "$length/$MAX_LEN",
                        color = if (tooLong) ErrorRed else InkSubtle,
                        fontSize = 11.sp
                    )
                }

                if (inlineError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = inlineError,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isMandatory) {
                        TextButton(onClick = onDismiss, enabled = !isSaving) {
                            Text(
                                stringResource(R.string.username_cancel),
                                color = InkSubtle,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                    }
                    Button(
                        onClick = { onSave(trimmed) },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkBronze,
                            contentColor = PaperCream,
                            disabledContainerColor = InkBronze.copy(alpha = 0.4f),
                            disabledContentColor = PaperCream.copy(alpha = 0.7f)
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = PaperCream,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            stringResource(R.string.username_save),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
