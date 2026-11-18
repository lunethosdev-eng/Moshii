package com.pokke.music.data

object TrackLog {
    fun d(tag: String, msg: String, about: Any? = null) =
        android.util.Log.d(tag, if (about != null) "$msg | $about" else msg)
    fun w(tag: String, msg: String, about: Any? = null) =
        android.util.Log.w(tag, if (about != null) "$msg | $about" else msg)
    fun e(tag: String, msg: String, t: Throwable? = null) =
        if (t != null) android.util.Log.e(tag, msg, t) else android.util.Log.e(tag, msg)
}

/** Alias used by BitChord-ported sources. */
typealias DebugLog = TrackLog
