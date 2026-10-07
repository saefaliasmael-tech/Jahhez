package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorageHelper {
    private const val IMAGES_DIR = "product_images"

    /**
     * Copies an image from a content URI to the app's internal files directory.
     * Returns the permanent file URI string.
     */
    fun saveImageLocally(context: Context, sourceUri: Uri): String? {
        return try {
            val directory = File(context.filesDir, IMAGES_DIR)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = "prod_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(directory, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return null

            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes an internal product image file if it exists inside the app's internal images folder.
     */
    fun deleteInternalImage(context: Context, imageUriString: String?) {
        if (imageUriString.isNullOrBlank()) return
        try {
            val uri = Uri.parse(imageUriString)
            val path = uri.path ?: return
            val file = File(path)
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            if (file.exists() && file.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
