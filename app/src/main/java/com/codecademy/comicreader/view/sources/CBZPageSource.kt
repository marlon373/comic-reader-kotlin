package com.codecademy.comicreader.view.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.util.Locale
import java.util.zip.ZipInputStream

class CBZPageSource(
    private val context: Context,
    private val uri: Uri
) : BitmapPageSource() {

    private val imageMap = mutableMapOf<Int, String>()

    init {
        preloadImageList()
    }

    private fun preloadImageList() {
        val imageNames = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    while (true) {
                        val currentEntry = zis.nextEntry ?: break  //  use local immutable variable
                        val name = currentEntry.name.lowercase(Locale.getDefault())
                        if (!currentEntry.isDirectory &&
                            name.matches(Regex(".*\\.(jpg|jpeg|png|webp)$"))
                        ) {
                            imageNames.add(currentEntry.name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CBZPageSource", "Error indexing zip", e)
        }

        imageNames.sortWith(String.CASE_INSENSITIVE_ORDER)
        imageNames.forEachIndexed { i, name -> imageMap[i] = name }
    }

    override fun getPageCount(): Int = imageMap.size

    @Synchronized
    override fun getPageBitmap(index: Int): Bitmap? {
        getCached(index)?.let { return it }
        val target = imageMap[index] ?: return createCorruptPlaceholder("Missing page $index")

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    while (true) {
                        val currentEntry = zis.nextEntry ?: break  //  again: local immutable
                        if (currentEntry.name == target) {
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            val bmp = BitmapFactory.decodeStream(zis, null, options)
                            if (bmp != null) {
                                cache(index, bmp)
                                return bmp
                            }
                            return createCorruptPlaceholder("Corrupt page $index")
                        }
                    }
                }
            }
            createCorruptPlaceholder("Not found $index")
        } catch (e: Exception) {
            Log.e("CBZPageSource", "Error reading image: $target", e)
            createCorruptPlaceholder("Error page $index")
        }
    }
}
