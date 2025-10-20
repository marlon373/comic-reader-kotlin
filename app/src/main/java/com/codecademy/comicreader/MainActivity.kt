package com.codecademy.comicreader

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI.navigateUp
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import com.codecademy.comicreader.databinding.ActivityMainBinding
import com.codecademy.comicreader.ui.comic.ComicFragment
import com.codecademy.comicreader.ui.library.LibraryViewModel
import com.codecademy.comicreader.ui.recent.RecentFragment
import com.codecademy.comicreader.utils.FolderUtils
import androidx.core.content.edit
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.codecademy.comicreader.theme.ThemeManager


class MainActivity : AppCompatActivity() {
    private var mAppBarConfiguration: AppBarConfiguration? = null
    private var binding: ActivityMainBinding? = null

    companion object {
        private const val PREFS_NAME = "comicPrefs"
        private const val KEY_THEME = "isNightMode"
        const val KEY_DISPLAY_MODE: String = "isGridView"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forcefully apply app-defined theme
        ThemeManager.applyTheme(this)

        // Initialize LibraryViewModel scoped to the activity
        val libraryViewModel =
            ViewModelProvider(this).get<LibraryViewModel>(LibraryViewModel::class.java)

        // Load folder list from SharedPreferences
        val folders = FolderUtils.loadFolders(this)

        // Push folder list into ViewModel so it's shared across fragments
        libraryViewModel.setFolders(folders)

        Log.d("MainActivity", "Folders loaded at startup: " + folders.size)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.getRoot())

        setSupportActionBar(binding!!.appBarMain.toolbar)

        val drawer = binding!!.drawerLayout
        val navigationView = binding!!.navView

        mAppBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_comic, R.id.nav_recent, R.id.nav_library, R.id.nav_settings
        )
            .setOpenableLayout(drawer)
            .build()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        setupActionBarWithNavController(navController, mAppBarConfiguration!!)
        navigationView.setupWithNavController(navController)

        // Listen for destination changes and refresh menu
        navController.addOnDestinationChangedListener { controller: NavController?, destination: NavDestination?, arguments: Bundle? ->
            invalidateOptionsMenu() // Refresh menu when destination changes
        }
    }


    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear() // Clear the previous menu
        val navController = findNavController(this, R.id.nav_host_fragment_content_main)

        val currentDestination = navController.currentDestination
        if (currentDestination == null) return super.onPrepareOptionsMenu(menu) // Prevent crashes


        val currentDestinationId = currentDestination.id

        when (currentDestinationId) {
            R.id.nav_comic -> {
                menuInflater.inflate(R.menu.menu_comic, menu)
            }
            R.id.nav_recent -> {
                menuInflater.inflate(R.menu.menu_recent, menu)
            }
            R.id.nav_library -> {
                menuInflater.inflate(R.menu.menu_library, menu)
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_more) {
            val anchor = findViewById<View?>(R.id.action_more)
            val dest =
                findNavController(this, R.id.nav_host_fragment_content_main).currentDestination
            if (dest != null) {
                val id = dest.id
                if (id == R.id.nav_comic) {
                    showCustomMenu(anchor, R.layout.custom_action_comic)
                } else if (id == R.id.nav_recent) {
                    showCustomMenu(anchor, R.layout.custom_action_recent)
                }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCustomMenu(anchor: View?, layoutResId: Int) {
        val popupView = LayoutInflater.from(this).inflate(layoutResId, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.isOutsideTouchable = true
        popupWindow.elevation = 8f // Optional shadow
        popupWindow.showAsDropDown(anchor)

        // === Action: SORT ===
        val sort = popupView.findViewById<View?>(R.id.action_sort)
        sort?.setOnClickListener { v: View? ->
            openSortDialog()
            popupWindow.dismiss()
        }

        // === Action: DISPLAY (Grid/List) ===
        val display = popupView.findViewById<View?>(R.id.action_display)
        if (display != null) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true)
            (display as TextView).setText(if (isGridView) R.string.action_list else R.string.action_grid)
            display.setCompoundDrawablesWithIntrinsicBounds(
                if (isGridView) R.drawable.ic_menu_item_list else R.drawable.ic_menu_item_grid,
                0,
                0,
                0
            )
            display.setOnClickListener { v: View? ->
                toggleDisplayMode()
                popupWindow.dismiss()
            }
        }

        // === Action: DAY/NIGHT MODE ===
        val dayNight = popupView.findViewById<View?>(R.id.action_day_night)
        if (dayNight != null) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val isNightMode = prefs.getBoolean(KEY_THEME, false)
            (dayNight as TextView).setText(if (isNightMode) R.string.action_day else R.string.action_night)
            dayNight.setCompoundDrawablesWithIntrinsicBounds(
                if (isNightMode) R.drawable.ic_menu_item_day else R.drawable.ic_menu_item_night,
                0,
                0,
                0
            )
            dayNight.setOnClickListener { v: View? ->
                toggleDayNightMode() // Toggle mode
                popupWindow.dismiss()
            }
        }

        // === Action: CLEAR ALL (only in recent layout) ===
        val clearAll = popupView.findViewById<View?>(R.id.action_clear_all)
        clearAll?.setOnClickListener { v: View? ->
            openClearAllDialog()
            popupWindow.dismiss()
        }
        // Show the popup below the toolbar icon
        popupWindow.showAsDropDown(anchor, -100, 0, Gravity.END)
    }

    private fun toggleDayNightMode() {
        ThemeManager.toggleTheme(this)
    }



    // Share toggleDisPlayMode on comicFragment and recentFragment
    private fun toggleDisplayMode() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        var isGridView = prefs.getBoolean(KEY_DISPLAY_MODE, true)
        isGridView = !isGridView
        prefs.edit { putBoolean(KEY_DISPLAY_MODE, isGridView) }

        // Toggle fragments
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        if (navHostFragment is NavHostFragment) {
            val fragment = navHostFragment.getChildFragmentManager().primaryNavigationFragment
            if (fragment is ComicFragment) {
                fragment.toggleDisplayMode()
            } else if (fragment is RecentFragment) {
                fragment.toggleDisplayMode()
            }
        }
    }

    // Share sort dialog on comicFragment and recentFragment
    private fun openSortDialog() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        if (navHostFragment is NavHostFragment) {
            val fragment = navHostFragment.getChildFragmentManager().primaryNavigationFragment
            when (fragment) {
                is ComicFragment -> {
                    fragment.showSortDialog()
                    Log.d("MainActivity", "Successfully called showSortDialog.")
                }

                is RecentFragment -> {
                    fragment.showSortDialog()
                    Log.d("MainActivity", "Successfully called showSortDialog.")
                }

                else -> {
                    Log.e("MainActivity", "ComicFragment not found!")
                }
            }
        }
    }

    private fun openClearAllDialog() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        if (navHostFragment is NavHostFragment) {
            val recentFragment =
                navHostFragment.getChildFragmentManager().primaryNavigationFragment
            if (recentFragment is RecentFragment) {
                recentFragment.showClearAllDialog()
                Log.d("MainActivity", "Successfully called showClearAllDialog.")
            } else {
                Log.e("MainActivity", "RecentFragment not found!")
            }
        }
    }

    fun setupHamburgerMenu() {
        val navController = findNavController(this, R.id.nav_host_fragment_content_main)
        setupActionBarWithNavController(this, navController, mAppBarConfiguration!!)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(this, R.id.nav_host_fragment_content_main)
        return navigateUp(navController, mAppBarConfiguration!!) || super.onSupportNavigateUp()
    }

}
