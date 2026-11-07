package com.example.ratecalc

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Text whose digits roll up and down like an old mechanical meter when the value changes.
 *
 * Only the real digits are shown (৳9021.00 stays ৳9021.00, no leading zeros). Digits are counted
 * from the right, so the units wheel always keeps the same wheel. Every wheel starts at 0 when it
 * first appears and rolls to its digit: all wheels roll up from 0 when the app opens, and a new
 * wheel rolls in from 0 when the value grows (9999 -> 10000, 99999 -> 100000 = 1 lakh).
 */
@Composable
fun OdometerText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val density = LocalDensity.current
    val slotHeight: Dp = with(density) { fontSize.toDp() } * 1.3f

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val n = text.length
        text.forEachIndexed { i, ch ->
            key(n - i) {
                if (ch in '0'..'9') {
                    RollingDigit(ch - '0', fontSize, color, fontWeight, slotHeight)
                } else {
                    Box(Modifier.height(slotHeight), contentAlignment = Alignment.Center) {
                        Text(ch.toString(), fontSize = fontSize, fontWeight = fontWeight, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun RollingDigit(
    digit: Int,
    fontSize: TextUnit,
    color: Color,
    weight: FontWeight,
    slotHeight: Dp
) {
    var shown by remember { mutableIntStateOf(0) } // every wheel starts at 0 and rolls up
    LaunchedEffect(digit) { shown = digit }

    val y by animateDpAsState(
        targetValue = -(slotHeight * shown),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "odometerDigit"
    )
    Box(Modifier.height(slotHeight).clipToBounds()) {
        Column(Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true).offset(y = y)) {
            for (d in 0..9) {
                Box(Modifier.height(slotHeight), contentAlignment = Alignment.Center) {
                    Text(d.toString(), fontSize = fontSize, fontWeight = weight, color = color)
                }
            }
        }
    }
}
