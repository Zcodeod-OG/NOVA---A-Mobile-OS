package com.nova.runtime.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaIndigoAccent
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaSurfaceVariant
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary
import java.util.Locale

@Composable
fun CommandBar(
    onCommandSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var queryText by remember { mutableStateOf("") }

    val handleSubmit = {
        if (queryText.isNotBlank()) {
            onCommandSubmit(queryText.trim())
            queryText = ""
        }
    }

    // Android Voice Recognition Speech-to-Text Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                queryText = spokenText
                onCommandSubmit(spokenText)
                queryText = ""
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Voice Input Mic Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(NovaIndigoAccent.copy(alpha = 0.8f))
                .clickable {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command to NOVA...")
                        }
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Voice Recognition not available on device", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎤",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = "Ask NOVA or speak command...",
                    color = NovaTextSecondary,
                    fontSize = 13.sp
                )
            },
            singleLine = true,
            textStyle = TextStyle(
                color = NovaTextPrimary,
                fontSize = 14.sp
            ),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NovaSurfaceDark,
                unfocusedContainerColor = NovaSurfaceDark,
                focusedBorderColor = NovaCyanAccent,
                unfocusedBorderColor = NovaSurfaceVariant,
                focusedTextColor = NovaTextPrimary,
                unfocusedTextColor = NovaTextPrimary,
                cursorColor = NovaCyanAccent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { handleSubmit() })
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = { handleSubmit() },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = NovaCyanAccent,
                disabledContainerColor = NovaSurfaceVariant
            ),
            enabled = queryText.isNotBlank()
        ) {
            Text(
                text = "EXECUTE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (queryText.isNotBlank()) NovaSurfaceDark else NovaTextSecondary
            )
        }
    }
}
