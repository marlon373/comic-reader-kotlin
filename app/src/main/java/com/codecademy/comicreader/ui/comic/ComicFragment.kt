package com.codecademy.comicreader.ui.comic

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codecademy.comicreader.data.ComicDatabase
import com.codecademy.comicreader.databinding.FragmentComicBinding
import com.codecademy.comicreader.dialog.SortDialog
import com.codecademy.comicreader.model.Comic
import com.codecademy.comicreader.model.Folder
import com.codecademy.comicreader.ui.library.LibraryViewModel
import com.codecademy.comicreader.ui.recent.RecentViewModel
import com.codecademy.comicreader.utils.SystemUtil
import com.codecademy.comicreader.view.ComicViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class ComicFragment : Fragment() {

    private var binding: FragmentComicBinding? = null
    private val comicFiles = mutableListOf<Comic>()
    private var isGridView: Boolean = true
    private lateinit var comicViewModel: ComicViewModel
    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var recentViewModel: RecentViewModel
    private lateinit var comicAdapter: ComicAdapter
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
        binding = FragmentComicBinding.inflate(inflater, container, false)
        val root = binding!!.root

        loadPreferences()
        setupRecyclerView()

        comicAdapter = ComicAdapter(mutableListOf(), this::onComicClicked, isGridView, requireContext(), ioDispatcher)
        binding?.rvComicDisplay?.adapter = comicAdapter

        updateComicsView()
        loadSortPreferences()

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        comicViewModel = ViewModelProvider(this)[ComicViewModel::class.java]
        recentViewModel = ViewModelProvider(requireActivity())[RecentViewModel::class.java]
        libraryViewModel = ViewModelProvider(requireActivity())[LibraryViewModel::class.java]

        observeFolderChanges()
        setupSwipeRefresh()
        observeUITexts()
    }

    // Observes folder changes using coroutines
    private fun observeFolderChanges() {

        // Folder added → Full rescan
        libraryViewModel.getFolderAdded().observe(viewLifecycleOwner) { added ->
            if (added == true) {
                Log.d("ComicFragment", "Folder was added — scanning...")
                scanAndUpdateComics(fullRescan = true)
                libraryViewModel.resetFolderAddedFlag()
            }
        }

        // Folder list changed → just refresh UI
        libraryViewModel.getFolders().observe(viewLifecycleOwner) { folders ->
            if (!folders.isNullOrEmpty()) {
                Log.d("ComicFragment", "Folders available — refreshing comic view.")
                updateComicsView()

                //  Check if DB is empty → trigger initial scan
                viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
                    val db = ComicDatabase.getInstance(requireContext())
                    val comicsEmpty = db.comicDao().getAllComics().isEmpty()
                    if (comicsEmpty) {
                        withContext(Dispatchers.Main){
                            Log.d("ComicFragment", "No comics in DB → initial full scan.")
                            scanAndUpdateComics(fullRescan = true)
                        }
                    }
                }
            }
        }

        // Folder removed → Full rescan
        libraryViewModel.getFolderRemoved().observe(viewLifecycleOwner) { removed ->
            if (removed == true) {
                comicFiles.clear()
                comicAdapter.updateComicList(mutableListOf())

                val folders = libraryViewModel.getFolders().value
                if (folders.isNullOrEmpty()) {
                    binding?.progressBar?.visibility = View.GONE
                    binding?.tvScanningBanner?.visibility = View.GONE
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(350)
                        scanAndUpdateComics(fullRescan = true)
                    }
                }
                libraryViewModel.notifyFolderRemovedHandled()
            }
        }

    }

    // Swipe refresh → Full rescan
    private fun setupSwipeRefresh() {

        binding?.swipeRefresh?.setOnRefreshListener {
            Log.d("ComicFragment", "Swipe-to-refresh triggered.")
            updateComicsView()

            val folders = libraryViewModel.getFolders().value
            if (folders.isNullOrEmpty()) {
                Log.d("ComicFragment", "No folders → skip scan.")
                binding?.swipeRefresh?.isRefreshing = false
                binding?.progressBar?.visibility = View.GONE
                binding?.tvScanningBanner?.visibility = View.GONE
                return@setOnRefreshListener
            }

            // Only scan if folders exist
            scanAndUpdateComics(fullRescan = true)

            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { putLong("last_scan_timestamp", System.currentTimeMillis()) }
        }

    }

    // UI messages
    private fun observeUITexts() {

        comicViewModel.noComicsMessage.observe(viewLifecycleOwner) {
            binding?.tvShowNoComicsFound?.text = it
        }
        comicViewModel.addOnLibraryMessage.observe(viewLifecycleOwner) {
            binding?.tvAddOnLibrary?.text = it
        }
        comicViewModel.noComicFolderMessage.observe(viewLifecycleOwner) {
            binding?.tvShowNoComicsFolderFound?.text = it
        }
    }

    // Display add folder first lunch
    private fun isFirstAppLaunch(): Boolean {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return !prefs.getBoolean("has_launched_before", false)
    }

    private fun onComicClicked(comic: Comic) {
        // 1. Save to recent (already existing)
        recentViewModel.addComicToRecent(comic)

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recentPaths = LinkedHashSet(prefs.getStringSet("recent_paths", LinkedHashSet()) ?: LinkedHashSet())
        recentPaths.remove(comic.path)
        recentPaths.add(comic.path)

        while (recentPaths.size > 20) {
            recentPaths.remove(recentPaths.iterator().next())
        }
        prefs.edit { putStringSet("recent_paths", recentPaths) }

        // 2. Start ComicViewer activity
        val intent = Intent(requireContext(), ComicViewer::class.java)
        intent.putExtra("comicPath", comic.path)
        startActivity(intent)
    }

    // Load display mode preference
    private fun loadPreferences() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true)
    }

    // Toggle between grid and list
    fun toggleDisplayMode() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var gridView = prefs.getBoolean(KEY_DISPLAY_MODE, true)

        gridView = !gridView
        prefs.edit { putBoolean(KEY_DISPLAY_MODE, gridView) }

        Log.d("ComicFragment", "toggleDisplayMode: Switched to ${if (gridView) "Grid View" else "List View"}")

        // Fully reset the RecyclerView layout
        setupRecyclerView()

        // Reload comics to reflect latest state
        updateComicsView() // Ensure comics are updated when switching layouts

        // Create a new adapter to force rebind with the correct layout
        comicAdapter = ComicAdapter(comicFiles, this::onComicClicked, gridView, requireContext(), ioDispatcher)
        binding?.rvComicDisplay?.adapter = comicAdapter

        comicAdapter.notifyDataSetChanged()// Ensure UI refresh
    }

    // Updates RecyclerView LayoutManager
    private fun setupRecyclerView() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true)

        Log.d("ComicFragment", "setupRecyclerView: Applying " + (if (isGridView) "Grid View" else "List View"))

        // Get current device orientation
        val layoutManager = getLayoutManager(isGridView)

        binding!!.rvComicDisplay.setLayoutManager(layoutManager)
        binding!!.rvComicDisplay.setHasFixedSize(true)
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

    // Updates the comic list from Room database
    private fun updateComicsView() {
        val context = context ?: run {
            Log.w("ComicFragment", "Context is null, skipping updateComicsView")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
            val db = ComicDatabase.getInstance(context)
            val cachedComics = db.comicDao().getAllComics()
            val validComics = mutableListOf<Comic>()

            val prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE)
            val removedPaths = prefs.getStringSet("removed_paths", emptySet()) ?: emptySet()

            for (comic in cachedComics) {
                if (removedPaths.contains(comic.path)) continue

                val file = DocumentFile.fromSingleUri(context, comic.path.toUri())
                if (file != null && file.exists()) {
                    validComics.add(comic)
                } else {
                    db.comicDao().deleteComicByPath(comic.path)
                }
            }
            withContext(Dispatchers.Main) {
                if (!isAdded || activity == null) return@withContext
                updateComicsList(validComics)
            }
        }
    }

    // Scanning or Rescanning
    private fun scanAndUpdateComics(fullRescan: Boolean = false) {

        binding?.apply {
            progressBar.visibility = View.VISIBLE
            tvScanningBanner.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
            val db = ComicDatabase.getInstance(requireContext())
            val folders = libraryViewModel.getFolders().value
            if (folders.isNullOrEmpty()) {
                db.comicDao().deleteAll()
                withContext(Dispatchers.Main) { updateComicsList(mutableListOf()) }
                return@launch
            }

            val removedPrefs = requireContext().getSharedPreferences("removed_comics", Context.MODE_PRIVATE)
            val removedPaths = removedPrefs.getStringSet("removed_paths", emptySet())?.toMutableSet() ?: mutableSetOf()
            val currentFolderPaths = folders.map { it.path }.toSet()

            // Clean up comics from removed folders
            removedPaths.removeIf { path -> currentFolderPaths.any { path.startsWith(it) } }
            removedPrefs.edit { putStringSet("removed_paths", removedPaths) }

            db.comicDao().getAllComics().forEach { comic ->
                if (currentFolderPaths.none { comic.path.startsWith(it) }) {
                    db.comicDao().deleteComicByPath(comic.path)
                }
            }

            val newComics = mutableListOf<Comic>()
            val existingPaths = db.comicDao().getAllComics().map { it.path }.toSet()
            val scanPrefs = requireContext().getSharedPreferences("FolderScanPrefs", Context.MODE_PRIVATE)
            val scanEditor = scanPrefs.edit()

            for (folder in folders) {
                val dir = DocumentFile.fromTreeUri(requireContext(), folder.path.toUri())
                if (dir == null || !dir.exists() || !dir.isDirectory) continue

                val lastScan = scanPrefs.getLong(folder.path, 0)
                val shouldScanAll = fullRescan || dir.lastModified() > lastScan

                if (shouldScanAll) {
                    scanFolderRecursively(dir, newComics, existingPaths.toMutableSet(), db)
                    scanEditor.putLong(folder.path, System.currentTimeMillis())
                }
            }

            if (newComics.isNotEmpty()) db.comicDao().insertAll(newComics)

            val finalList = db.comicDao().getAllComics()
            scanEditor.apply()

            withContext(Dispatchers.Main) {
                updateComicsList(finalList)
                binding?.apply {
                    progressBar.visibility = View.GONE
                    tvScanningBanner.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    // Recursive scan helper
    private suspend fun scanFolderRecursively(
        directory: DocumentFile,
        comics: MutableList<Comic>,
        existingComicPaths: MutableSet<String>,
        db: ComicDatabase
    ) {
        val batch = mutableListOf<Comic>()
        for (file in directory.listFiles()) {
            if (file.isDirectory) {
                scanFolderRecursively(file, comics, existingComicPaths, db)
            } else if (file.name?.endsWith(".cbr") == true ||
                file.name?.endsWith(".cbz") == true ||
                file.name?.endsWith(".pdf") == true
            ) {
                val path = file.uri.toString()
                if (path in existingComicPaths) continue

                val title = file.name ?: ""
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(file.lastModified()))
                val size = formatFileSize(file.length())
                val format = title.substringAfterLast('.', "")
                val comic = Comic(title, path, date, size, format)

                comics.add(comic)
                batch.add(comic)

                if (batch.size >= 10) {
                    db.comicDao().insertAll(batch)
                    val uiBatch = batch.toList()
                    batch.clear()
                    withContext(Dispatchers.Main) { comicAdapter.appendComics(uiBatch) }
                }
            }
        }
        if (batch.isNotEmpty()) {
            db.comicDao().insertAll(batch)
            val finalBatch = batch.toList()
            withContext(Dispatchers.Main) { comicAdapter.appendComics(finalBatch) }
        }
    }

    // Updates the comic list from Room database
    private fun updateComicsList(newComics: List<Comic>) {
        if (!isAdded) {
            Log.w("ComicFragment", "Fragment not attached, skipping updateComicsList.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
            val context = context
            if (context == null) {
                Log.w("ComicFragment", "Context is null in background thread, skipping update.")
                return@launch
            }

            val prefs = context.getSharedPreferences("removed_comics", Context.MODE_PRIVATE)
            val removedPaths = prefs.getStringSet("removed_paths", emptySet()) ?: emptySet()
            val seenPaths = mutableSetOf<String>()
            val validComics = mutableListOf<Comic>()

            for (comic in newComics) {
                val path = comic.path

                if (removedPaths.contains(path)) {
                    Log.d("ComicFragment", "Skipping removed comic: ${comic.name}")
                    continue
                }

                if (!seenPaths.add(path)) {
                    Log.d("ComicFragment", "Skipping duplicate comic: $path")
                    continue
                }

                val file = DocumentFile.fromSingleUri(context, path.toUri())
                if (file != null && file.exists()) {
                    validComics.add(comic)
                } else {
                    Log.w("ComicFragment", "Skipping missing comic: ${comic.name}")
                }
            }

            withContext(Dispatchers.Main) {
                if (!isAdded || activity == null) return@withContext

                val diffResult = DiffUtil.calculateDiff(ComicDiffCallback(comicFiles, validComics))
                comicFiles.clear()
                comicFiles.addAll(validComics)
                diffResult.dispatchUpdatesTo(comicAdapter)
                updateUIVisibility()
                loadSortPreferences()

                Log.d("ComicFragment", "Displayed comic list updated. Total: ${comicFiles.size}")
            }
        }
    }


    // Detect manually remove file or folder
    private fun areAllFoldersInvalid(folders: List<Folder>?): Boolean {
        if (folders.isNullOrEmpty()) return true
        return folders.none {
            val dir = DocumentFile.fromTreeUri(requireContext(), it.path.toUri())
            dir != null && dir.exists() && dir.isDirectory
        }
    }

    // Updates UI visibility based on whether comics exist
    private fun updateUIVisibility() {
        val b = binding
        if (b == null) {
            Log.e("ComicFragment", "Binding is null in updateUIVisibility")
            return
        }

        val noComicsMessage = b.tvShowNoComicsFound
        val addOnLibraryMessage = b.tvAddOnLibrary
        val noComicsFolderMessage = b.tvShowNoComicsFolderFound

        // Hide all initially
        noComicsMessage.visibility = View.GONE
        addOnLibraryMessage.visibility = View.GONE
        noComicsFolderMessage.visibility = View.GONE
        b.rvComicDisplay.visibility = View.GONE

        val isLoading = b.progressBar.isVisible
        if (isLoading) {
            // Don't show any message if loading
            b.tvShowNoComicsFound.visibility = View.GONE
            b.tvAddOnLibrary.visibility = View.GONE
            b.tvShowNoComicsFolderFound.visibility = View.GONE
            return
        }

        // If comics are found, show only RecyclerView
        if (comicFiles.isNotEmpty()) {
            b.rvComicDisplay.visibility = View.VISIBLE
            Log.d("ComicFragment", "Comics found, showing RecyclerView.")
            return
        }

        // Get folders from ViewModel
        val folders = libraryViewModel.getFolders().value
        if (folders == null) {
            Log.w("ComicFragment", "Folders list is null. Skipping UI update.")
            return
        }

        if (isFirstAppLaunch()) {
            addOnLibraryMessage.visibility = View.VISIBLE
            Log.d("ComicFragment", "First launch: showing Add-on Library message.")
            return // Skip other checks
        }

        // If no valid folders
        if (areAllFoldersInvalid(folders)) {
            noComicsFolderMessage.visibility = View.VISIBLE
            Log.d("ComicFragment", "No valid folders found, showing 'No Comic Folder Found'.")
        } else {
            // Valid folders exist but no comics
            noComicsMessage.visibility = View.VISIBLE
            Log.d("ComicFragment", "Folders exist but no comics, showing 'No Comics Found'.")
        }
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 KB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(sizeInBytes.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(sizeInBytes / 1024.0.pow(digitGroups.toDouble())) +
                " " + units[digitGroups]
    }

    // Formats file sizes for display
    private fun parseFileSize(sizeStr: String): Long {
        return try {
            val parts = sizeStr.split(" ")
            if (parts.size != 2) return 0
            val number = parts[0].replace(",", "").toDouble()
            when (parts[1].uppercase()) {
                "B" -> number.toLong()
                "KB" -> (number * 1024).toLong()
                "MB" -> (number * 1024 * 1024).toLong()
                "GB" -> (number * 1024 * 1024 * 1024).toLong()
                else -> 0
            }
        } catch (e: Exception) {
            Log.e("ComicFragment", "Failed to parse size: $sizeStr", e)
            0
        }
    }

    private fun loadSortPreferences() {
        if (comicFiles.isEmpty()) {
            Log.w("ComicFragment", "loadSortPreferences: comicFiles not ready. Skipping sort.")
            return
        }
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val criteria = prefs.getString("sort_criteria", "name") ?: "name"
        val isAscending = prefs.getBoolean(KEY_SORT_MODE, true)

        Log.d("ComicFragment", "loadSortPreferences: criteria=$criteria, ascending=$isAscending")

        applySorting(criteria, isAscending)
    }

    private fun applySorting(criteria: String, isAscending: Boolean) {
        val prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString("sort_criteria", criteria)
            putBoolean(KEY_SORT_MODE, isAscending)
        }

        if (comicFiles.isEmpty()) {
            Log.w("ComicFragment", "applySorting: comicFiles is null or empty. Skipping sort.")
            return
        }

        val comparator = when (criteria) {
            "size" -> compareBy<Comic> { parseFileSize(it.size) }
            "date" -> compareBy { it.date }
            "name" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            else -> {
                Log.w("ComicFragment", "applySorting: Unknown sort criteria: $criteria, defaulting to name")
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
        }

        if (isAscending) {
            comicFiles.sortWith(comparator)
        } else {
            comicFiles.sortWith(comparator.reversed())
        }

        comicAdapter.updateComicList(comicFiles.toMutableList())

        Log.d("ComicFragment", "applySorting: Sorted by $criteria | Ascending: $isAscending")
    }

    // Sort arrange: name, size, date
    fun showSortDialog() {
        val sortDialog = SortDialog.newInstance()
        sortDialog.setOnSortListener { criteria, isAscending ->
            Log.d("ComicFragment", "showSortDialog: User selected sort - $criteria, Ascending: $isAscending")
            applySorting(criteria, isAscending)
        }
        sortDialog.show(parentFragmentManager, "SortDialog")
    }

    override fun onResume() {
        super.onResume()
        Log.d("ComicFragment", "onResume: checking comics state...")

        // Always validate DB + refresh UI
        updateComicsView()

        // Skip scanning on very first app launch
        if (isFirstAppLaunch()) {
            Log.d("ComicFragment", "First app launch → skip onResume scan.")
            return
        }

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastScanTime = prefs.getLong("last_scan_timestamp", 0)
        val now = System.currentTimeMillis()

        val folders = libraryViewModel.getFolders().value
        if (folders.isNullOrEmpty()) {
            Log.d("ComicFragment", "No folders in library → skipping scan.")
            return
        }

        val scanPrefs = requireContext().getSharedPreferences("FolderScanPrefs", Context.MODE_PRIVATE)
        var shouldRescan = false

        for (folder in folders) {
            val dir = DocumentFile.fromTreeUri(requireContext(), folder.path.toUri())
            if (dir == null || !dir.exists() || !dir.isDirectory) continue

            val lastModified = dir.lastModified()
            val lastScan = scanPrefs.getLong(folder.path, 0)

            if (lastModified > lastScan) {
                Log.d("ComicFragment", "Folder changed: ${folder.path}")
                shouldRescan = true
                break
            }
        }

        if (shouldRescan || now - lastScanTime >= 5 * 60_000) { // fallback: 5 min
            Log.d("ComicFragment", "Triggering rescan on resume.")
            scanAndUpdateComics(fullRescan = false)
            prefs.edit { putLong("last_scan_timestamp", now) }
        } else {
            Log.d("ComicFragment", "Skipping rescan on resume (no changes detected).")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

}