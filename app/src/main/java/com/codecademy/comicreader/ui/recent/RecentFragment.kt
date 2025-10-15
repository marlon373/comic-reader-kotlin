package com.codecademy.comicreader.ui.recent

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.codecademy.comicreader.data.ComicDatabase
import com.codecademy.comicreader.databinding.FragmentRecentBinding
import com.codecademy.comicreader.dialog.ClearAllRecentDialog
import com.codecademy.comicreader.dialog.SortDialog
import com.codecademy.comicreader.model.Comic
import com.codecademy.comicreader.ui.comic.ComicAdapter
import com.codecademy.comicreader.view.ComicViewer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.Boolean
import kotlin.Exception
import kotlin.Long
import kotlin.collections.LinkedHashSet
import kotlin.text.split
import kotlin.text.toDouble
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.codecademy.comicreader.utils.SystemUtil


class RecentFragment : Fragment() {
    private var binding: FragmentRecentBinding? = null
    private var comicAdapter: ComicAdapter? = null
    private val recentComics = mutableListOf<Comic>()
    private var isGridView: Boolean = true
    private lateinit var recentViewModel: RecentViewModel
    private val ioDispatcher by lazy { SystemUtil.createSmartDispatcher(requireContext()) }


    companion object {
        private const val PREFS_NAME = "ComicPrefs"
        private const val KEY_DISPLAY_MODE = "isGridView"
        const val KEY_SORT_MODE = "isAscending"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecentBinding.inflate(inflater, container, false)
        val view = binding!!.root

        recentViewModel = ViewModelProvider(requireActivity())[RecentViewModel::class.java]

        loadPreferences()
        setupRecyclerView()

        comicAdapter = ComicAdapter(recentComics, ::onComicClicked, isGridView, requireContext(),ioDispatcher)
        binding!!.rvRecentDisplay.adapter = comicAdapter

        recentViewModel.getRecentComics().observe(viewLifecycleOwner) { comics ->
            recentComics.clear()
            recentComics.addAll(comics)
            comicAdapter?.notifyDataSetChanged()
        }

        loadSortPreferences()

        return view
    }

    private fun onComicClicked(comic: Comic) {
        val intent = Intent(requireContext(), ComicViewer::class.java).apply {
            putExtra("comicPath", comic.path)
        }
        startActivity(intent)
    }

