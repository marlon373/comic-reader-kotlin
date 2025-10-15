package com.codecademy.comicreader.ui.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.codecademy.comicreader.MainActivity
import com.codecademy.comicreader.R
import com.codecademy.comicreader.data.ComicDatabase
import com.codecademy.comicreader.data.LibraryDatabase
import com.codecademy.comicreader.data.dao.LibraryDao
import com.codecademy.comicreader.databinding.FragmentLibraryBinding
import com.codecademy.comicreader.dialog.RemoveFolderDialog
import com.codecademy.comicreader.model.Folder
import com.codecademy.comicreader.utils.FolderUtils
import com.codecademy.comicreader.view.ComicViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack
import java.util.concurrent.Executors

class LibraryFragment : Fragment() {

    private var binding: FragmentLibraryBinding? = null
    private val folderItems = mutableListOf<Folder>()
    private var libraryFolderAdapter: LibraryFolderAdapter? = null
    private val folderHistory: Stack<Uri> = Stack()
    private var selectedFolder: Folder? = null // To track the currently selected folder
    private var isItemSelected = false // Track selection state for action_delete visibility
    private var isNavigating = false
    private lateinit var libraryViewModel: LibraryViewModel
    private val ioDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()



    // Room database and DAO
    private lateinit var libraryDatabase: LibraryDatabase
    private lateinit var folderItemDao: LibraryDao


