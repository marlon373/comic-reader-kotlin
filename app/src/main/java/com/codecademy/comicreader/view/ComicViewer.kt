package com.codecademy.comicreader.view

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.viewpager2.widget.ViewPager2
import com.codecademy.comicreader.R
import com.codecademy.comicreader.databinding.ComicViewerBinding
import com.codecademy.comicreader.dialog.InfoDialog.Companion.newInstance
import com.codecademy.comicreader.dialog.SelectPageDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.slider.Slider
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.codecademy.comicreader.theme.ThemeManager
import com.codecademy.comicreader.utils.SystemUtil
import com.codecademy.comicreader.view.sources.BitmapPageSource
import com.codecademy.comicreader.view.sources.CBRPageSource
import com.codecademy.comicreader.view.sources.CBZPageSource
import com.codecademy.comicreader.view.sources.ComicPageSource
import com.codecademy.comicreader.view.sources.PDFPageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.math.log10
import kotlin.math.pow


class ComicViewer : AppCompatActivity(), CoroutineScope {
    private lateinit var binding: ComicViewerBinding
    private lateinit var viewPager: ViewPager2
    private var adapter: PageRendererAdapter? = null
    private var pageSource: ComicPageSource? = null
    private var comicPath: String? = null
    private lateinit var slider: Slider
    private lateinit var comicViewProgress: View
    private lateinit var tvPageNumber: TextView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job

    private val ioDispatcher by lazy { SystemUtil.createSmartDispatcher(applicationContext) }


    companion object {
        private const val PREFS_NAME = "comicPrefs"
        private const val KEY_THEME = "isNightMode"
        private const val SCROLL_TYPE = "isScrolling"
        private const val KEY_LAST_PAGE = "last_page_"
        private const val PRELOAD_PAGE_COUNT = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.applyTheme(this)

        binding = ComicViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewPager = findViewById(R.id.viewPager_comic)
        slider = findViewById(R.id.slider_page_scroll)
        comicViewProgress = findViewById(R.id.progress_overlay)
        tvPageNumber = findViewById(R.id.tv_num_page)

        // Bottom sheet
        val bottomSheet = findViewById<View>(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 125
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // Toggle sheet visibility on tap
        findViewById<View>(R.id.container).setOnClickListener {
            val currentState = bottomSheetBehavior.state
            bottomSheetBehavior.state =
                if (currentState == BottomSheetBehavior.STATE_EXPANDED || currentState == BottomSheetBehavior.STATE_COLLAPSED) {
                    BottomSheetBehavior.STATE_HIDDEN
                } else {
                    BottomSheetBehavior.STATE_COLLAPSED
                }
        }

        setupControls()
        updateScrollTypeIcon()
        updateScrollTypeOrientation()

        comicPath = intent.getStringExtra("comicPath")
        comicPath?.let { path ->
            val uri = path.toUri()
            val ext = path.substringAfterLast('.').lowercase()

            when (ext) {
                "cbz" -> loadCBZLazy(uri)
                "cbr" -> loadCBRLazy(uri)
                "pdf" -> loadPDFLazy(uri)
                else -> showToast("Unsupported file type: $ext")
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        binding.imgbtnFirstPage.setOnClickListener { viewPager.currentItem = 0 }
        binding.imgbtnLastPage.setOnClickListener { viewPager.currentItem = pageSource!!.getPageCount() - 1 }
        binding.imgbtnNextPage.setOnClickListener { viewPager.currentItem = viewPager.currentItem + 1 }
        binding.imgbtnBackPage.setOnClickListener { viewPager.currentItem = viewPager.currentItem - 1 }

        binding.imgbtnInfo.setOnClickListener {
            comicPath?.let { path -> showInfoDialog(path.toUri()) }
        }

        binding.imgbtnDarkMode.setOnClickListener { toggleDayNightMode() }
        updateDayNightIcon()

        binding.imgbtnScrollType.setOnClickListener {
            val pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val isScrolling = pref.getBoolean(SCROLL_TYPE, false)

            // Toggle and save
            pref.edit { putBoolean(SCROLL_TYPE, !isScrolling) }

            // Update icon and scroll direction
            updateScrollTypeOrientation()
            updateScrollTypeIcon()
        }

        binding.imgbtnSelectPage.setOnClickListener { showSelectPageDialog() }

        binding.viewPagerComic.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                Log.d("ComicViewer", "Switched to page: $position")
                tvPageNumber.text = getString(R.string.pages, position + 1, pageSource?.getPageCount() ?: 0)
                slider.value = position.toFloat()
                adapter?.resetZoomAt(position)

                // Save current page
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit { putInt(KEY_LAST_PAGE, position) }
            }
        })

        slider.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val touchX = event.x

                // Calculate thumb X position manually
                val sliderWidth = slider.width - slider.paddingLeft - slider.paddingRight
                val valueFraction = (slider.value - slider.valueFrom) / (slider.valueTo - slider.valueFrom)
                val thumbX = slider.paddingLeft + valueFraction * sliderWidth

                val tolerance = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 24f, view.resources.displayMetrics
                )

