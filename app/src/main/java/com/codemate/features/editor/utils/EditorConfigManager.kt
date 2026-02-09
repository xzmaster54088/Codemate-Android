package com.codemate.features.editor.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.codemate.features.editor.data.*

/**
 * 编辑器配置管理器
 * 负责管理编辑器的各种配置设置
 */
class EditorConfigManager(private val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "codemate_editor_config", Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_LANGUAGE = "editor_language"
        private const val KEY_THEME = "editor_theme"
        private const val KEY_FONT_SIZE = "editor_font_size"
        private const val KEY_FONT_FAMILY = "editor_font_family"
        private const val KEY_AUTO_INDENT = "editor_auto_indent"
        private const val KEY_AUTO_COMPLETE = "editor_auto_complete"
        private const val KEY_SHOW_LINE_NUMBERS = "editor_show_line_numbers"
        private const val KEY_WORD_WRAP = "editor_word_wrap"
        private const val KEY_TAB_SIZE = "editor_tab_size"
        private const val KEY_USE_SPACES = "editor_use_spaces"
        private const val KEY_MINIMAP_ENABLED = "editor_minimap_enabled"
        private const val KEY_HIGHLIGHT_CURRENT_LINE = "editor_highlight_current_line"
        private const val KEY_VIRTUAL_SCROLLING = "editor_virtual_scrolling"
        private const val KEY_MAX_UNDO_SIZE = "editor_max_undo_size"
        private const val KEY_ENABLE_SYNTAX_HIGHLIGHT = "editor_enable_syntax_highlight"
        private const val KEY_ENABLE_CODE_ANALYSIS = "editor_enable_code_analysis"
    }
    
    /**
     * 获取编程语言设置
     */
    fun getLanguage(): EditorLanguage {
        val languageName = preferences.getString(KEY_LANGUAGE, EditorLanguage.KOTLIN.name)
        return EditorLanguage.values().find { it.name == languageName } ?: EditorLanguage.KOTLIN
    }
    
    /**
     * 设置编程语言
     */
    fun setLanguage(language: EditorLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
    }
    
    /**
     * 获取主题设置
     */
    fun getTheme(): EditorTheme {
        val themeName = preferences.getString(KEY_THEME, EditorTheme.LIGHT.name)
        return EditorTheme.values().find { it.name == themeName } ?: EditorTheme.LIGHT
    }
    
    /**
     * 设置主题
     */
    fun setTheme(theme: EditorTheme) {
        preferences.edit().putString(KEY_THEME, theme.name).apply()
    }
    
    /**
     * 获取字体大小
     */
    fun getFontSize(): Int {
        return preferences.getInt(KEY_FONT_SIZE, 14)
    }
    
    /**
     * 设置字体大小
     */
    fun setFontSize(fontSize: Int) {
        preferences.edit().putInt(KEY_FONT_SIZE, fontSize).apply()
    }
    
    /**
     * 获取字体族
     */
    fun getFontFamily(): EditorFont {
        val fontName = preferences.getString(KEY_FONT_FAMILY, EditorFont.DEFAULT.name)
        return EditorFont.values().find { it.name == fontName } ?: EditorFont.DEFAULT
    }
    
    /**
     * 设置字体族
     */
    fun setFontFamily(font: EditorFont) {
        preferences.edit().putString(KEY_FONT_FAMILY, font.name).apply()
    }
    
    /**
     * 获取编辑器设置
     */
    fun getEditorSettings(): EditorSettings {
        return EditorSettings(
            autoIndent = preferences.getBoolean(KEY_AUTO_INDENT, true),
            autoComplete = preferences.getBoolean(KEY_AUTO_COMPLETE, true),
            showLineNumbers = preferences.getBoolean(KEY_SHOW_LINE_NUMBERS, true),
            wordWrap = preferences.getBoolean(KEY_WORD_WRAP, true),
            tabSize = preferences.getInt(KEY_TAB_SIZE, 4),
            useSpaces = preferences.getBoolean(KEY_USE_SPACES, true),
            showMiniMap = preferences.getBoolean(KEY_MINIMAP_ENABLED, false),
            highlightCurrentLine = preferences.getBoolean(KEY_HIGHLIGHT_CURRENT_LINE, true),
            enableVirtualScrolling = preferences.getBoolean(KEY_VIRTUAL_SCROLLING, true),
            maxUndoStackSize = preferences.getInt(KEY_MAX_UNDO_SIZE, 100),
            fontSizeRange = 8..72
        )
    }
    
    /**
     * 设置编辑器设置
     */
    fun setEditorSettings(settings: EditorSettings) {
        preferences.edit().apply {
            putBoolean(KEY_AUTO_INDENT, settings.autoIndent)
            putBoolean(KEY_AUTO_COMPLETE, settings.autoComplete)
            putBoolean(KEY_SHOW_LINE_NUMBERS, settings.showLineNumbers)
            putBoolean(KEY_WORD_WRAP, settings.wordWrap)
            putInt(KEY_TAB_SIZE, settings.tabSize)
            putBoolean(KEY_USE_SPACES, settings.useSpaces)
            putBoolean(KEY_MINIMAP_ENABLED, settings.showMiniMap)
            putBoolean(KEY_HIGHLIGHT_CURRENT_LINE, settings.highlightCurrentLine)
            putBoolean(KEY_VIRTUAL_SCROLLING, settings.enableVirtualScrolling)
            putInt(KEY_MAX_UNDO_SIZE, settings.maxUndoStackSize)
            apply()
        }
    }
    
    /**
     * 启用/禁用语法高亮
     */
    fun setSyntaxHighlightEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLE_SYNTAX_HIGHLIGHT, enabled).apply()
    }
    
    /**
     * 是否启用语法高亮
     */
    fun isSyntaxHighlightEnabled(): Boolean {
        return preferences.getBoolean(KEY_ENABLE_SYNTAX_HIGHLIGHT, true)
    }
    
    /**
     * 启用/禁用代码分析
     */
    fun setCodeAnalysisEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLE_CODE_ANALYSIS, enabled).apply()
    }
    
    /**
     * 是否启用代码分析
     */
    fun isCodeAnalysisEnabled(): Boolean {
        return preferences.getBoolean(KEY_ENABLE_CODE_ANALYSIS, true)
    }
    
    /**
     * 重置为默认设置
     */
    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }
    
    /**
     * 导出配置
     */
    fun exportConfig(): Map<String, Any> {
        return mapOf(
            "language" to getLanguage().name,
            "theme" to getTheme().name,
            "fontSize" to getFontSize(),
            "fontFamily" to getFontFamily().name,
            "settings" to getEditorSettings(),
            "syntaxHighlightEnabled" to isSyntaxHighlightEnabled(),
            "codeAnalysisEnabled" to isCodeAnalysisEnabled()
        )
    }
    
    /**
     * 导入配置
     */
    fun importConfig(config: Map<String, Any>) {
        config["language"]?.let {
            EditorLanguage.values().find { it.name == it.toString() }?.let { language ->
                setLanguage(language)
            }
        }
        
        config["theme"]?.let {
            EditorTheme.values().find { it.name == it.toString() }?.let { theme ->
                setTheme(theme)
            }
        }
        
        config["fontSize"]?.let { setFontSize(it as Int) }
        
        config["fontFamily"]?.let {
            EditorFont.values().find { it.name == it.toString() }?.let { font ->
                setFontFamily(font)
            }
        }
        
        config["settings"]?.let { settings ->
            if (settings is EditorSettings) {
                setEditorSettings(settings)
            }
        }
        
        config["syntaxHighlightEnabled"]?.let { 
            setSyntaxHighlightEnabled(it as Boolean) 
        }
        
        config["codeAnalysisEnabled"]?.let { 
            setCodeAnalysisEnabled(it as Boolean) 
        }
    }
}

