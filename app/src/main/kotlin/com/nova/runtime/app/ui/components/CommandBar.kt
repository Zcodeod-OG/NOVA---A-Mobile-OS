package com.nova.runtime.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nova.runtime.app.ui.theme.NovaColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop

@Composable
fun CommandBar(
    onCommandSubmit: (String) -> Unit,
    onMicToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRecording: Boolean = false,
    voiceStatusMessage: String? = null,
) {
    val context = LocalContext.current
    var queryText by remember { mutableStateOf("") }

    fun submit() {
        val trimmed = queryText.trim()
        if (trimmed.isNotBlank() && enabled && !isRecording) {
            onCommandSubmit(trimmed)
            queryText = ""
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    ambientColor = NovaColors.accent.copy(alpha = 0.08f),
                    spotColor = NovaColors.accent.copy(alpha = 0.12f),
                )
                .clip(MaterialTheme.shapes.extraLarge)
                .background(NovaColors.glassSurface)
                .border(1.dp, NovaColors.glassBorder, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onMicToggle,
                    enabled = enabled,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) {
                                NovaColors.error.copy(alpha = 0.15f)
                            } else {
                                NovaColors.surfaceElevated.copy(alpha = 0.6f)
                            },
                        ),
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Start voice input",
                        tint = if (isRecording) NovaColors.error else NovaColors.accent,
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                BasicTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    enabled = enabled && !isRecording,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = NovaColors.textPrimary),
                    cursorBrush = SolidColor(NovaColors.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    decorationBox = { innerTextField ->
                        if (queryText.isEmpty()) {
                            Text(
                                text = if (isRecording) {
                                    "Listening…"
                                } else {
                                    "Ask NOVA anything"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = NovaColors.textSecondary,
                            )
                        }
                        innerTextField()
                    },
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = ::submit,
                    enabled = queryText.isNotBlank() && enabled && !isRecording,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (queryText.isNotBlank() && enabled && !isRecording) {
                                NovaColors.accent
                            } else {
                                NovaColors.surfaceElevated.copy(alpha = 0.4f)
                            },
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (queryText.isNotBlank() && enabled && !isRecording) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            NovaColors.textSecondary
                        },
                    )
                }
            }
        }

        voiceStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = NovaColors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
    }
}
