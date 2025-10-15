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
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.codecademy.comicreader.R

class RemoveFileDialog : DialogFragment() {
    private var listener: OnComicRemoveListener? = null

    companion object {
        fun newInstance(listener: OnComicRemoveListener?): RemoveFileDialog {
            val fragment = RemoveFileDialog()
            fragment.listener = listener
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_remove_file, container, false)

        val btnComicRemoveOk = view.findViewById<Button>(R.id.btn_remove_file_ok)
        val btnComicRemoveCancel = view.findViewById<Button>(R.id.btn_remove_file_cancel)

        btnComicRemoveOk.setOnClickListener { v: View? -> comicRemove() }
        btnComicRemoveCancel.setOnClickListener { v: View? -> dismiss() }

        return view
    }

    private fun comicRemove() {
        if (listener != null) {
            listener!!.onComicRemove()
        }
        dismiss()
    }

    fun interface OnComicRemoveListener {
        fun onComicRemove()
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
