package com.codecademy.comicreader.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.codecademy.comicreader.R


// Problem layout-size on other devices
class InfoDialog : DialogFragment() {

    companion object {
        @JvmStatic
        fun newInstance(name: String?, path: String?, date: String?, size: String?): InfoDialog {
            val fragment = InfoDialog()
            val args = Bundle()
            args.putString("name", name)
            args.putString("path", path)
            args.putString("date", date)
            args.putString("size", size)
            fragment.setArguments(args)
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_info, container, false)

        val btnInfoClose = view.findViewById<Button>(R.id.btn_info_close)
        btnInfoClose.setOnClickListener { v: View? -> dismiss() }

        comicInfo(view) // Call this with the root view

        return view
    }

    private fun comicInfo(view: View) {
        val args = arguments
        if (args == null) return

        val name = args.getString("name", "N/A")
        val path = args.getString("path", "N/A")
        val date = args.getString("date", "N/A")
        val size = args.getString("size", "N/A")

        val tvName = view.findViewById<TextView>(R.id.tv_info_name_value)
        val tvPath = view.findViewById<TextView>(R.id.tv_info_path_value)
        val tvDate = view.findViewById<TextView>(R.id.tv_info_last_mod_date_value)
        val tvSize = view.findViewById<TextView>(R.id.tv_info_size_value)

        tvName.text = name
        tvPath.text = path
        tvDate.text = date
        tvSize.text = size
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

    override fun onPause() {
        super.onPause()
        if (dialog?.isShowing == true && !isRemoving) {
            dismissAllowingStateLoss()
        }
    }
}

