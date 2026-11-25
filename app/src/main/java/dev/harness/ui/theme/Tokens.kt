package dev.harness.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale — multiples of 4 dp only. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radius scale + prebuilt shapes. */
object Radii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 100.dp

    val shapeSm: Shape = RoundedCornerShape(sm)
    val shapeMd: Shape = RoundedCornerShape(md)
    val shapeLg: Shape = RoundedCornerShape(lg)
    val shapeXl: Shape = RoundedCornerShape(xl)
    val shapePill: Shape = RoundedCornerShape(pill)
}

/** Elevation scale (dp) used by surfaces, bars and dialogs. */
object Elev {
    val flat = 0.dp
    val low = 1.dp
    val mid = 3.dp
    val high = 6.dp
    val sheet = 16.dp
}

/**
 * Motion tokens: durations and easing curves. All animations in the app go
 * through these so "remove animations" (animator duration scale = 0) is
 * respected automatically by Compose.
 */
@Immutable
data class MotionSpec(
    val fastMs: Int = 120,
    val normalMs: Int = 200,
    val slowMs: Int = 300,
)

object MotionDefaults {
    val Standard = MotionSpec()
}

val LocalMotion = staticCompositionLocalOf { MotionDefaults.Standard }

/** Standard elevation shadow used by floating elements (jump-to-bottom, snackbar). */
object ShadowTokens {
    val color = androidx.compose.ui.graphics.Color(0x40000000)
}

/** Minimum interactive target size (Material/a11y). */
val MinTouchTarget: Dp = 48.dp
