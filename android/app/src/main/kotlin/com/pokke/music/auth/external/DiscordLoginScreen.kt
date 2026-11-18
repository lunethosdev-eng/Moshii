package com.pokke.music.auth.external

/**
 * Discord no longer uses the legacy WebView/localStorage token-capture flow.
 * The supported integration is the server-side OAuth2 flow exposed by
 * AuthRepository.startDiscordOAuth() and launched from ExternalAccountsSection.
 * Keeping this source file as a package marker avoids stale imports in downstream
 * branches while ensuring no private Discord session token is harvested.
 */
