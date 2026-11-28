package com.example

import com.example.core.ui.AvatarImageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarHelperTest {

    @Test
    fun `calculateSampleSize scales large images cleanly as powers of two`() {
        assertEquals(1, AvatarImageHelper.calculateSampleSize(400, 400, 512))
        assertEquals(1, AvatarImageHelper.calculateSampleSize(512, 512, 512))
        assertEquals(2, AvatarImageHelper.calculateSampleSize(1024, 1024, 512))
        assertEquals(4, AvatarImageHelper.calculateSampleSize(2048, 2048, 512))
        assertEquals(8, AvatarImageHelper.calculateSampleSize(4096, 4096, 512))
    }

    @Test
    fun `isDataImageUri validates base64 image data URIs strictly`() {
        assertTrue(AvatarImageHelper.isDataImageUri("data:image/jpeg;base64,/9j/4AAQ"))
        assertTrue(AvatarImageHelper.isDataImageUri("  DATA:IMAGE/PNG;base64,iVBORw0K "))
        assertFalse(AvatarImageHelper.isDataImageUri("https://res.cloudinary.com/demo/image/upload/avatar.jpg"))
        assertFalse(AvatarImageHelper.isDataImageUri("file:///data/user/0/avatar.jpg"))
        assertFalse(AvatarImageHelper.isDataImageUri(null))
        assertFalse(AvatarImageHelper.isDataImageUri(""))
    }
}
