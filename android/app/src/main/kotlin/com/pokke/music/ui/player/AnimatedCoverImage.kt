package com.pokke.music.ui.player

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.pokke.music.data.model.Song

/** Uses the animated artwork URL when enabled; otherwise keeps the static album cover. */
@Composable
fun AnimatedCoverImage(
    song: Song,
    animatedEnabled: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }
    val imageUrl = if (animatedEnabled) song.animatedCoverUrl?.takeIf(String::isNotBlank) else null
    val model = remember(song.id, imageUrl, song.displayCoverUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl ?: song.displayCoverUrl)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = model,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
