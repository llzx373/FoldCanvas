package com.llzx373.foldcanvas

import com.llzx373.foldcanvas.data.MediaPermissions
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MediaPermissionsTest {

    @Test
    fun `api 33+ uses granular media permissions`() {
        assertArrayEquals(
            arrayOf(MediaPermissions.READ_MEDIA_IMAGES, MediaPermissions.READ_MEDIA_VIDEO),
            MediaPermissions.required(33),
        )
        assertArrayEquals(
            arrayOf(MediaPermissions.READ_MEDIA_IMAGES, MediaPermissions.READ_MEDIA_VIDEO),
            MediaPermissions.required(35),
        )
    }

    @Test
    fun `api 32 and below uses read external storage`() {
        assertArrayEquals(
            arrayOf(MediaPermissions.READ_EXTERNAL_STORAGE),
            MediaPermissions.required(32),
        )
        assertArrayEquals(
            arrayOf(MediaPermissions.READ_EXTERNAL_STORAGE),
            MediaPermissions.required(26),
        )
    }
}
