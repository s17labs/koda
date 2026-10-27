package com.s17labs.koda

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView

class CustomAboutDialog(private val context: Context) {

    private val dialog = Dialog(context)
    private var versionName: String = ""

    fun setVersion(version: String): CustomAboutDialog {
        versionName = version
        return this
    }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_about)
        dialog.showKodaDialog()

        dialog.findViewById<TextView>(R.id.textVersion).text = "v$versionName"

        dialog.findViewById<Button>(R.id.btnOk).setOnClickListener { dialog.dismiss() }

        setupLinkClick()
    }

    private fun setupLinkClick() {
        val contentText = context.getString(R.string.about_content)
        val linkStart = contentText.indexOf("https://github.com/s17labs/koda")
        val linkEnd = linkStart + "https://github.com/s17labs/koda".length

        val spannableString = SpannableString(contentText)

        spannableString.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/s17labs/koda"))
                context.startActivity(intent)
            }

            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.color = context.getColor(R.color.kodaBlue)
            }
        }, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val textContent = dialog.findViewById<TextView>(R.id.textContent)
        textContent.text = spannableString
        textContent.movementMethod = LinkMovementMethod.getInstance()
    }
}
