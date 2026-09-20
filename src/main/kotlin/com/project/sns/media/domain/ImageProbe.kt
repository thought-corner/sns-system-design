package com.project.sns.media.domain

import java.io.InputStream
import javax.imageio.ImageIO

data class ImageInfo(
    val contentType: String,
    val width: Int?,
    val height: Int?,
)

object ImageProbe {
    private const val HEADER_BYTES = 16

    fun probe(input: InputStream): ImageInfo? {
        val buffered = if (input.markSupported()) input else input.buffered()
        buffered.mark(HEADER_BYTES)
        val header = ByteArray(HEADER_BYTES)
        val read = buffered.readNBytes(header, 0, HEADER_BYTES)
        buffered.reset()
        val contentType = detect(header, read) ?: return null
        return when (val dimensions = readDimensions(buffered)) {
            Dimensions.NoReader -> ImageInfo(contentType = contentType, width = null, height = null)
            Dimensions.Corrupt -> null
            is Dimensions.Known -> ImageInfo(
                contentType = contentType,
                width = dimensions.width,
                height = dimensions.height
            )
        }
    }

    private sealed interface Dimensions {
        data object NoReader : Dimensions

        data object Corrupt : Dimensions

        data class Known(val width: Int, val height: Int) : Dimensions
    }

    private fun detect(h: ByteArray, n: Int): String? = when {
        n >= 3 && h[0] == 0xFF.b && h[1] == 0xD8.b && h[2] == 0xFF.b -> "image/jpeg"
        n >= 8 && h[0] == 0x89.b && h[1] == 'P'.b && h[2] == 'N'.b && h[3] == 'G'.b &&
                h[4] == 0x0D.b && h[5] == 0x0A.b && h[6] == 0x1A.b && h[7] == 0x0A.b -> "image/png"

        n >= 6 && h[0] == 'G'.b && h[1] == 'I'.b && h[2] == 'F'.b && h[3] == '8'.b &&
                (h[4] == '7'.b || h[4] == '9'.b) && h[5] == 'a'.b -> "image/gif"

        n >= 12 && h[0] == 'R'.b && h[1] == 'I'.b && h[2] == 'F'.b && h[3] == 'F'.b &&
                h[8] == 'W'.b && h[9] == 'E'.b && h[10] == 'B'.b && h[11] == 'P'.b -> "image/webp"

        else -> null
    }

    private fun readDimensions(input: InputStream): Dimensions {
        val imageInput = ImageIO.createImageInputStream(input) ?: return Dimensions.NoReader
        imageInput.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return Dimensions.NoReader
            val reader = readers.next()
            try {
                reader.setInput(stream, true, true)
                return Dimensions.Known(reader.getWidth(0), reader.getHeight(0))
            } catch (e: Exception) {
                return Dimensions.Corrupt
            } finally {
                reader.dispose()
            }
        }
    }

    private val Int.b: Byte get() = toByte()
    private val Char.b: Byte get() = code.toByte()
}
