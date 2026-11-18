package com.pokke.music.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val animatedCoverUrl: String? = null,
    val audioUrl: String? = null,
    val durationSeconds: Int = 0,
    val lyricsText: String? = null,
    val lyricsUrl: String? = null,
    val genre: String? = null,
    val youtubeId: String? = null,
    val lyrics: List<LyricLine> = emptyList()
) {
    val hasAnimatedCover: Boolean get() = !animatedCoverUrl.isNullOrBlank()
    val durationMs: Long get() = durationSeconds * 1000L

    /**
     * Cover prioritario para UI.
     * 1. coverUrl (servidor / absoluto)
     * 2. animatedCoverUrl si es imagen estática
     * 3. thumbnail de YouTube
     *
     * Si todo falla, la UI debe llamar a CoverUrls.resolveBest en background
     * y cachear el resultado.
     */
    val displayCoverUrl: String?
        get() = coverUrl?.takeIf(String::isNotBlank)
            ?: animatedCoverUrl?.takeIf(::isStillArtworkImage)
            ?: youtubeId?.takeIf(String::isNotBlank)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    private fun isStillArtworkImage(url: String): Boolean {
        val path = android.net.Uri.parse(url).lastPathSegment.orEmpty().substringBefore('?').lowercase()
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".avif")
    }
}

data class LyricLine(val timeMs: Long, val text: String)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val bio: String? = null
)

data class Album(
    val name: String,
    val artist: String,
    val coverUrl: String? = null,
    val songCount: Int = 0
)

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val isPublic: Boolean = false,
    val songCount: Int = 0,
    val primaryColor: String = "#6750A4",
    val secondaryColor: String = "#26324A",
    val visualStyle: String = "gradient"
)

data class UserProfile(
    val id: String,
    val displayName: String? = null,
    val username: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bio: String? = null,
    val theme: String? = "default",
    val frame: String? = "none",
    val isPublic: Boolean = true,
    val isArtist: Boolean = false,
    val badge: String? = "none",
    val pronouns: String? = null,
    val statusText: String? = null,
    val accentColor: String? = "violet",
    val cardStyle: String? = "glass",
    val badges: String? = null,
    val linkLabel: String? = null,
    val linkUrl: String? = null,
    /** When true, other users can see what you're currently listening to on your profile. */
    val showListening: Boolean = true,
    /** Extra links as "label|url,label|url". Primary link remains linkLabel/linkUrl. */
    val extraLinks: String? = null,
    /**
     * Favorite mangas from MangaDex, stored as JSON array string:
     * [{"id":"...","title":"...","cover":"..."}]
     */
    val favoriteMangas: String? = null,
    val searchCardStyle: String? = "default",
    val searchCardColor: String? = "violet",
    val searchCardEffect: String? = "none",
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val gradientEnabled: Boolean = true,
    val profileGifs: String? = null,
    val aboutExtra: String? = null,
    val isOwner: Boolean = false,
    val searchCardBannerUrl: String? = null,
    val customBgUrl: String? = null,
    val nameplateId: String? = null,
    val nameplateImageUrl: String? = null,
    val nameplatePrimaryColor: String? = null,
    val nameplateSecondaryColor: String? = null,
    val nameplateStyle: String? = "aurora",
    val nameFont: String? = "sans",
    val nameColor: String? = "#F3F2FA",
    val profileCardOrder: String? = "listening,badges"
)
