package com.s17labs.koda

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var switchOpenNew: CustomToggleView
    private lateinit var switchOpenLast: CustomToggleView
    private lateinit var switchAutoSave: CustomToggleView
    private lateinit var switchWrapLines: CustomToggleView
    private lateinit var switchWakeLock: CustomToggleView

    private lateinit var textLanguageValue: TextView
    private lateinit var textFontValue: TextView
    private lateinit var textFontSizeValue: TextView
    private lateinit var textVersion: TextView
    private lateinit var textOpenNewDesc: TextView
    private lateinit var textOpenLastDesc: TextView
    private lateinit var textSaveFolderValue: TextView

    private val languages = listOf("English", "Slovak")
    private val fonts = listOf("Monospace", "Serif", "Sans Serif")
    private val fontSizes = listOf("XS", "S", "M", "ML", "L", "XL")

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("koda_prefs", Context.MODE_PRIVATE)
        
        applyLocale()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
    }

    override fun onResume() {
        super.onResume()
        loadSaveFolderPath()
    }
    
    private fun applyLocale() {
        val language = prefs.getString("language", "English")
        val locale = when (language) {
            "Slovak" -> Locale("sk")
            else -> Locale.ENGLISH
        }
        
        val currentLocale = resources.configuration.locales[0]
        if (currentLocale.language != locale.language) {
            Locale.setDefault(locale)
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    private fun initViews() {
        switchOpenNew = findViewById(R.id.switchOpenNew)
        switchOpenLast = findViewById(R.id.switchOpenLast)
        switchAutoSave = findViewById(R.id.switchAutoSave)
        switchWrapLines = findViewById(R.id.switchWrapLines)
        switchWakeLock = findViewById(R.id.switchWakeLock)

        textLanguageValue = findViewById(R.id.textLanguageValue)
        textFontValue = findViewById(R.id.textFontValue)
        textFontSizeValue = findViewById(R.id.textFontSizeValue)
        textVersion = findViewById(R.id.textVersion)
        textOpenNewDesc = findViewById(R.id.textOpenNewDesc)
        textOpenLastDesc = findViewById(R.id.textOpenLastDesc)
        textSaveFolderValue = findViewById(R.id.textSaveFolderValue)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val versionName = BuildConfig.VERSION_NAME
        textVersion.text = "v$versionName"

        switchOpenNew.setOnCheckedChangeListener { isChecked ->
            prefs.edit().putBoolean("open_new", isChecked).apply()
            val bothEnabled = isChecked && switchOpenLast.isChecked
            textOpenNewDesc.text = if (bothEnabled) {
                getString(R.string.setting_open_new_desc_both)
            } else {
                getString(R.string.setting_open_new_desc)
            }
            textOpenLastDesc.text = if (bothEnabled) {
                getString(R.string.setting_open_last_desc_both)
            } else {
                getString(R.string.setting_open_last_desc)
            }
        }

        switchOpenLast.setOnCheckedChangeListener { isChecked ->
            if (isChecked) {
                prefs.edit().putBoolean("open_last", true).apply()
            } else {
                prefs.edit().putBoolean("open_last", false).remove("last_file").apply()
            }
            val bothEnabled = switchOpenNew.isChecked && isChecked
            textOpenNewDesc.text = if (bothEnabled) {
                getString(R.string.setting_open_new_desc_both)
            } else {
                getString(R.string.setting_open_new_desc)
            }
            textOpenLastDesc.text = if (bothEnabled) {
                getString(R.string.setting_open_last_desc_both)
            } else {
                getString(R.string.setting_open_last_desc)
            }
        }

        switchAutoSave.setOnCheckedChangeListener { isChecked ->
            prefs.edit().putBoolean("auto_save", isChecked).apply()
        }

        switchWrapLines.setOnCheckedChangeListener { isChecked ->
            prefs.edit().putBoolean("wrap_lines", isChecked).apply()
        }

        switchWakeLock.setOnCheckedChangeListener { isChecked ->
            prefs.edit().putBoolean("wake_lock", isChecked).apply()
        }

        findViewById<LinearLayout>(R.id.settingSaveFolder).setOnClickListener {
            openFolderPicker()
        }

        findViewById<LinearLayout>(R.id.settingLanguage).setOnClickListener {
            showLanguageDialog()
        }

        findViewById<LinearLayout>(R.id.settingFont).setOnClickListener {
            showFontDialog()
        }

        findViewById<LinearLayout>(R.id.settingFontSize).setOnClickListener {
            showFontSizeDialog()
        }

        findViewById<LinearLayout>(R.id.settingAbout).setOnClickListener {
            showAboutDialog()
        }

        findViewById<LinearLayout>(R.id.settingDebugLog).setOnClickListener {
            startActivity(Intent(this, DebugLogActivity::class.java))
        }
    }

    private fun loadSettings() {
        switchOpenNew.isChecked = prefs.getBoolean("open_new", true)
        switchOpenLast.isChecked = prefs.getBoolean("open_last", false)
        switchAutoSave.isChecked = prefs.getBoolean("auto_save", false)
        switchWrapLines.isChecked = prefs.getBoolean("wrap_lines", true)
        switchWakeLock.isChecked = prefs.getBoolean("wake_lock", false)

        val language = prefs.getString("language", "English")
        textLanguageValue.text = language

        val font = prefs.getString("font", "Monospace")
        textFontValue.text = font
        textFontValue.typeface = when (font) {
            "Serif" -> android.graphics.Typeface.SERIF
            "Sans Serif" -> android.graphics.Typeface.SANS_SERIF
            else -> android.graphics.Typeface.MONOSPACE
        }

        val fontSize = prefs.getString("font_size", "M")
        textFontSizeValue.text = fontSize

        updateOpenNewDescription(switchOpenNew.isChecked, switchOpenLast.isChecked)
        updateOpenLastDescription(switchOpenNew.isChecked, switchOpenLast.isChecked)
        loadSaveFolderPath()
    }

    private fun loadSaveFolderPath() {
        val customFolder = prefs.getString("default_save_folder_uri", null)
        if (customFolder != null) {
            val folderName = getFolderDisplayName(Uri.parse(customFolder))
            textSaveFolderValue.text = folderName
        } else {
            textSaveFolderValue.text = getString(R.string.setting_save_folder_default)
        }
    }

    private fun getFolderDisplayName(uri: Uri): String {
        val encodedPath = uri.encodedPath ?: return "Custom Folder"
        
        val decodedPath = encodedPath
            .substringAfter("tree/", "")
            .replace("%3A", "/")
            .replace("%2F", "/")
            .replace("%20", " ")
        
        if (decodedPath.startsWith("primary/")) {
            return "Internal Storage/" + decodedPath.substringAfter("primary/")
        }
        
        return decodedPath
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_SAVE_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SAVE_FOLDER && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                prefs.edit().putString("default_save_folder_uri", uri.toString()).apply()
                textSaveFolderValue.text = getFolderDisplayName(uri)
                Toast.makeText(this, R.string.setting_save_folder_changed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOpenNewDescription(openNewEnabled: Boolean, openLastEnabled: Boolean) {
        textOpenNewDesc.text = if (openNewEnabled && openLastEnabled) {
            getString(R.string.setting_open_new_desc_both)
        } else {
            getString(R.string.setting_open_new_desc)
        }
    }

    private fun updateOpenLastDescription(openNewEnabled: Boolean, openLastEnabled: Boolean) {
        textOpenLastDesc.text = if (openNewEnabled && openLastEnabled) {
            getString(R.string.setting_open_last_desc_both)
        } else {
            getString(R.string.setting_open_last_desc)
        }
    }

    private fun showLanguageDialog() {
        val currentLanguage = prefs.getString("language", "English")
        val currentIndex = languages.indexOf(currentLanguage).coerceAtLeast(0)

        CustomOptionDialog(this)
            .setTitle(getString(R.string.choose_language))
            .setOptions(languages, currentIndex)
            .setOnOkClickListener { index ->
                val selected = languages[index]
                prefs.edit().putString("language", selected).apply()
                textLanguageValue.text = selected
                Toast.makeText(this, getString(R.string.language_changed, selected), Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setOnCancelClickListener { }
            .show()
    }

    private fun showFontDialog() {
        val currentFont = prefs.getString("font", "Monospace")
        val currentIndex = fonts.indexOf(currentFont).coerceAtLeast(0)

        CustomOptionDialog(this)
            .setTitle(getString(R.string.choose_font))
            .setOptions(fonts, currentIndex)
            .setOnOkClickListener { index ->
                val selected = fonts[index]
                prefs.edit().putString("font", selected).apply()
                textFontValue.text = selected
                textFontValue.typeface = when (selected) {
                    "Serif" -> android.graphics.Typeface.SERIF
                    "Sans Serif" -> android.graphics.Typeface.SANS_SERIF
                    else -> android.graphics.Typeface.MONOSPACE
                }
            }
            .setOnCancelClickListener { }
            .show()
    }

    private fun showFontSizeDialog() {
        val currentSize = prefs.getString("font_size", "M")
        val currentIndex = fontSizes.indexOf(currentSize).coerceAtLeast(0)

        CustomOptionDialog(this)
            .setTitle(getString(R.string.choose_font_size))
            .setOptions(fontSizes, currentIndex)
            .setOnOkClickListener { index ->
                val selected = fontSizes[index]
                prefs.edit().putString("font_size", selected).apply()
                textFontSizeValue.text = selected
            }
            .setOnCancelClickListener { }
            .show()
    }

    private fun showAboutDialog() {
        val versionName = BuildConfig.VERSION_NAME
        CustomAboutDialog(this)
            .setVersion(versionName)
            .show()
    }

    companion object {
        private const val REQUEST_SAVE_FOLDER = 200
        
        fun getPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences("koda_prefs", Context.MODE_PRIVATE)
        }
    }
}