                if (kotlin.math.abs(touchX - thumbX) > tolerance) return@setOnTouchListener true //  Block tap
            }
            if (event.action == MotionEvent.ACTION_UP) view.performClick() // Required for accessibility
            false // Allow drag behavior
        }

        binding.sliderPageScroll.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewPager.setCurrentItem(value.toInt(), true)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val volumeScroll = prefs.getBoolean("scroll_with_volume", false)

            if (volumeScroll) {
                val currentPage = viewPager.currentItem
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (currentPage > 0) viewPager.currentItem = currentPage - 1
                        return true // Consume event
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (pageSource != null && currentPage < (pageSource!!.getPageCount() - 1)) {
                            viewPager.currentItem = currentPage + 1
                        }
                        return true // Consume event
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadCBZLazy(uri: Uri) {
        comicViewProgress.visibility = View.VISIBLE
        launch {
            try {
                val source = withContext(ioDispatcher) {
                    CBZPageSource(this@ComicViewer, uri)
                }
                updateAdapter(source)
                comicViewProgress.visibility = View.GONE
                preloadPages()
            } catch (e: Exception) {
                showError("CBZ", e)
            }
        }
    }

    private fun loadCBRLazy(uri: Uri) {
        comicViewProgress.visibility = View.VISIBLE
        launch {
            try {
                val source = withContext(ioDispatcher) {
                    CBRPageSource(this@ComicViewer, uri)
                }
                updateAdapter(source)
                comicViewProgress.visibility = View.GONE
                preloadPages()
            } catch (e: Exception) {
                showError("CBR", e)
            }
        }
    }

    private fun loadPDFLazy(uri: Uri) {
        comicViewProgress.visibility = View.VISIBLE
        launch {
            try {
                val source = withContext(ioDispatcher) {
                    PDFPageSource(this@ComicViewer, uri)
                }
                updateAdapter(source)
                comicViewProgress.visibility = View.GONE
                preloadPages()
            } catch (e: Exception) {
                showError("PDF", e)
            }
        }
    }


    private fun preloadPages() {
        val source = pageSource ?: return
        launch(ioDispatcher) {
            repeat(PRELOAD_PAGE_COUNT.coerceAtMost(source.getPageCount())) { i ->
                source.getPageBitmap(i)
            }
        }
    }

    private fun showError(type: String, e: Exception) {
        val message = "Error loading $type: ${e.message}"
        Log.e("ComicViewer", message, e)
        runOnUiThread {
            showToast(message)
            comicViewProgress.visibility = View.GONE
        }
    }

    private fun updateAdapter(pageSource: ComicPageSource) {
        this.pageSource = pageSource
        adapter = PageRendererAdapter(this, pageSource, ioDispatcher)
        viewPager.adapter = adapter

        slider.valueFrom = 0f
        slider.valueTo = (pageSource.getPageCount() - 1).toFloat()
        slider.stepSize = 1f
        slider.value = 0f
        tvPageNumber.text = getString(R.string.pages, 1, pageSource.getPageCount()
        )

        // Restore last page (if enabled)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean("open_last_file", false)) {
            val lastPage = prefs.getInt(KEY_LAST_PAGE, 0)
            if (lastPage in 0 until pageSource.getPageCount()) {
                viewPager.setCurrentItem(lastPage, true)
                slider.value = lastPage.toFloat()
            }
        }

        // Bottom sheet restore
        val bottomSheet = findViewById<View>(R.id.bottom_sheet)
        bottomSheet.isEnabled = true
        bottomSheet.isClickable = true
        bottomSheet.post { bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED }
    }

    private fun showInfoDialog(uri: Uri) {
        launch(ioDispatcher) {
            try {
                var name = "Unknown"
                var size = "Unknown"
                var date = "Unknown"

                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (it.moveToFirst()) {
                        if (nameIndex >= 0) name = it.getString(nameIndex)
                        if (sizeIndex >= 0) size = formatFileSize(it.getLong(sizeIndex))
                    }
                }

                val docFile = DocumentFile.fromSingleUri(this@ComicViewer, uri)
                if (docFile != null && docFile.lastModified() > 0) {
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(docFile.lastModified()))
                }

                var displayPath = uri.path ?: uri.toString()
                try {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val parts = docId.split(":")
                    if (parts.size == 2) {
                        val volume = parts[0]
                        val relativePath = parts[1]
                        displayPath = if (volume == "primary") {
                            "/storage/emulated/0/$relativePath"
                        } else {
                            "/storage/$volume/$relativePath"
                        }
                    }
                } catch (_: Exception) {
                    displayPath = docFile?.name ?: uri.toString()
                }

                withContext(Dispatchers.Main) {
                    newInstance(name, displayPath, date, size)
                        .show(supportFragmentManager, "InfoDialog")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to show info dialog: ${e.message}")
                }
            }
        }
    }

    // Formats file sizes for display
    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 KB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(sizeInBytes.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(sizeInBytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun toggleDayNightMode() {
        ThemeManager.toggleTheme(this)
    }

    private fun updateDayNightIcon() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isNightMode = prefs.getBoolean(KEY_THEME, false) // Get the current mode

        // Change the icon based on the current mode
        binding.imgbtnDarkMode.setImageResource(
            if (isNightMode) R.drawable.ic_bottom_menu_day else R.drawable.ic_bottom_menu_night // Night mode icon or Day mode icon , change to day
        )
    }


    private fun updateScrollTypeOrientation() {
        val pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isScrolling = pref.getBoolean(SCROLL_TYPE, false)
        viewPager.orientation =
            if (isScrolling) ViewPager2.ORIENTATION_VERTICAL else ViewPager2.ORIENTATION_HORIZONTAL
    }

    private fun updateScrollTypeIcon() {
        val pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isScrolling = pref.getBoolean(SCROLL_TYPE, false)
        binding.imgbtnScrollType.setImageResource(
            if (isScrolling) R.drawable.ic_bottom_menu_vertical else R.drawable.ic_bottom_menu_horizontal
        )
    }

    private fun showSelectPageDialog() {
        SelectPageDialog.newInstance { pageNumber ->
            if (pageSource != null && pageNumber in 0 until pageSource!!.getPageCount()) {
                viewPager.currentItem = pageNumber
            } else {
                showToast("Page number out of range")
            }
        }.show(supportFragmentManager, "select_page")
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter?.shutdown()
        job.cancel()
        when (val src = pageSource) {
            is PDFPageSource -> src.close()
            is CBRPageSource -> src.close()
            is BitmapPageSource -> src.clear()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Dismiss SelectPageDialog if visible
        (supportFragmentManager.findFragmentByTag("select_page") as? DialogFragment)
            ?.dismissAllowingStateLoss()

        // Dismiss InfoDialog if visible
        (supportFragmentManager.findFragmentByTag("InfoDialog") as? DialogFragment)
            ?.dismissAllowingStateLoss()
    }

}
