package com.codecademy.comicreader.view

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext


class PageRendererAdapter(
    private val context: Context,
    private val pageSource: ComicPageSource,
    private val ioDispatcher: CoroutineDispatcher
) : RecyclerView.Adapter<PageRendererAdapter.PageViewHolder>(), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = ioDispatcher + job

    private val holderRefs: MutableMap<Int, WeakReference<PageViewHolder>> = HashMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return PageViewHolder(container)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val parent = holder.itemView as FrameLayout
        parent.removeAllViews()
        parent.addView(holder.photoView)
        parent.addView(holder.progressBar)

        holder.progressBar.visibility = View.VISIBLE
        holder.photoView.setImageBitmap(null)

        val bindingPosition = position
        holderRefs[bindingPosition] = WeakReference(holder)

        // Coroutine-based page loading
        launch {
            try {
                val bmp = withContext(ioDispatcher) {
                    pageSource.getPageBitmap(bindingPosition)
                }

                withContext(Dispatchers.Main) {
                    // Skip if holder is reused
                    if (holder.bindingAdapterPosition != bindingPosition) return@withContext
                    holder.progressBar.visibility = View.GONE
                    holder.photoView.setImageBitmap(bmp)
                }

            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    holder.progressBar.visibility = View.GONE
                    holder.photoView.setImageDrawable(null)
                }
            }
        }
    }

    override fun getItemCount(): Int = pageSource.getPageCount()

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            holderRefs.remove(position)
        }
        holder.photoView.setImageDrawable(null)
    }

    fun resetZoomAt(position: Int) {
        holderRefs[position]?.get()?.photoView?.setScale(1f, true)
    }

    fun shutdown() {
        job.cancel() // Cancels coroutines
        if (pageSource is BitmapPageSource) {
            pageSource.clear()
        }
    }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photoView: PhotoView
        val progressBar: ProgressBar

        init {
            val container = itemView as FrameLayout

            photoView = PhotoView(container.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            progressBar = ProgressBar(container.context).apply {
                val pbParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                pbParams.gravity = Gravity.CENTER
                layoutParams = pbParams
                isIndeterminate = true
            }

            container.addView(photoView)
            container.addView(progressBar)
        }
    }
}



