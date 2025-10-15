package com.codecademy.comicreader.view.sources

import android.graphics.Bitmap

interface ComicPageSource {
    fun getPageCount(): Int
    fun getPageBitmap(index: Int): Bitmap?
}