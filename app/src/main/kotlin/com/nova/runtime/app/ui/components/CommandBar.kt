package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary

@Composable
fun CommandBar(
    onCommandSubmit: (String) -> Unit,
    onMicToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRecording: Boolean = false,
    voiceStatusMessage: String? = null,
) {
    var queryText by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(NovaSurfaceDark)
                .border(1.dp, NovaCyanAccent.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onMicToggle,
                shape = CircleShape,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) {
                        NovaCyanAccent
                    } else {
                        NovaSurfaceDark
                    },
                    contentColor = if (isRecording) NovaSurfaceDark else NovaCyanAccent,
                    disabledContainerColor = NovaSurfaceDark.copy(alpha = 0.5f),
                    disabledContentColor = NovaTextSecondary,
                ),
            ) {
                Text(
                    text = if (isRecording) "REC" else "MIC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = queryText,
                onValueChange = { queryText = it },
                modifier = Modifier.weight(1f),
                enabled = enabled && !isRecording,
                textStyle = TextStyle(
                    color = NovaTextPrimary,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(NovaCyanAccent),
                decorationBox = { innerTextField ->
                    if (queryText.isEmpty()) {
                        Text(
                            text = if (isRecording) {
                                "Recording voice command…"
                            } else {
                                "Ask NOVA or execute AI command…"
                            },
                            color = NovaTextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (queryText.isNotBlank()) {
                        onCommandSubmit(queryText)
                        queryText = ""
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaCyanAccent,
                ),
                enabled = queryText.isNotBlank() && enabled && !isRecording,
            ) {
                Text(
                    text = "EXECUTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NovaSurfaceDark,
                )
            }
        }

        voiceStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = NovaTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
    }
}