    private fun loadPreferences() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true)
    }

    fun toggleDisplayMode() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var isGridViewPref = prefs.getBoolean(KEY_DISPLAY_MODE, true)

        isGridViewPref = !isGridViewPref
        prefs.edit { putBoolean(KEY_DISPLAY_MODE, isGridViewPref) }

        Log.d("RecentFragment", "toggleDisplayMode: Switched to ${if (isGridViewPref) "Grid" else "List"} View")

        setupRecyclerView()

        comicAdapter = ComicAdapter(recentComics, ::onComicClicked, isGridViewPref, requireContext(),ioDispatcher)
        binding?.rvRecentDisplay?.adapter = comicAdapter
        comicAdapter?.notifyDataSetChanged()
    }

    // Updates RecyclerView LayoutManager
    private fun setupRecyclerView() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true)

        Log.d("ComicFragment", "setupRecyclerView: Applying " + (if (isGridView) "Grid View" else "List View"))

        // Get current device orientation
        val layoutManager = getLayoutManager(isGridView)

        binding!!.rvRecentDisplay.setLayoutManager(layoutManager)
        binding!!.rvRecentDisplay.setHasFixedSize(true)
    }

    private fun getLayoutManager(isGridView: Boolean): RecyclerView.LayoutManager {
        val orientation = resources.configuration.orientation

        val layoutManager: RecyclerView.LayoutManager

        if (isGridView) {
            //  In portrait → 2 columns
            //  In landscape → 4 columns
            val spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
            layoutManager = GridLayoutManager(context, spanCount)
        } else {
            // List mode always 1 column
            layoutManager = LinearLayoutManager(context)
        }
        return layoutManager
    }

    private fun recentLoadDatabase() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPaths = prefs.getStringSet("recent_paths", LinkedHashSet()) ?: return
        if (savedPaths.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
            val db = ComicDatabase.getInstance(requireContext())
            val allComics = db.comicDao().getAllComics()

            val comicMap = allComics.associateBy { it.path }
            val loadedComics = savedPaths.mapNotNull { path ->
                comicMap[path] ?: run {
                    Log.w("RecentFragment", "Comic not found in DB: $path")
                    null
                }
            }
            // Validate and filter using helper
            val validComics = recentComicList(loadedComics)
            // Update saved recent paths
            val validPaths = LinkedHashSet(validComics.map { it.path })
            prefs.edit { putStringSet("recent_paths", validPaths) }

            withContext(Dispatchers.Main) {
                recentViewModel.setRecentComics(validComics)
            }
        }
    }

    private fun recentComicList(newComics: List<Comic>): MutableList<Comic> {
        val context = requireContext()
        val prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE)
        val removedPaths = prefs.getStringSet("removed_paths", HashSet()) ?: HashSet()

        val validComics = mutableListOf<Comic>()

        for (comic in newComics) {
            if (removedPaths.contains(comic.path)) {
                Log.d("RecentFragment", "Skipping removed comic: ${comic.name}")
                continue
            }

            val file = DocumentFile.fromSingleUri(context, comic.path.toUri())
            if (file != null && file.exists()) {
                validComics.add(comic)
            } else {
                Log.w("RecentFragment", "File not found or missing: ${comic.name}")
            }
        }

        return validComics
    }

    private fun loadSortPreferences() {
        if (recentComics.isEmpty()) {
            Log.w("RecentFragment", "loadSortPreferences: comicFiles not ready. Skipping sort.")
            return
        }

        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val criteria = prefs.getString("sort_criteria", "name") ?: "name"
        val isAscending = prefs.getBoolean(KEY_SORT_MODE, true)

        Log.d("RecentFragment", "loadSortPreferences: criteria=$criteria, ascending=$isAscending")

        applySorting(criteria, isAscending)
    }

    private fun parseFileSize(sizeStr: String): Long {
        return try {
            val parts = sizeStr.split(" ")
            if (parts.size != 2) return 0
            val number = parts[0].replace(",", "").toDouble()
            return when (parts[1].uppercase()) {
                "B" -> number.toLong()
                "KB" -> (number * 1024).toLong()
                "MB" -> (number * 1024 * 1024).toLong()
                "GB" -> (number * 1024 * 1024 * 1024).toLong()
                else -> 0
            }
        } catch (e: Exception) {
            Log.e("RecentFragment", "Failed to parse size: $sizeStr", e)
            0
        }
    }

    private fun applySorting(criteria: String, isAscending: Boolean) {
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString("sort_criteria", criteria)
                .putBoolean(KEY_SORT_MODE, isAscending)
        }

        if (recentComics.isEmpty()) {
            Log.w("RecentFragment", "applySorting: comicFiles is null or empty. Skipping sort.")
            return
        }

        val comparator = when (criteria) {
            "size" -> compareBy<Comic> { parseFileSize(it.size) }
            "date" -> compareBy { it.date }
            "name" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

        if (isAscending) recentComics.sortWith(comparator)
        else recentComics.sortWith(comparator.reversed())

        comicAdapter?.updateComicList(recentComics.toList())

        Log.d("RecentFragment", "applySorting: Sorted by $criteria | Ascending: $isAscending")
    }

    fun showSortDialog() {
        val sortDialog = SortDialog.newInstance()
        sortDialog.setOnSortListener { criteria, isAscending ->
            Log.d("RecentFragment", "User selected sort - $criteria, Asc=$isAscending")
            applySorting(criteria, isAscending)
        }
        sortDialog.show(parentFragmentManager, "SortDialog")
    }

    fun showClearAllDialog() {
        val dialog = ClearAllRecentDialog.newInstance()
        dialog.setOnClearAllRecentListener {
            //  Clear from SharedPreferences
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { remove("recent_paths") }
            // Clear ViewModel to refresh the UI
            recentViewModel.setRecentComics(emptyList<Comic>().toMutableList())
        }
        dialog.show(parentFragmentManager, "ClearAllDialog")
    }

    override fun onResume() {
        super.onResume()
        recentLoadDatabase()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

