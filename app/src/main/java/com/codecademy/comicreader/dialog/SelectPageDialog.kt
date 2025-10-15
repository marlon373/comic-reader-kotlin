package com.codecademy.comicreader.dialog

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.codecademy.comicreader.R

class SelectPageDialog : DialogFragment() {

    private var listener: OnComicSelectPageListener? = null

    companion object {
        fun newInstance(listener: OnComicSelectPageListener): SelectPageDialog {
            val fragment = SelectPageDialog()
            fragment.listener = listener
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.dialog_select_page, container, false)

        val btnComicSelectOk: Button = view.findViewById(R.id.btn_select_ok)
        val btnComicSelectCancel: Button = view.findViewById(R.id.btn_select_cancel)

        btnComicSelectOk.setOnClickListener { comicSelect() }
        btnComicSelectCancel.setOnClickListener { dismiss() }

        return view
    }

    private fun comicSelect() {
        val etComicSelectPage: EditText = requireView().findViewById(R.id.et_select_page_value)
        val input = etComicSelectPage.text.toString().trim()

        if (input.isNotEmpty()) {
            try {
                val pageNumber = input.toInt() - 1 // ViewPager uses 0-based index
                listener?.onComicSelect(pageNumber)
                dismiss() // Close dialog
            } catch (_: NumberFormatException) {
                etComicSelectPage.error = "Invalid page number"
            }
        } else {
            etComicSelectPage.error = "Page number required"
        }
    }

   fun interface OnComicSelectPageListener {
        fun onComicSelect(pageNumber: Int)
    }

    // Adjusts the dialog size and background styling when it appears
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

}