/**
 * 文件管理器
 * 负责文件的读写和保存
 */
class FileManager(private val context: Context) {
    
    /**
     * 保存文件
     */
    suspend fun saveFile(
        fileName: String,
        content: String,
        language: EditorLanguage
    ): Result<Boolean> {
        return try {
            val file = java.io.File(context.filesDir, fileName)
            file.writeText(content)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 加载文件
     */
    suspend fun loadFile(fileName: String): Result<Pair<String, EditorLanguage>> {
        return try {
            val file = java.io.File(context.filesDir, fileName)
            if (file.exists()) {
                val content = file.readText()
                val language = detectLanguageFromFile(fileName, content)
                Result.success(content to language)
            } else {
                Result.failure(Exception("File not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取文件列表
     */
    fun getFileList(): List<EditorFile> {
        val filesDir = context.filesDir
        val files = filesDir.listFiles() ?: return emptyList()
        
        return files.filter { it.isFile }.map { file ->
            EditorFile(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                language = detectLanguageFromFile(file.name, ""),
                content = "" // 延迟加载内容
            )
        }
    }
    
    /**
     * 删除文件
     */
    suspend fun deleteFile(fileName: String): Result<Boolean> {
        return try {
            val file = java.io.File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
                Result.success(true)
            } else {
                Result.failure(Exception("File not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 从文件内容检测语言
     */
    private fun detectLanguageFromFile(fileName: String, content: String): EditorLanguage {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
        return when (extension) {
            "kt" -> EditorLanguage.KOTLIN
            "java" -> EditorLanguage.JAVA
            "py" -> EditorLanguage.PYTHON
            "js" -> EditorLanguage.JAVASCRIPT
            "ts" -> EditorLanguage.TYPESCRIPT
            "cpp", "cc", "cxx" -> EditorLanguage.CPP
            "cs" -> EditorLanguage.CSHARP
            "go" -> EditorLanguage.GO
            "rs" -> EditorLanguage.RUST
            "swift" -> EditorLanguage.SWIFT
            "xml" -> EditorLanguage.XML
            "json" -> EditorLanguage.JSON
            "yaml", "yml" -> EditorLanguage.YAML
            "md" -> EditorLanguage.MARKDOWN
            else -> {
                // 根据内容检测语言
                detectLanguageFromContent(content)
            }
        }
    }
    
    /**
     * 根据内容检测语言
     */
    private fun detectLanguageFromContent(content: String): EditorLanguage {
        // 简单的语言检测逻辑
        return when {
            content.contains("fun ") && content.contains("class ") -> EditorLanguage.KOTLIN
            content.contains("public class") && content.contains("static void main") -> EditorLanguage.JAVA
            content.contains("def ") && content.contains(":") -> EditorLanguage.PYTHON
            content.contains("function ") && content.contains("var ") -> EditorLanguage.JAVASCRIPT
            content.contains("interface ") && content.contains("type ") -> EditorLanguage.TYPESCRIPT
            content.contains("#include") && content.contains("int main") -> EditorLanguage.CPP
            content.contains("namespace") && content.contains("using System") -> EditorLanguage.CSHARP
            content.contains("package main") && content.contains("func main") -> EditorLanguage.GO
            content.contains("fn ") && content.contains("println!") -> EditorLanguage.RUST
            content.contains("import ") && content.contains("func ") -> EditorLanguage.SWIFT
            content.contains("<") && content.contains(">") && content.contains("</") -> EditorLanguage.XML
            content.contains("{") && content.contains("}") && content.contains(":") -> EditorLanguage.JSON
            content.contains("---") && content.contains(":") -> EditorLanguage.YAML
            content.contains("# ") || content.contains("```") -> EditorLanguage.MARKDOWN
            else -> EditorLanguage.KOTLIN // 默认语言
        }
    }
}

/**
 * 编辑器文件数据类
 */
data class EditorFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val language: EditorLanguage,
    val content: String
)

/**
 * 编辑器主题管理器
 * 负责主题的定义和应用
 */
object EditorThemeManager {
    
    /**
     * 获取主题的动态颜色
     */
    fun getDynamicColors(theme: EditorTheme): DynamicColors {
        return when (theme) {
            EditorTheme.LIGHT -> DynamicColors(
                background = Color.White,
                surface = Color(0xFFF8F9FA),
                onSurface = Color.Black,
                primary = Color(0xFF1976D2),
                onPrimary = Color.White,
                secondary = Color(0xFF757575),
                onSecondary = Color.White,
                error = Color(0xFFD32F2F),
                onError = Color.White,
                outline = Color(0xFFBDBDBD)
            )
            EditorTheme.DARK -> DynamicColors(
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onSurface = Color.White,
                primary = Color(0xFF90CAF9),
                onPrimary = Color.Black,
                secondary = Color(0xFFB0BEC5),
                onSecondary = Color.Black,
                error = Color(0xFFFF5252),
                onError = Color.Black,
                outline = Color(0xFF616161)
            )
            EditorTheme.SOLARIZED_LIGHT -> DynamicColors(
                background = Color(0xFFFDF6E3),
                surface = Color(0xFFF5F5F5),
                onSurface = Color(0xFF657B83),
                primary = Color(0xFF268BD2),
                onPrimary = Color.White,
                secondary = Color(0xFF859900),
                onSecondary = Color.White,
                error = Color(0xFFDC322F),
                onError = Color.White,
                outline = Color(0xFFB58900)
            )
            EditorTheme.SOLARIZED_DARK -> DynamicColors(
                background = Color(0xFF002B36),
                surface = Color(0xFF073642),
                onSurface = Color(0xFF93A1A1),
                primary = Color(0xFF268BD2),
                onPrimary = Color.White,
                secondary = Color(0xFF859900),
                onSecondary = Color.White,
                error = Color(0xFFDC322F),
                onError = Color.White,
                outline = Color(0xFFB58900)
            )
            EditorTheme.MONOKAI -> DynamicColors(
                background = Color(0xFF272822),
                surface = Color(0xFF383830),
                onSurface = Color(0xFFF8F8F2),
                primary = Color(0xFFA6E22E),
                onPrimary = Color.Black,
                secondary = Color(0xFFF92672),
                onSecondary = Color.White,
                error = Color(0xFFF92672),
                onError = Color.White,
                outline = Color(0xFF75715E)
            )
            EditorTheme.GITHUB -> DynamicColors(
                background = Color.White,
                surface = Color(0xFFF6F8FA),
                onSurface = Color(0xFF24292E),
                primary = Color(0xFF0366D6),
                onPrimary = Color.White,
                secondary = Color(0xFF6F42C1),
                onSecondary = Color.White,
                error = Color(0xFFD73A49),
                onError = Color.White,
                outline = Color(0xFFE1E4E8)
            )
        }
    }
}

/**
 * 动态颜色数据类
 */
data class DynamicColors(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val error: Color,
    val onError: Color,
    val outline: Color
)

/**
 * 编辑器快捷键管理器
 */
object EditorShortcuts {
    
    // 定义常用快捷键
    val shortcuts = mapOf(
        "Ctrl+Z" to "undo",
        "Ctrl+Y" to "redo",
        "Ctrl+F" to "find",
        "Ctrl+H" to "replace",
        "Ctrl+A" to "select_all",
        "Ctrl+C" to "copy",
        "Ctrl+V" to "paste",
        "Ctrl+X" to "cut",
        "Ctrl+S" to "save",
        "Ctrl+O" to "open",
        "Ctrl+N" to "new_file",
        "Ctrl++" to "zoom_in",
        "Ctrl+-" to "zoom_out",
        "F11" to "toggle_fullscreen",
        "Escape" to "close_dialog"
    )
    
    /**
     * 执行快捷键操作
     */
    fun executeShortcut(
        shortcut: String,
        onAction: (String) -> Unit
    ) {
        val action = shortcuts[shortcut]
        action?.let { onAction(it) }
    }
    
    /**
     * 获取快捷键描述
     */
    fun getShortcutDescription(action: String): String {
        return shortcuts.entries.find { it.value == action }?.key ?: ""
    }
    
    /**
     * 检查是否为编辑器快捷键
     */
    fun isEditorShortcut(keyEvent: android.view.KeyEvent): Boolean {
        val ctrlPressed = keyEvent.isCtrlPressed
        val altPressed = keyEvent.isAltPressed
        val shiftPressed = keyEvent.isShiftPressed
        
        when (keyEvent.keyCode) {
            android.view.KeyEvent.KEYCODE_Z -> if (ctrlPressed && !shiftPressed) return true
            android.view.KeyEvent.KEYCODE_Y -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_F -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_H -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_A -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_C -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_V -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_X -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_S -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_O -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_N -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_EQUALS -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_MINUS -> if (ctrlPressed) return true
            android.view.KeyEvent.KEYCODE_F11 -> return true
            android.view.KeyEvent.KEYCODE_ESCAPE -> return true
        }
        
        return false
    }
}

/**
 * 编辑器插件接口
 */
interface EditorPlugin {
    val name: String
    val version: String
    val description: String
    
    /**
     * 初始化插件
     */
    fun initialize(context: Context)
    
    /**
     * 清理资源
     */
    fun dispose()
    
    /**
     * 处理编辑器事件
     */
    fun onEditorEvent(event: EditorEvent)
}

/**
 * 编辑器事件
 */
sealed class EditorEvent {
    data class CodeChanged(val oldCode: String, val newCode: String) : EditorEvent()
    data class CursorMoved(val oldPosition: Int, val newPosition: Int) : EditorEvent()
    data class SelectionChanged(val oldSelection: IntRange, val newSelection: IntRange) : EditorEvent()
    data class LanguageChanged(val oldLanguage: EditorLanguage, val newLanguage: EditorLanguage) : EditorEvent()
    data class ThemeChanged(val oldTheme: EditorTheme, val newTheme: EditorTheme) : EditorEvent()
    object FileSaved : EditorEvent()
    object FileLoaded : EditorEvent()
}

/**
 * 插件管理器
 */
class PluginManager {
    private val plugins = mutableListOf<EditorPlugin>()
    
    /**
     * 注册插件
     */
    fun registerPlugin(plugin: EditorPlugin) {
        plugins.add(plugin)
    }
    
    /**
     * 初始化所有插件
     */
    fun initializePlugins(context: Context) {
        plugins.forEach { plugin ->
            try {
                plugin.initialize(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 清理所有插件
     */
    fun disposePlugins() {
        plugins.forEach { plugin ->
            try {
                plugin.dispose()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        plugins.clear()
    }
    
    /**
     * 发送事件到所有插件
     */
    fun sendEvent(event: EditorEvent) {
        plugins.forEach { plugin ->
            try {
                plugin.onEditorEvent(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}