package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nova.runtime.app.ui.theme.NovaSurfaceVariant
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary

@Composable
fun CommandBar(
    onCommandSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var queryText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NovaSurfaceDark)
            .border(1.dp, NovaCyanAccent.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = queryText,
            onValueChange = { queryText = it },
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = NovaTextPrimary,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(NovaCyanAccent),
            decorationBox = { innerTextField ->
                if (queryText.isEmpty()) {
                    Text(
                        text = "Ask NOVA or execute AI command...",
                        color = NovaTextSecondary,
                        fontSize = 14.sp
                    )
                }
                innerTextField()
            }
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
                containerColor = NovaCyanAccent
            ),
            enabled = queryText.isNotBlank()
        ) {
            Text(
                text = "EXECUTE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NovaSurfaceDark
            )
        }
    }
}
