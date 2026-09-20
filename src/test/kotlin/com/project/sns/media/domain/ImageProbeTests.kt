package com.project.sns.media.domain

import com.project.sns.media.TestImages
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageProbeTests {
    @Test
    fun `jpeg png gif 는 형식과 픽셀 크기를 읽는다`() {
        assertEquals(ImageInfo("image/png", 2, 3), ImageProbe.probe(TestImages.png(2, 3).inputStream()))
        assertEquals(ImageInfo("image/jpeg", 4, 2), ImageProbe.probe(TestImages.jpeg(4, 2).inputStream()))
        assertEquals(ImageInfo("image/gif", 3, 3), ImageProbe.probe(TestImages.gif(3, 3).inputStream()))
    }

    @Test
    fun `webp 는 형식만 판정하고 크기는 비운다`() {
        val info = assertNotNull(ImageProbe.probe(TestImages.webpHeaderOnly().inputStream()))
        assertEquals("image/webp", info.contentType)
        assertNull(info.width)
        assertNull(info.height)
    }

    @Test
    fun `이미지가 아니거나 매직 넘버만 맞는 깨진 파일은 null 이다`() {
        assertNull(ImageProbe.probe("이건 텍스트".toByteArray().inputStream()))
        assertNull(ImageProbe.probe(ByteArray(0).inputStream()))
        assertNull(ImageProbe.probe(TestImages.corruptPng().inputStream()))
    }

    @Test
    fun `헤더만 읽고 스트림 전체를 소비하지 않는다`() {
        val bytes = TestImages.png(64, 64)
        val stream = bytes.inputStream()
        ImageProbe.probe(stream)
        check(stream.available() > 0)
    }
}
