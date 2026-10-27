package com.s17labs.koda

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class CustomTextInputDialog(context: Context) {

    private val dialog = Dialog(context)
    private var title: String = ""
    private var hint: String = ""
    private var defaultText: String = ""
    private var onOkListener: ((String) -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    fun setTitle(title: String): CustomTextInputDialog {
        this.title = title
        return this
    }

    fun setHint(hint: String): CustomTextInputDialog {
        this.hint = hint
        return this
    }

    fun setDefaultText(text: String): CustomTextInputDialog {
        this.defaultText = text
        return this
    }

    fun setOnOkClickListener(listener: (String) -> Unit): CustomTextInputDialog {
        onOkListener = listener
        return this
    }

    fun setOnCancelClickListener(listener: () -> Unit): CustomTextInputDialog {
        onCancelListener = listener
        return this
    }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_text_input)
        dialog.showKodaDialog()

        val titleView = dialog.findViewById<TextView>(R.id.dialogTitle)
        val editText = dialog.findViewById<EditText>(R.id.editText)
        val btnOk = dialog.findViewById<Button>(R.id.btnOk)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

        titleView.text = title
        editText.hint = hint
        editText.setText(defaultText)
        editText.selectAll()

        btnOk.isEnabled = defaultText.isNotEmpty()
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnOk.isEnabled = !s.isNullOrBlank()
            }
        })

        btnOk.setOnClickListener {
            val inputText = editText.text.toString().trim()
            if (inputText.isNotEmpty()) {
                onOkListener?.invoke(inputText)
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener {
            onCancelListener?.invoke()
            dialog.dismiss()
        }

        editText.postDelayed({
            editText.requestFocus()
            val imm = dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }
}
