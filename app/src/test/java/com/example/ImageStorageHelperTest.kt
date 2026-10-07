package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.ImageStorageHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class ImageStorageHelperTest {

    @Test
    fun testSaveAndCleanImageLocally() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Create a dummy source file
        val tempFile = File(context.cacheDir, "sample_picker.jpg")
        FileOutputStream(tempFile).use {
            it.write("fake_image_bytes".toByteArray())
        }
        val sourceUri = Uri.fromFile(tempFile)

        // Save locally using helper
        val savedUriString = ImageStorageHelper.saveImageLocally(context, sourceUri)
        assertNotNull(savedUriString)
        val savedFile = File(Uri.parse(savedUriString!!).path!!)
        assertTrue(savedFile.exists())
        assertEquals("fake_image_bytes", savedFile.readText())

        // Delete internal image using helper
        ImageStorageHelper.deleteInternalImage(context, savedUriString)
        assertFalse(savedFile.exists())
    }

    private fun assertEquals(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
