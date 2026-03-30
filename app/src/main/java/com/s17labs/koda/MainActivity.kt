package com.s17labs.koda

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.s17labs.koda.model.KodaMenuItem
import com.s17labs.koda.model.OpenFile
import java.io.File
import java.io.FileWriter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var textEditor: EditText
    private lateinit var editorContainer: LinearLayout
    private lateinit var textEmptyState: TextView
    private lateinit var startPage: View
    private lateinit var tabContainer: LinearLayout
    private lateinit var tabScrollView: HorizontalScrollView
    private lateinit var toolbarTitle: TextView
    private lateinit var prefs: android.content.SharedPreferences

    private val openFiles = mutableListOf<OpenFile>()
    private var currentFile: OpenFile? = null
    private var savedLanguage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("koda_prefs", Context.MODE_PRIVATE)
        
        savedLanguage = prefs.getString("language", "English") ?: "English"
        applyLocale()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        applySettings()
        
        setupViews()
        setupEditor()
        setupTabs()
        setupMenu()
        
        val openedFromIntent = intent?.data != null
        if (openedFromIntent) {
            intent?.data?.let { uri ->
                DebugLog.intent("Opening file from intent", uri.toString())
                openFileFromUri(uri)
            } ?: run {
                DebugLog.w("Intent received but no data (uri is null)")
            }
        } else if (intent?.action != null) {
            DebugLog.w("Intent received but no data: action=${intent?.action}")
            updateTabBarVisibility()
        }
        
        val openLastEnabled = prefs.getBoolean("open_last", false)
        val openNewEnabled = prefs.getBoolean("open_new", true)
        DebugLog.i("onCreate: openLastEnabled=$openLastEnabled, openNewEnabled=$openNewEnabled, openedFromIntent=$openedFromIntent")

        if (!openedFromIntent) {
            var newFile: OpenFile? = null

            if (openNewEnabled) {
                newFile = OpenFile()
                openFiles.add(newFile)
                addTab(newFile)
            }

            if (openLastEnabled) {
                val lastFileData = prefs.getString("last_file", null)
                DebugLog.i("Auto open last, data: ${lastFileData?.take(50)}")
                if (lastFileData != null) {
                    val lastFile = OpenFile.fromJson(lastFileData)
                    DebugLog.i("Auto parsed lastFile: name=${lastFile?.name}, contentLength=${lastFile?.content?.length}")
                    if (lastFile != null) {
                        openFiles.add(lastFile)
                        addTab(lastFile)
                        if (!openNewEnabled) {
                            currentFile = lastFile
                            textEditor.setText(lastFile.content)
                            DebugLog.i("Set editor text, length=${lastFile.content.length}")
                        }
                    } else {
                        DebugLog.i("lastFile was null after parsing")
                    }
                } else {
                    DebugLog.i("lastFileData was null")
                }
            }

            if (openNewEnabled && newFile != null) {
                switchToFile(newFile)
                DebugLog.i("switchToFile newFile")
            }

            if (openNewEnabled || openLastEnabled) {
                updateTabBarVisibility()
                showStartPage()
                return
            }
        }

        updateTabBarVisibility()
        showStartPage()
    }

    override fun onResume() {
        super.onResume()
        
        val currentLang = prefs.getString("language", "English") ?: "English"
        if (savedLanguage != currentLang) {
            savedLanguage = currentLang
            recreate()
            return
        }
        
        applySettings()
        applyEditorSettings()
        showStartPage()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            DebugLog.intent("Opening file from new intent (app running)", uri.toString())
            openFileFromUri(uri)
        } ?: run {
            DebugLog.w("New intent received but no data: action=${intent.action}")
        }
    }

    private fun applySettings() {
        if (prefs.getBoolean("wake_lock", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyLocale() {
        val language = prefs.getString("language", "English")
        val locale = when (language) {
            "Spanish" -> Locale("es")
            "Chinese" -> Locale("zh")
            "Hindi" -> Locale("hi")
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

    private fun applyEditorSettings() {
        val fontType = when (prefs.getString("font", "Monospace")) {
            "Serif" -> Typeface.SERIF
            "Sans Serif" -> Typeface.SANS_SERIF
            else -> Typeface.MONOSPACE
        }
        
        val fontSize = prefs.getString("font_size", "M")
        val fontSizeSp = when (fontSize) {
            "XS" -> 10f
            "S" -> 12f
            "M" -> 14f
            "ML" -> 16f
            "L" -> 18f
            "XL" -> 22f
            else -> 14f
        }
        
        textEditor.typeface = fontType
        textEditor.textSize = fontSizeSp
        
        val wrapLines = prefs.getBoolean("wrap_lines", true)
        textEditor.setHorizontallyScrolling(!wrapLines)
    }

    override fun onPause() {
        super.onPause()
        if (prefs.getBoolean("auto_save", false)) {
            currentFile?.let { file ->
                file.content = textEditor.text.toString()
                saveCurrentFile(true)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        saveLastFile()
    }

    private fun saveLastFile() {
        currentFile?.let { file ->
            if (!file.isNew && file.path != null) {
                file.content = textEditor.text.toString()
                prefs.edit().putString("last_file", file.toJson()).commit()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new -> {
                newFile()
                true
            }
            R.id.action_open -> {
                openFile()
                true
            }
            R.id.action_save -> {
                saveCurrentFile()
                true
            }
            R.id.action_save_as -> {
                saveFileAs()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_exit -> {
                handleExit()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupViews() {
        textEditor = findViewById(R.id.textEditor)
        editorContainer = findViewById(R.id.editorContainer)
        textEmptyState = findViewById(R.id.textEmptyState)
        startPage = findViewById(R.id.startPage)
        tabContainer = findViewById(R.id.tabContainer)
        tabScrollView = findViewById(R.id.tabScrollView)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        
        setupStartPage()
        applyEditorSettings()
    }

    private fun setupStartPage() {
        startPage.findViewById<LinearLayout>(R.id.btnNewFile).setOnClickListener {
            hideStartPage()
            newFile()
            updateTabBarVisibility()
        }
        
        startPage.findViewById<LinearLayout>(R.id.btnOpenFile).setOnClickListener {
            hideStartPage()
            openFile()
        }
        
        startPage.findViewById<LinearLayout>(R.id.btnOpenMultiple).setOnClickListener {
            hideStartPage()
            openMultipleFiles()
        }
        
        startPage.findViewById<LinearLayout>(R.id.btnOpenLast).setOnClickListener {
            if (!prefs.getBoolean("open_last", false)) {
                return@setOnClickListener
            }
            val lastFileData = prefs.getString("last_file", null)
            DebugLog.i("Open Last clicked, data: ${lastFileData?.take(50)}")
            if (lastFileData != null) {
                val lastFile = OpenFile.fromJson(lastFileData)
                DebugLog.i("Parsed lastFile: name=${lastFile?.name}, contentLength=${lastFile?.content?.length}")
                if (lastFile != null) {
                    hideStartPage()
                    openFiles.add(lastFile)
                    addTab(lastFile)
                    switchToFile(lastFile)
                    updateTabBarVisibility()
                }
            }
        }
    }

    private fun hideStartPage() {
        startPage.visibility = View.GONE
        toolbarTitle.visibility = View.VISIBLE
    }

    private fun setupEditor() {
        textEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentFile?.let { file ->
                    val newContent = s?.toString() ?: ""
                    file.content = newContent
                    file.isModified = newContent != file.originalContent
                    updateTabTitle(file)
                }
            }
        })
    }

    private fun setupTabs() {
    }

    private fun showCustomMenu(anchorView: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_menu, null)
        val container = popupView.findViewById<LinearLayout>(R.id.customMenuContainer)

        val menuItems = listOf(
            KodaMenuItem(1, getString(R.string.menu_new), R.drawable.ic_menu_new) { 
                hideStartPage()
                newFile() 
            },
            KodaMenuItem(2, getString(R.string.menu_open), R.drawable.ic_menu_folder) { 
                hideStartPage()
                openFile() 
            },
            KodaMenuItem(7, getString(R.string.menu_open_multiple), R.drawable.ic_menu_folder) { 
                hideStartPage()
                openMultipleFiles() 
            },
            KodaMenuItem(3, getString(R.string.menu_save), R.drawable.ic_menu_save) { saveCurrentFile() },
            KodaMenuItem(4, getString(R.string.menu_save_as), R.drawable.ic_menu_save_as) { saveFileAs() },
            KodaMenuItem(5, getString(R.string.menu_settings), R.drawable.ic_menu_settings) { startActivity(Intent(this, SettingsActivity::class.java)) },
            KodaMenuItem(6, getString(R.string.menu_exit), R.drawable.ic_menu_exit) { handleExit() }
        )

        for (item in menuItems) {
            val itemView = layoutInflater.inflate(R.layout.menu_item, container, false)
            itemView.findViewById<ImageView>(R.id.menuIcon).setImageResource(item.icon)
            itemView.findViewById<TextView>(R.id.menuText).text = item.title
            itemView.setOnClickListener {
                customMenuPopupWindow?.dismiss()
                item.action()
            }
            container.addView(itemView)
        }

        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(null)
        popupWindow.isFocusable = true
        popupWindow.elevation = 8f
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        popupWindow.showAtLocation(anchorView, android.view.Gravity.TOP or android.view.Gravity.END, 16, anchorView.height + 8)
        customMenuPopupWindow = popupWindow
        popupWindow.setOnDismissListener { customMenuPopupWindow = null }
    }

    private var customMenuPopupWindow: PopupWindow? = null

    private fun setupMenu() {
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { view ->
            showCustomMenu(view)
        }
    }

    private fun newFile() {
        val newFile = OpenFile()
        openFiles.add(newFile)
        addTab(newFile)
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_OPEN_FILE)
    }

    private fun openMultipleFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_OPEN_MULTIPLE_FILES)
    }

    private fun openFileFromUri(uri: Uri) {
        val existing = openFiles.find { it.path == uri.toString() }
        if (existing != null) {
            switchToFile(existing)
            return
        }
        
        val untitledFile = openFiles.find { it.isNew }
        if (untitledFile != null) {
            try {
                tryTakePersistablePermission(uri)
                
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                        
                        contentResolver.openInputStream(uri)?.use { input ->
                            val content = input.bufferedReader().readText()
                            untitledFile.name = name
                            untitledFile.path = uri.toString()
                            untitledFile.content = content
                            untitledFile.originalContent = content
                            untitledFile.isNew = false
                            untitledFile.isModified = false
                            switchToFile(untitledFile)
                            updateTabTitle(untitledFile)
                            prefs.edit().putString("last_file", untitledFile.toJson()).apply()
                            if (prefs.getBoolean("open_last", false)) {
                                startPage.findViewById<LinearLayout>(R.id.btnOpenLast).visibility = View.VISIBLE
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("Error opening file", e)
                Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }
            
        try {
            tryTakePersistablePermission(uri)
            
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    
                    contentResolver.openInputStream(uri)?.use { input ->
                        val content = input.bufferedReader().readText()
                        val openFile = OpenFile(
                            name = name,
                            path = uri.toString(),
                            content = content,
                            originalContent = content,
                            isNew = false,
                            isModified = false
                        )
                        openFiles.add(openFile)
                        switchToFile(openFile)
                        addTab(openFile)
                        prefs.edit().putString("last_file", openFile.toJson()).apply()
                        if (prefs.getBoolean("open_last", false)) {
                            startPage.findViewById<LinearLayout>(R.id.btnOpenLast).visibility = View.VISIBLE
                        }
                        DebugLog.fileOpen(uri.toString(), name)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.e("Error opening file", e)
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun tryTakePersistablePermission(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            DebugLog.w("Could not take persistable permission (may not be granted)")
        }
    }

    private fun saveCurrentFile(isAutoSave: Boolean = false) {
        val file = currentFile ?: return
        
        if (file.content.isEmpty() && !isAutoSave) {
            Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isAutoSave && !file.isModified) {
            return
        }
        
        if (file.isNew || file.path == null) {
            if (!isAutoSave) {
                saveFileAs()
            }
        } else {
            writeToFile(file, true)
        }
    }

    private fun saveFileAs() {
        val file = currentFile ?: return
        
        if (file.content.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivityForResult(intent, REQUEST_SAVE_FILE)
    }

    private fun writeToFile(openFile: OpenFile, showToast: Boolean = true) {
        try {
            val path = openFile.path ?: return
            
            if (path.startsWith("content://")) {
                contentResolver.openOutputStream(Uri.parse(path))?.use { output ->
                    output.write(openFile.content.toByteArray())
                }
            } else {
                val f = File(path)
                FileWriter(f).use { it.write(openFile.content) }
            }
            openFile.isModified = false
            openFile.isNew = false
            openFile.originalContent = openFile.content
            updateTabTitle(openFile)
            DebugLog.fileSave(openFile.name, path)
            if (showToast) {
                Toast.makeText(this, R.string.file_saved, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            DebugLog.e("Error saving file", e)
            Toast.makeText(this, getString(R.string.error_saving, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchToFile(file: OpenFile) {
        currentFile = file
        textEditor.setText(file.content)
        
        for (i in 0 until tabContainer.childCount) {
            val tab = tabContainer.getChildAt(i)
            val isActive = tab.tag == file.id
            tab.setBackgroundColor(
                ContextCompat.getColor(this, if (isActive) R.color.kodaTabActive else R.color.kodaTabInactive)
            )
            if (isActive) {
                tab.post {
                    tabScrollView.smoothScrollTo(tab.left, 0)
                }
            }
        }
    }

    private fun addTab(file: OpenFile) {
        val tab = layoutInflater.inflate(R.layout.item_tab, tabContainer, false)
        tab.tag = file.id
        
        val title = tab.findViewById<TextView>(R.id.tabTitle)
        title.text = if (file.isModified) "${file.name}*" else file.name
        
        val closeBtn = tab.findViewById<ImageButton>(R.id.tabClose)
        closeBtn.setOnClickListener { closeTab(file) }
        
        tab.setOnClickListener { switchToFile(file) }
        tabContainer.addView(tab)
        
        switchToFile(file)
        updateTabBarVisibility()
        
        tabScrollView.post {
            tabScrollView.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun closeTab(file: OpenFile) {
        if (file.isModified) {
            val dialog = android.app.AlertDialog.Builder(this)
            dialog.setTitle(R.string.unsaved_changes)
            dialog.setMessage(getString(R.string.save_changes, file.name))
            dialog.setPositiveButton(R.string.save) { _, _ ->
                if (file.isNew || file.path == null) {
                    saveFileAsForTab(file)
                } else {
                    writeToFile(file)
                }
                removeTab(file)
            }
            dialog.setNegativeButton(R.string.dont_save) { _, _ -> removeTab(file) }
            dialog.setNeutralButton(R.string.cancel, null)
            dialog.show()
        } else {
            removeTab(file)
        }
    }

    private fun saveFileAsForTab(file: OpenFile) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        pendingFileForSave = file
        startActivityForResult(intent, REQUEST_SAVE_FILE_TAB)
    }

    private var pendingFileForSave: OpenFile? = null

    private fun removeTab(file: OpenFile) {
        val index = openFiles.indexOf(file)
        openFiles.remove(file)
        
        val tabIndex = (0 until tabContainer.childCount).find { tabContainer.getChildAt(it).tag == file.id }
        tabIndex?.let { tabContainer.removeViewAt(it) }
        
        when {
            openFiles.isEmpty() -> {
                currentFile = null
                textEditor.setText("")
            }
            currentFile == file -> switchToFile(openFiles[minOf(index, openFiles.size - 1)])
        }
        updateTabBarVisibility()
        showStartPage()
    }

    private fun updateTabTitle(file: OpenFile) {
        for (i in 0 until tabContainer.childCount) {
            val tab = tabContainer.getChildAt(i)
            if (tab.tag == file.id) {
                val title = tab.findViewById<TextView>(R.id.tabTitle)
                title.text = if (file.isModified) "${file.name}*" else file.name
                break
            }
        }
    }

    private fun updateTabBarVisibility() {
        val hasFiles = openFiles.isNotEmpty()
        tabScrollView.visibility = if (hasFiles) View.VISIBLE else View.GONE
        editorContainer.visibility = if (hasFiles) View.VISIBLE else View.GONE
        textEmptyState.visibility = if (hasFiles) View.GONE else View.VISIBLE
    }

    private fun showStartPage() {
        if (openFiles.isNotEmpty()) {
            startPage.visibility = View.GONE
            toolbarTitle.visibility = View.VISIBLE
            return
        }
        val openLast = prefs.getBoolean("open_last", false)
        val hasLastFile = prefs.getString("last_file", null) != null

        startPage.visibility = View.VISIBLE
        textEmptyState.visibility = View.GONE
        toolbarTitle.visibility = View.GONE

        startPage.findViewById<LinearLayout>(R.id.btnOpenLast).visibility = if (openLast && hasLastFile) View.VISIBLE else View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SAVE_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                currentFile?.let { file ->
                    try {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(file.content.toByteArray())
                        }
                        file.isModified = false
                        file.isNew = false
                        file.originalContent = file.content
                        updateTabTitle(file)
                        Toast.makeText(this, R.string.file_saved, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        DebugLog.e("Error saving file (tab)", e)
                        Toast.makeText(this, getString(R.string.error_saving, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        if (requestCode == REQUEST_SAVE_FILE_TAB && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                pendingFileForSave?.let { file ->
                    try {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(file.content.toByteArray())
                        }
                        file.isModified = false
                        file.isNew = false
                        file.originalContent = file.content
                        updateTabTitle(file)
                        Toast.makeText(this, R.string.file_saved, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        DebugLog.e("Error saving file (tab save as)", e)
                        Toast.makeText(this, getString(R.string.error_saving, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            pendingFileForSave = null
        }
        if (requestCode == REQUEST_OPEN_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                openFileFromUri(uri)
            }
        }
        if (requestCode == REQUEST_OPEN_MULTIPLE_FILES && resultCode == RESULT_OK) {
            hideStartPage()
            val clipData = data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    openFileFromUri(uri)
                }
            } else {
                data?.data?.let { uri ->
                    openFileFromUri(uri)
                }
            }
            updateTabBarVisibility()
        }
    }

    private fun handleExit() {
        if (openFiles.any { it.isModified }) {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_changes)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.save_and_exit) { _, _ ->
                    saveCurrentFile()
                    finish()
                }
                .setNegativeButton(R.string.exit_without_saving) { _, _ ->
                    finish()
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else {
            finish()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
    
    companion object {
        private const val REQUEST_SAVE_FILE = 100
        private const val REQUEST_SAVE_FILE_TAB = 103
        private const val REQUEST_OPEN_FILE = 102
        private const val REQUEST_OPEN_MULTIPLE_FILES = 104
    }
}
