package com.s17labs.koda

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class CustomOptionDialog(context: Context) {

    private val dialog = Dialog(context)
    private var title: String = ""
    private var options: List<String> = emptyList()
    private var defaultIndex: Int = 0
    private var onOkListener: ((Int) -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    fun setTitle(title: String): CustomOptionDialog {
        this.title = title
        return this
    }

    fun setOptions(options: List<String>, defaultIndex: Int = 0): CustomOptionDialog {
        this.options = options
        this.defaultIndex = defaultIndex
        return this
    }

    fun setOnOkClickListener(listener: (Int) -> Unit): CustomOptionDialog {
        onOkListener = listener
        return this
    }

    fun setOnCancelClickListener(listener: () -> Unit): CustomOptionDialog {
        onCancelListener = listener
        return this
    }

    fun show() {
        dialog.setContentView(R.layout.dialog_option)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        dialog.findViewById<TextView>(R.id.dialogTitle).text = title

        val radioGroup = dialog.findViewById<RadioGroup>(R.id.radioGroup)
        radioGroup.removeAllViews()

        options.forEachIndexed { index, option ->
            val radioButton = RadioButton(dialog.context).apply {
                text = option
                id = index
                setTextColor(dialog.context.getColor(R.color.kodaText))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 32, 16, 32)
                if (index == defaultIndex) {
                    isChecked = true
                }
            }
            radioGroup.addView(radioButton)
        }

        var selectedIndex = defaultIndex
        radioGroup.setOnCheckedChangeListener { _, id ->
            selectedIndex = id
            onOkListener?.invoke(selectedIndex)
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            onCancelListener?.invoke()
            dialog.dismiss()
        }

    }
}
