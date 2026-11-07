package com.example.ratecalc

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
 * The whole-number part is padded to [minIntDigits] wheels (6 = up to 9,99,999, so it rolls
 * through 1 lakh = 1,00,000). Unused leading wheels stay visible but dim, like an unlit meter,
 * and light up as the value grows. Digits are counted from the right, so the units wheel
 * always keeps the same wheel while the total changes.
 */
@Composable
fun OdometerText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    minIntDigits: Int = 6
) {
    val density = LocalDensity.current
    val slotHeight: Dp = with(density) { fontSize.toDp() } * 1.3f

    // Split "৳12345.67" into prefix / whole digits / rest and pad the whole digits.
    val m = Regex("^(\\D*)(\\d+)(.*)$").find(text)
    val shown: String
    var dimCount = 0
    var firstDigit = 0
    if (m != null) {
        val prefix = m.groupValues[1]
        val whole = m.groupValues[2].padStart(minIntDigits, '0')
        shown = prefix + whole + m.groupValues[3]
        firstDigit = prefix.length
        while (dimCount < whole.length - 1 && whole[dimCount] == '0') dimCount++
    } else {
        shown = text
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val n = shown.length
        shown.forEachIndexed { i, ch ->
            key(n - i) {
                val dim = m != null && i >= firstDigit && i < firstDigit + dimCount
                val c by animateColorAsState(
                    targetValue = if (dim) color.copy(alpha = 0.22f) else color,
                    label = "odometerColor"
                )
                if (ch in '0'..'9') {
                    RollingDigit(ch - '0', fontSize, c, fontWeight, slotHeight)
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
private fun RollingDigit(digit: Int, fontSize: TextUnit, color: Color, weight: FontWeight, slotHeight: Dp) {
    val y by animateDpAsState(
        targetValue = -(slotHeight * digit),
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
