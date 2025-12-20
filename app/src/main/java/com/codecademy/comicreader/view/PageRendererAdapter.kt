package com.codecademy.comicreader.view

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import java.lang.ref.WeakReference
import com.codecademy.comicreader.view.sources.BitmapPageSource
import com.codecademy.comicreader.view.sources.ComicPageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Adapter for rendering pages from a ComicPageSource or BitmapPageSource
 */
class PageRendererAdapter(
    private val pageSource: ComicPageSource,
    renderDispatcher: CoroutineDispatcher
) : RecyclerView.Adapter<PageRendererAdapter.PageViewHolder>() {

    private val renderScope = CoroutineScope(SupervisorJob() + renderDispatcher)

    private val holderRefs = mutableMapOf<Int, WeakReference<PageViewHolder>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                MATCH_PARENT,
                MATCH_PARENT
            )
        }
        return PageViewHolder(container)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val container = holder.itemView as FrameLayout
        container.removeAllViews()
        container.addView(holder.photoView)
        container.addView(holder.progressBar)

        holder.progressBar.visibility = View.VISIBLE
        holder.photoView.setImageBitmap(null)

        holderRefs[position] = WeakReference(holder)

        // Cancel previous decode
        pageSource.cancelLoad(position)

        // Async mode (fast + avoids flicker)
        if (pageSource is BitmapPageSource) {
            pageSource.loadPageAsync(position) { bmp ->
                val ref = holderRefs[position]?.get() ?: return@loadPageAsync
                if (ref.bindingAdapterPosition != position) return@loadPageAsync

                ref.progressBar.visibility = View.GONE
                ref.photoView.setImageBitmap(bmp)
            }
            return
        }

        // Sync mode (fallback)
        renderScope.launch {
            val bmp = try { pageSource.getPageBitmap(position) }
            catch (_: Exception) { null }

            withContext(Dispatchers.Main) {
                if (holder.bindingAdapterPosition != position) return@withContext
                holder.progressBar.visibility = View.GONE
                holder.photoView.setImageBitmap(bmp)
            }
        }
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            holderRefs.remove(pos)
            pageSource.cancelLoad(pos)
        }

        holder.photoView.setImageDrawable(null)
        holder.photoView.setScale(1f, false)

        super.onViewRecycled(holder)
    }

    fun resetZoomAt(position: Int) {
        holderRefs[position]?.get()?.photoView?.setScale(1f, true)
    }

    fun shutdown() {
        renderScope.cancel()
        holderRefs.clear()
        pageSource.closeSource()
    }

    override fun getItemCount(): Int = pageSource.getPageCount()

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photoView: PhotoView
        val progressBar: ProgressBar

        init {
            val container = itemView as FrameLayout

            photoView = PhotoView(container.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    MATCH_PARENT,
                    MATCH_PARENT
                )
            }

            progressBar = ProgressBar(container.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    WRAP_CONTENT,
                    WRAP_CONTENT,
                    Gravity.CENTER
                )
            }

            container.addView(photoView)
            container.addView(progressBar)
        }
    }
}





