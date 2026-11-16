package com.kimicode.pocket

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import java.io.IOException

object GalleryQrDecoder {
    private const val MAX_IMAGE_EDGE = 2560

    fun decode(contentResolver: ContentResolver, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Cannot open selected image")
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unsupported image")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Cannot reopen selected image")
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IOException("Cannot decode selected image")

        return try {
            decodeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var size = 1
        while (width / (size * 2) >= 1 && height / (size * 2) >= 1 &&
            maxOf(width / (size * 2), height / (size * 2)) >= MAX_IMAGE_EDGE
        ) {
            size *= 2
        }
        return size
    }

    private fun decodeBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val images = listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source)),
            BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source)))
        )
        val reader = MultiFormatReader()
        for (image in images) {
            try {
                return reader.decode(image, hints).text
            } catch (_: ReaderException) {
                reader.reset()
            }
        }
        return null
    }
}