    // Folder picker launcher
    private val folderPickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val uri = result.data!!.data
                if (uri != null) {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    addFolder(uri)
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentLibraryBinding.inflate(inflater, container, false)
        val view = binding!!.root

        // Initialize Room database and DAO
        libraryDatabase = LibraryDatabase.getInstance(requireContext())
        folderItemDao = libraryDatabase.folderItemDao()

       setupRecyclerView()

        // FAB click opens the folder picker
        binding!!.fab.setOnClickListener { openFolderPicker() }

        // Load saved folders
        loadSavedFolders()

        // Menu provider
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_library, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_delete)?.isVisible = isItemSelected && selectedFolder != null
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        if (folderHistory.isNotEmpty()) {
                            folderHistory.pop()
                            if (folderHistory.isNotEmpty()) {
                                val parentUri = folderHistory.peek()
                                folderItems.clear()
                                loadFolderContents(parentUri)
                            } else {
                                resetToFolderList()
                            }
                            true
                        } else false
                    }
                    R.id.action_delete -> {
                        showRemoveFolderDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now it's safe to access ViewModel scoped to activity
        libraryViewModel = ViewModelProvider(requireActivity())[LibraryViewModel::class.java]

        // Observes ViewModel text changes and updates UI
        libraryViewModel.addFolderLibrary.observe(viewLifecycleOwner) { message ->
            binding?.tvInstruction?.text = message
            updateEmptyMessageVisibility()
        }

        //  Observe folders or perform setup
        libraryViewModel.getFolders().observe(viewLifecycleOwner) { folders ->
            Log.d("LibraryFragment", "Observed folders: ${folders.size}")
            folderItems.clear()
            folderItems.addAll(folders)
            libraryFolderAdapter?.notifyDataSetChanged()
            updateEmptyMessageVisibility()
        }
    }

    // Updates RecyclerView LayoutManager based on orientation
    private fun setupRecyclerView() {
        if (binding == null) return

        // Get the current orientation
        val orientation = resources.configuration.orientation

        //  Portrait = 3 columns | Landscape = 5 columns
        val spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3

        val gridLayoutManager = GridLayoutManager(context, spanCount)
        binding!!.rvComicLibrary.setLayoutManager(gridLayoutManager)
        binding!!.rvComicLibrary.setHasFixedSize(true)

        // Adapter setup (if not yet set)
        if (libraryFolderAdapter == null) {
            libraryFolderAdapter = LibraryFolderAdapter(folderItems, { item: Folder -> this.onFolderClicked(item) }, { item: Folder?, itemView: View? -> this.onFolderLongClicked(item!!, itemView!!) }
            )
            binding!!.rvComicLibrary.setAdapter(libraryFolderAdapter)
        } else {
            binding!!.rvComicLibrary.setAdapter(libraryFolderAdapter)
        }
    }

    // Updates the toolbar name dynamically
    private fun updateToolbarTitle(title: String) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    // Enables or disables the back button in the toolbar
    private fun setToolbarBackButtonEnabled(enabled: Boolean) {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(enabled)
            setHomeAsUpIndicator(null) // Use default back arrow
        }
    }

    // Shows or hides the empty folder message based on folder availability
    private fun updateEmptyMessageVisibility() {
        binding?.tvInstruction?.visibility =
            if (folderItems.isEmpty()) View.VISIBLE else View.GONE // Show message if no folders or Hide message if folders exist
    }

    // Hide add-folder on comicFragment
    private fun setFirstAppLaunchCompleted() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("has_launched_before", true) }
    }

    // Opens Android’s folder picker
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        folderPickerLauncher.launch(intent)
    }

    // Adds a new folder and saves it in the database
    private fun addFolder(uri: Uri) {
        val directory = DocumentFile.fromTreeUri(requireContext(), uri)
        if (directory != null && directory.isDirectory) {
            // Check if the folder already exists in the list
            if (folderItems.any { it.path == uri.toString() }) {
                Toast.makeText(requireContext(), "Folder already exists", Toast.LENGTH_SHORT).show()
                return // Prevent duplicate addition
            }
            // Check if the folder already exists in the database
            viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
                val existingFolder = folderItemDao.getFolderByPath(uri.toString())
                if (existingFolder != null) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Folder already exists in database", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                // If folder is unique, add it
                val newItem = Folder(directory.name ?: "Unknown", uri.toString(), true)
                folderItems.add(newItem)
                folderItemDao.insert(newItem)

                withContext(Dispatchers.Main) {
                    libraryFolderAdapter?.notifyDataSetChanged()
                    FolderUtils.saveFolders(requireContext(), folderItems)
                    libraryViewModel.setFolders(folderItems) // Ensure LiveData update
                    libraryViewModel.notifyFolderAdded() // Trigger ComicFragment
                    updateEmptyMessageVisibility()
                }
                Log.d("LibraryFragment", "Folder saved in DB: ${directory.name}")
            }
        }
        setFirstAppLaunchCompleted()
    }

    // Loads saved folders from the database
    private fun loadSavedFolders() {
        viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
            val savedEntities = folderItemDao.allFolders
            val newFolderItems = mutableListOf<Folder>()

            for (entity in savedEntities) {
                val directory = DocumentFile.fromTreeUri(requireContext(), entity.path.toUri())
                if (directory != null && directory.isDirectory) {
                    newFolderItems.add(Folder(entity.name, entity.path, true))
                }
            }

            withContext(Dispatchers.Main) {
                folderItems.clear()
                folderItems.addAll(newFolderItems)
                libraryFolderAdapter?.notifyDataSetChanged()
                FolderUtils.saveFolders(requireContext(), newFolderItems)
                libraryViewModel.setFolders(newFolderItems) // Ensure ComicFragment updates
                updateEmptyMessageVisibility()
                // Set launch complete only if folders were loaded
                if (newFolderItems.isNotEmpty()) setFirstAppLaunchCompleted()
            }
        }
    }

    // Loads the contents of a selected folder
    private fun loadFolderContents(uri: Uri) {
        if (folderHistory.isNotEmpty() && folderHistory.peek() == uri) {
            folderItems.clear()
        } else {
            folderHistory.push(uri)
        }

        isNavigating = true
        folderItems.clear()

        val directory = DocumentFile.fromTreeUri(requireContext(), uri)
        if (directory != null && directory.isDirectory) {
            for (file in directory.listFiles()) {
                folderItems.add(Folder(file.name ?: "Unknown", file.uri.toString(), file.isDirectory))
            }
            binding?.tvLibrary?.visibility = View.GONE
            setToolbarBackButtonEnabled(true)
            updateToolbarTitle(directory.name ?: "Folder")
        } else {
            Toast.makeText(requireContext(), "Unable to open folder", Toast.LENGTH_SHORT).show()
        }

        binding?.fab?.visibility = View.GONE
        libraryFolderAdapter?.notifyDataSetChanged()
    }

    // Resets the view back to the folder list
    private fun resetToFolderList() {
        isNavigating = false
        folderHistory.clear()
        folderItems.clear()
        loadSavedFolders()

        setToolbarBackButtonEnabled(false)
        binding?.tvLibrary?.visibility = View.VISIBLE
        (activity as? MainActivity)?.setupHamburgerMenu()
        binding?.fab?.visibility = View.VISIBLE
        libraryFolderAdapter?.notifyDataSetChanged()
    }

    // Open CBR,CBZ and PDF file
    private fun openComicBook(filePath: String) {
        val intent = Intent(requireContext(), ComicViewer::class.java)
        intent.putExtra("comicPath", filePath)
        startActivity(intent)
    }

    // Handles folder and file clicks
    private fun onFolderClicked(item: Folder) {
        // Ignore normal clicks when an item is selected (selection mode)
        if (isItemSelected) {
            // Optional: clear selection if clicked again
            selectedFolder = null
            isItemSelected = false
            libraryFolderAdapter?.setSelectedFolder(null)
            requireActivity().invalidateOptionsMenu() // Hide delete action
            return
        }

        // Continue normal navigation if not in selection mode
        if (item.isFolder) {
            loadFolderContents(item.path.toUri()) // Navigate into folder
        } else {
            if (item.path.endsWith(".cbr") || item.path.endsWith(".cbz") || item.path.endsWith(".pdf")) {
                openComicBook(item.path)
            } else {
                Toast.makeText(requireContext(), "Unsupported file type: ${item.path}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handles folder long-click for selection
    private fun onFolderLongClicked(item: Folder, itemView: View) {
        // Do not allow selection mode while navigating inside subfolders
        if (isNavigating) return

        // Toggle selection
        if (selectedFolder == item) {
            selectedFolder = null
            isItemSelected = false
        } else {
            selectedFolder = item
            isItemSelected = true
        }

        libraryFolderAdapter?.setSelectedFolder(selectedFolder)
        requireActivity().invalidateOptionsMenu() // Refresh menu to show delete action
    }

    fun showRemoveFolderDialog() {
        selectedFolder?.let { folder ->
            val dialog = RemoveFolderDialog.newInstances(folder.path.toUri())
            dialog.setOnFolderRemoveListener { folderPath ->
                viewLifecycleOwner.lifecycleScope.launch(ioDispatcher) {
                    val entity = folderItemDao.getFolderByPath(folderPath)
                    if (entity != null) {
                        // Step 1: Delete the folder from the library DB
                        folderItemDao.delete(entity)

                        // Step 2: Delete comics from the deleted folder
                        val db = ComicDatabase.getInstance(requireContext())
                        db.comicDao().deleteComicsByFolderPath(folderPath)

                        // Step 3: Remove all "removed" comic paths under this folder
                        val prefs = requireContext().getSharedPreferences("removed_comics", Context.MODE_PRIVATE)
                        val removedPaths = prefs.getStringSet("removed_paths", emptySet())!!.toMutableSet()
                        removedPaths.removeIf { it.startsWith(folderPath) } // Clear related comic removals
                        prefs.edit { putStringSet("removed_paths", removedPaths) }

                        // Step 4: Update UI and ViewModel
                        withContext(Dispatchers.Main) {
                            folderItems.removeIf { it.path == folderPath }
                            libraryFolderAdapter?.notifyDataSetChanged()
                            FolderUtils.saveFolders(requireContext(), folderItems) //  Triggers ComicFragment to reload
                            libraryViewModel.setFolders(ArrayList(folderItems)) // Let ComicFragment know about the removal
                            libraryViewModel.notifyFolderRemoved()

                            // Clear selection state
                            selectedFolder = null
                            isItemSelected = false
                            requireActivity().invalidateOptionsMenu() // Refresh the menu to hide delete
                            updateEmptyMessageVisibility()
                        }
                        Log.d("LibraryFragment", "Folder removed and related comics cleared: $folderPath")
                    } else {
                        Log.e("LibraryFragment", "Folder not found in database: $folderPath")
                    }
                }
            }
            dialog.show(childFragmentManager, "RemoveFolderDialog")
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (folderHistory.isNotEmpty()) {
                        folderHistory.pop()
                        if (folderHistory.isNotEmpty()) {
                            val parentUri = folderHistory.peek()
                            folderItems.clear()
                            loadFolderContents(parentUri)
                        } else {
                            resetToFolderList()
                        }
                    } else {
                        requireActivity().finish()
                    }
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ioDispatcher.close() // shuts down the underlying single thread
        binding = null
    }
}