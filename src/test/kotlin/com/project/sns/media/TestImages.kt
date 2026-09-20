package com.project.sns.media

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object TestImages {
    fun png(width: Int = 2, height: Int = 3): ByteArray = encode("png", width, height)

    fun jpeg(width: Int = 4, height: Int = 2): ByteArray = encode("jpg", width, height)

    fun gif(width: Int = 3, height: Int = 3): ByteArray = encode("gif", width, height)

    fun webpHeaderOnly(): ByteArray =
        "RIFF".toByteArray() + byteArrayOf(0x24, 0, 0, 0) + "WEBPVP8 ".toByteArray() + ByteArray(20)

    fun corruptPng(): ByteArray = byteArrayOf(
        0x89.toByte(),
        'P'.code.toByte(),
        'N'.code.toByte(),
        'G'.code.toByte(),
        0x0D,
        0x0A,
        0x1A,
        0x0A
    ) + ByteArray(32) { 0x42 }

    private fun encode(format: String, width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { check(ImageIO.write(image, format, it)) { "ImageIO 가 $format 을 못 쓴다" } }
            .toByteArray()
    }
}
