package com.pokke.music.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Generates a standards-compliant QR code that camera apps can scan. */
object QrBitmap {
    fun encode(payload: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (y in 0 until sizePx) for (x in 0 until sizePx) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }

    fun pairPayload(pairingId: String, code: String, pin: String, secret: String): String =
            "pokke://pair?id=$pairingId&code=${code.trim()}&pin=${pin.trim()}&secret=${secret.trim()}"
}
