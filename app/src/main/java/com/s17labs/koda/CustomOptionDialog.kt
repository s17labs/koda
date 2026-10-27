package com.s17labs.koda

import android.app.Dialog
import android.content.Context
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.widget.CompoundButtonCompat

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
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_option)
        dialog.showKodaDialog()

        dialog.findViewById<TextView>(R.id.dialogTitle).text = title

        val radioGroup = dialog.findViewById<RadioGroup>(R.id.radioGroup)
        radioGroup.removeAllViews()

        options.forEachIndexed { index, option ->
            val radioButton = RadioButton(dialog.context).apply {
                text = option
                CompoundButtonCompat.setButtonTintList(this, null)
                setButtonDrawable(R.drawable.radio_button_selector)
                compoundDrawablePadding = (12 * resources.displayMetrics.density).toInt()
                id = index
                setTextColor(dialog.context.getColor(R.color.kodaText))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val density = resources.displayMetrics.density
                setPadding((8 * density).toInt(), (12 * density).toInt(),
                    (8 * density).toInt(), (12 * density).toInt())
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
