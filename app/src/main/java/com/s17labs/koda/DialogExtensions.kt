package com.s17labs.koda

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager

internal fun Dialog.showKodaDialog() {
    window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val margin = (24 * context.resources.displayMetrics.density).toInt()
        decorView.setPadding(margin, 0, margin, 0)
        setGravity(Gravity.CENTER)
        setDimAmount(0.6f)
    }
    setCanceledOnTouchOutside(true)
    show()
    window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT
    )
}
