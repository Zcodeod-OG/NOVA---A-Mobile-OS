package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaAkzidenzRed
import com.nova.runtime.app.ui.theme.NovaColors
import com.nova.runtime.app.ui.theme.NovaErrorRed
import com.nova.runtime.app.ui.theme.NovaPureBlack
import com.nova.runtime.app.ui.theme.NovaSecondaryGrey
import com.nova.runtime.app.ui.theme.NovaSurfaceContainerHighest
import com.nova.runtime.app.ui.theme.NovaSurfaceLowest

/** Reusable Swiss Objective Modernist Card: 2px solid border, 0px corner radius, 0px shadow. */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NovaPureBlack,
    borderWidth: Dp = 2.dp,
    backgroundColor: Color = NovaSurfaceLowest,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .border(borderWidth, borderColor, RoundedCornerShape(0.dp))
            .background(backgroundColor, RoundedCornerShape(0.dp))
            .padding(16.dp),
        content = content,
    )
}

enum class NovaButtonVariant {
    PRIMARY,
    PRIMARY_RED,
    SECONDARY,
    OUTLINE,
}

/** Reusable Swiss Button: rectangular 0px radius, heavy padding, bold uppercase typography. */
@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NovaButtonVariant = NovaButtonVariant.PRIMARY,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val bgColor = when (variant) {
        NovaButtonVariant.PRIMARY -> if (enabled) NovaPureBlack else NovaSecondaryGrey
        NovaButtonVariant.PRIMARY_RED -> if (enabled) NovaAkzidenzRed else NovaSecondaryGrey
        NovaButtonVariant.SECONDARY -> NovaSurfaceLowest
        NovaButtonVariant.OUTLINE -> NovaSurfaceLowest
    }
    val textColor = when (variant) {
        NovaButtonVariant.PRIMARY, NovaButtonVariant.PRIMARY_RED -> NovaSurfaceLowest
        NovaButtonVariant.SECONDARY, NovaButtonVariant.OUTLINE -> NovaPureBlack
    }
    val borderColor = when (variant) {
        NovaButtonVariant.PRIMARY -> NovaPureBlack
        NovaButtonVariant.PRIMARY_RED -> NovaAkzidenzRed
        NovaButtonVariant.SECONDARY, NovaButtonVariant.OUTLINE -> NovaPureBlack
    }

    Box(
        modifier = modifier
            .border(2.dp, borderColor, RoundedCornerShape(0.dp))
            .background(bgColor, RoundedCornerShape(0.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            if (leadingIcon != null) Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.sp,
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) Spacer(modifier = Modifier.width(8.dp))
            trailingIcon?.invoke()
        }
    }
}

/** Reusable Swiss Status Chip: 0px corner radius, bold uppercase status. */
@Composable
fun NovaStatusChip(
    status: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isError: Boolean = false,
    isSecondary: Boolean = false,
) {
    val bgColor = when {
        isPrimary -> NovaAkzidenzRed
        isError -> NovaErrorRed
        isSecondary -> NovaSurfaceContainerHighest
        else -> NovaPureBlack
    }
    val textColor = when {
        isSecondary -> NovaPureBlack
        else -> NovaSurfaceLowest
    }
    val borderColor = when {
        isSecondary -> NovaPureBlack
        else -> bgColor
    }

    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(0.dp))
            .background(bgColor, RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.05.sp,
            ),
            color = textColor,
        )
    }
}

/** Reusable Swiss Toggle Switch: 0px corner radius 2px border toggle. */
@Composable
fun NovaToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackBg = if (checked) NovaPureBlack else NovaSurfaceLowest
    val knobBg = if (checked) NovaSurfaceLowest else NovaPureBlack

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .border(2.dp, NovaPureBlack, RoundedCornerShape(0.dp))
            .background(trackBg, RoundedCornerShape(0.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(knobBg, RoundedCornerShape(0.dp)),
        )
    }
}

/** Reusable Swiss Slider with 2px track & rectangular thumb. */
@Composable
fun NovaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = NovaPureBlack,
            activeTrackColor = NovaPureBlack,
            inactiveTrackColor = NovaSurfaceContainerHighest,
        ),
        modifier = modifier,
    )
}
