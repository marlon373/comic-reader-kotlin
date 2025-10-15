package com.codecademy.comicreader.dialog


import android.content.Context
import android.content.res.Configuration
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

class ClearAllRecentDialog : DialogFragment() {
    private var onClearAllRecentListener: OnClearAllRecentListener? = null

    companion object {
        fun newInstance(): ClearAllRecentDialog {
            return ClearAllRecentDialog()
        }
    }

    fun setOnClearAllRecentListener(listener: OnClearAllRecentListener?) {
        this.onClearAllRecentListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_clear_all_recent, container, false)

        val btnClearRecentOk = view.findViewById<Button>(R.id.btn_clear_all_ok)
        val btnClearRecentCancel = view.findViewById<Button>(R.id.btn_clear_all_cancel)

        btnClearRecentOk.setOnClickListener { v: View? ->
            if (onClearAllRecentListener != null) {
                onClearAllRecentListener!!.onClearAllRecent()
            }
            dismiss()
        }

        btnClearRecentCancel.setOnClickListener { v: View? -> dismiss() }

        return view
    }

   fun interface OnClearAllRecentListener {
        fun onClearAllRecent()
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
            val widthPercent = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
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
