package com.pokke.music.ui.shapes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

/** Fully rounded pill — drop-in for com.kyant.shapes.Capsule() */
object Capsule {
    operator fun invoke(): Shape = RoundedCornerShape(percent = 50)
}
