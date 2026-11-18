package com.pokke.music.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.R

/**
 * Built-in avatar frames (transparent PNGs in res/drawable-nodpi).
 * Stored in profiles.frame as "none" | "frame_01" … "frame_26".
 * Named decorative frames can be re-enabled once their webp assets are committed.
 */
object AvatarFrames {
    data class Frame(val id: String, val label: String, @DrawableRes val resId: Int?)

    val ALL: List<Frame> = buildList {
        add(Frame("none", "Ninguno", null))
        val numeric = listOf(
            R.drawable.frame_01, R.drawable.frame_02, R.drawable.frame_03, R.drawable.frame_04,
            R.drawable.frame_05, R.drawable.frame_06, R.drawable.frame_07, R.drawable.frame_08,
            R.drawable.frame_09, R.drawable.frame_10, R.drawable.frame_11, R.drawable.frame_12,
            R.drawable.frame_13, R.drawable.frame_14, R.drawable.frame_15, R.drawable.frame_16,
            R.drawable.frame_17, R.drawable.frame_18, R.drawable.frame_19, R.drawable.frame_20,
            R.drawable.frame_21, R.drawable.frame_22, R.drawable.frame_23, R.drawable.frame_24,
            R.drawable.frame_25, R.drawable.frame_26
        )
        numeric.forEachIndexed { i, res ->
            add(Frame("frame_%02d".format(i + 1), "Marco ${i + 1}", res))
        }
    }

    fun byId(id: String?): Frame = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

@Composable
fun FramedAvatar(
    avatarUrl: String?,
    displayName: String,
    frameId: String?,
    size: Dp = 96.dp,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val frame = AvatarFrames.byId(frameId)
    val hasFrame = frame.resId != null
    val photoScale = if (hasFrame) 0.78f else 1f
    val photoSize = size * photoScale

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(photoSize)
                .clip(CircleShape)
                .background(Color(0xFF2A2F42))
                .then(
                    if (!hasFrame && borderWidth > 0.dp)
                        Modifier.border(borderWidth, borderColor, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = (displayName.firstOrNull() ?: 'P').uppercaseChar().toString(),
                    color = Color.White,
                    fontSize = (size.value * 0.32f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (hasFrame && frame.resId != null) {
            Image(
                painter = painterResource(frame.resId),
                contentDescription = frame.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
