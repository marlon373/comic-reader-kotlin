package com.codecademy.comicreader.view.sources

import android.graphics.Bitmap
import kotlinx.coroutines.Job

/**
 * ComicPageSource - defines interface for page sources (PDF/CBZ/CBR/etc.)
 */
interface ComicPageSource {

    // Returns the total number of pages in the source.
    fun getPageCount(): Int
    // Synchronously returns the Bitmap for a given page index.
    fun getPageBitmap(index: Int): Bitmap?

    /**
     * Asynchronously loads a page Bitmap.
     * @param index Page index
     * @param callback Called with the loaded Bitmap (can be null if failed)
     * @return Job representing the async task, can be cancelled
     */
    fun loadPageAsync(index: Int, callback: (Bitmap?) -> Unit): Job

    /**
     * Cancels loading of a specific page. Optional implementation.
     * @param index Page index to cancel
     */
    fun cancelLoad(index: Int) {}

    // Closes the page source, releasing resources. Optional implementation.
    fun closeSource() {}
}