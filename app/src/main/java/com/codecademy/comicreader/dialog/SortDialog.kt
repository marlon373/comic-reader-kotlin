package com.codecademy.comicreader.dialog

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import androidx.fragment.app.DialogFragment
import com.codecademy.comicreader.R
import androidx.core.graphics.drawable.toDrawable

class SortDialog : DialogFragment() {

    private var onSortListener: OnSortListener? = null
    private var isAscending = true

    companion object {
        private const val TAG = "SortDialog"
        fun newInstance(): SortDialog = SortDialog()
    }

    fun setOnSortListener(listener: OnSortListener?) {
        if (listener == null) {
            Log.w(TAG, "setOnSortListener: Listener is null!")
        }
        onSortListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_sort_by, container, false)

        val sortName: RadioButton = view.findViewById(R.id.rb_sort_by_name)
        val sortSize: RadioButton = view.findViewById(R.id.rb_sort_by_size)
        val sortDate: RadioButton = view.findViewById(R.id.rb_sort_by_date)
        val sortDescending: Button = view.findViewById(R.id.btn_descending_button)
        val sortAscending: Button = view.findViewById(R.id.btn_ascending_button)

        val activity = activity
        if (activity == null) {
            Log.e(TAG, "Activity is null. Cannot load sorting preferences.")
            return view
        }

        val prefs = activity.getSharedPreferences("SortPrefs", Context.MODE_PRIVATE)
        val savedCriteria = prefs.getString("sort_criteria", "name") ?: "name"
        isAscending = prefs.getBoolean("sort_order", true)

        Log.d(TAG, "Loaded sort preferences: criteria=$savedCriteria, isAscending=$isAscending")

        when (savedCriteria) {
            "name" -> sortName.isChecked = true
            "size" -> sortSize.isChecked = true
            "date" -> sortDate.isChecked = true
            else -> {
                Log.w(TAG, "Unknown sorting criteria: $savedCriteria")
                sortName.isChecked = true // Default
            }
        }

        sortAscending.setOnClickListener {
            isAscending = true
            Log.d(TAG, "Ascending button clicked.")
            applySort(sortName, sortSize, sortDate)
        }

        sortDescending.setOnClickListener {
            isAscending = false
            Log.d(TAG, "Descending button clicked.")
            applySort(sortName, sortSize, sortDate)
        }

        return view
    }

    private fun applySort(
        sortName: RadioButton,
        sortSize: RadioButton,
        sortDate: RadioButton
    ) {
        val activity = activity
        if (activity == null) {
            Log.e(TAG, "Activity is null. Cannot apply sorting.")
            return
        }

        val criteria = when {
            sortName.isChecked -> "name"
            sortSize.isChecked -> "size"
            sortDate.isChecked -> "date"
            else -> {
                Log.w(TAG, "No sorting criteria selected. Defaulting to 'name'.")
                "name"
            }
        }

        Log.d(TAG, "Applying sort: criteria=$criteria, isAscending=$isAscending")

        val prefs = activity.getSharedPreferences("SortPrefs", Context.MODE_PRIVATE)
        val success = prefs.edit()
            .putString("sort_criteria", criteria)
            .putBoolean("sort_order", isAscending)
            .commit()

        Log.d(TAG, "SharedPreferences commit status: $success")

        if (onSortListener != null) {
            Log.d(TAG, "onSortListener is set. Calling onSort()")
            onSortListener?.onSort(criteria, isAscending)
        } else {
            Log.e(TAG, "onSortListener is NULL! Sorting will NOT apply.")
        }

        if (isAdded) {
            dismiss()
        } else {
            Log.w(TAG, "SortDialog is not attached. Cannot dismiss.")
        }
    }

    fun interface OnSortListener {
        fun onSort(criteria: String, isAscending: Boolean)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val wm = requireContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val widthPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = wm.currentWindowMetrics
                val insets = metrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                metrics.bounds.width() - insets.left - insets.right
            } else {
                @Suppress("DEPRECATION")
                DisplayMetrics().apply {
                    wm.defaultDisplay.getMetrics(this)
                }.widthPixels
            }

            //  adjust width based on orientation
            val orientation = resources.configuration.orientation
            val widthPercent = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                0.52  // smaller width for landscape
            } else {
                0.90  // normal for portrait
            }

            val dialogWidth = (widthPx * widthPercent).toInt()
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    override fun onPause() {
        super.onPause()
        if (dialog?.isShowing == true && !isRemoving) {
            dismissAllowingStateLoss()
        }
    }

}