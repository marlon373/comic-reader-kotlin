package com.codecademy.comicreader.dialog

import android.content.Context
import android.graphics.Color
import android.net.Uri
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
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.codecademy.comicreader.R

// DialogFragment to confirm and handle folder removal from the UI
class RemoveFolderDialog : DialogFragment() {
    private var folderUri: Uri? = null
    private var onFolderRemoveListener: OnFolderRemoveListener? = null


    companion object {
        // Factory method to create an instance of the dialog with folder URI as an argument
        fun newInstances(folderUri: Uri?): RemoveFolderDialog {
            val deleteFolderDialog = RemoveFolderDialog()
            val args = Bundle()
            args.putParcelable("folderUri", folderUri)
            deleteFolderDialog.setArguments(args)
            return deleteFolderDialog
        }
    }

    // Sets the listener for folder removal
    fun setOnFolderRemoveListener(listener: OnFolderRemoveListener?) {
        this.onFolderRemoveListener = listener
    }

    // Inflates the dialog layout and sets up click listeners
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_remove_folder, container, false)

        val folderOk = view.findViewById<Button>(R.id.btn_remove_folder_ok)
        val folderCancel = view.findViewById<Button>(R.id.btn_remove_folder_cancel)

        // Set up the remove button action
        folderOk.setOnClickListener { v: View? -> removeFolder() }

        // Cancel button dismisses the dialog
        folderCancel.setOnClickListener { v: View? -> dismiss() }


        return view
    }

    // Handles folder removal (removes from UI only, not from storage)
    private fun removeFolder() {
        folderUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable("folderUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable("folderUri")
        }

        if (folderUri != null) {
            Log.d("RemoveFolderDialog", "Removing folder from UI only: $folderUri")
            if (onFolderRemoveListener != null) {
                onFolderRemoveListener!!.onFolderRemove(folderUri.toString()) // Only notify, don't delete storage
            }
            dismiss()
        } else {
            Log.e("RemoveFolderDialog", "Invalid folder URI")
        }
    }

    // Interface for folder removal event callback
    fun interface OnFolderRemoveListener {
        fun onFolderRemove(folderPath: String)
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
                0.50  // smaller width for landscape
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