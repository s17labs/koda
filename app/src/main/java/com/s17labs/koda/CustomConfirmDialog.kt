package com.s17labs.koda

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class CustomConfirmDialog(context: Context) {

    private val dialog = Dialog(context)
    private var title: String = ""
    private var message: String = ""
    private var positiveText: String = ""
    private var negativeText: String = ""
    private var onPositiveListener: (() -> Unit)? = null
    private var onNegativeListener: (() -> Unit)? = null

    fun setTitle(title: String): CustomConfirmDialog {
        this.title = title
        return this
    }

    fun setMessage(message: String): CustomConfirmDialog {
        this.message = message
        return this
    }

    fun setPositiveButton(text: String, listener: () -> Unit): CustomConfirmDialog {
        this.positiveText = text
        this.onPositiveListener = listener
        return this
    }

    fun setNegativeButton(text: String, listener: () -> Unit): CustomConfirmDialog {
        this.negativeText = text
        this.onNegativeListener = listener
        return this
    }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setDimAmount(0.6f)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        val titleView = dialog.findViewById<TextView>(R.id.dialogTitle)
        val messageView = dialog.findViewById<TextView>(R.id.dialogMessage)
        val positiveBtn = dialog.findViewById<Button>(R.id.btnPositive)
        val negativeBtn = dialog.findViewById<Button>(R.id.btnNegative)

        titleView.text = title
        messageView.text = message
        positiveBtn.text = positiveText
        negativeBtn.text = negativeText

        positiveBtn.setOnClickListener {
            onPositiveListener?.invoke()
            dialog.dismiss()
        }

        negativeBtn.setOnClickListener {
            onNegativeListener?.invoke()
            dialog.dismiss()
        }
    }
}
