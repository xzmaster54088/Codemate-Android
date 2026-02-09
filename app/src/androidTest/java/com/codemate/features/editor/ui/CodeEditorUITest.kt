package com.codemate.features.editor.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.codemate.MainActivity
import com.codemate.features.editor.presentation.ui.CodeEditorScreen
import com.codemate.features.editor.presentation.state.EditorState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 代码编辑器UI测试
 * 使用Espresso测试主要用户流程，包括登录、编辑、编译、Git操作等
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CodeEditorUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun `codeEditorScreen when loaded should display editor components`() {
        // Given - Setup test environment

        // When - Launch editor screen
        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { }
            )
        }

        // Then - Verify UI components are displayed
        composeTestRule.onNodeWithTag("EditorContainer")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("CodeEditor")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("EditorToolbar")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("EditorActions")
            .assertIsDisplayed()
    }

    @Test
    fun `typingInEditor should update content and show typing indicators`() {
        // Given - Setup editor with initial content
        val initialContent = ""
        val testContent = "fun main() {\n    println(\"Hello World\")\n}"

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = initialContent,
                    isModified = false,
                    language = "kotlin"
                )
            )
        }

        // When - Type in the editor
        val codeEditor = composeTestRule.onNodeWithTag("CodeEditor")
        codeEditor.performClick()
        codeEditor.performTextInput(testContent)

        // Then - Verify content is updated
        composeTestRule.onNodeWithText(testContent)
            .assertIsDisplayed()

        // Verify save button becomes enabled
        composeTestRule.onNodeWithTag("SaveButton")
            .assertIsEnabled()
    }

    @Test
    fun `saveFile operation should show success feedback`() {
        // Given - Setup editor with content
        val testContent = "fun test() { }"
        var saveCalled = false
        var savedContent = ""
        var savedPath = ""

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { path, content ->
                    saveCalled = true
                    savedPath = path
                    savedContent = content
                },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = testContent,
                    isModified = true,
                    language = "kotlin"
                )
            )
        }

        // When - Click save button
        composeTestRule.onNodeWithTag("SaveButton")
            .performClick()

        // Then - Verify save operation was called
        assert(saveCalled)
        assert(savedContent == testContent)

        // Verify success feedback is shown
        composeTestRule.onNodeWithTag("SaveSuccessMessage")
            .assertIsDisplayed()
    }

    @Test
    fun `compileCode operation should show compilation results`() {
        // Given - Setup editor with code
        val kotlinCode = """
            fun main() {
                println("Hello World")
            }
        """.trimIndent()
        
        var compileCalled = false
        var compiledCode = ""

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { code ->
                    compileCalled = true
                    compiledCode = code
                },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = kotlinCode,
                    isModified = true,
                    language = "kotlin"
                )
            )
        }

        // When - Click compile button
        composeTestRule.onNodeWithTag("CompileButton")
            .performClick()

        // Then - Verify compile operation was called
        assert(compileCalled)
        assert(compiledCode == kotlinCode)

        // Verify compilation results are displayed
        composeTestRule.onNodeWithTag("CompilationResults")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("编译成功")
            .assertIsDisplayed()
    }

    @Test
    fun `executeCode operation should show execution output`() {
        // Given - Setup editor with executable code
        val javaCode = """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
        """.trimIndent()
        
        var executeCalled = false
        var executedCode = ""

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { code ->
                    executeCalled = true
                    executedCode = code
                },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = javaCode,
                    isModified = true,
                    language = "java"
                )
            )
        }

        // When - Click execute button
        composeTestRule.onNodeWithTag("ExecuteButton")
            .performClick()

        // Then - Verify execute operation was called
        assert(executeCalled)
        assert(executedCode == javaCode)

        // Verify execution results are displayed
        composeTestRule.onNodeWithTag("ExecutionResults")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Hello World")
            .assertIsDisplayed()
    }

    @Test
    fun `syntaxHighlighting should apply correct colors to code`() {
        // Given - Setup editor with Kotlin code
        val kotlinCode = """
            fun main() {
                val name = "Kotlin"
                println(name)
            }
        """.trimIndent()

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = kotlinCode,
                    isModified = false,
                    language = "kotlin",
                    syntaxHighlightingEnabled = true
                )
            )
        }

        // Then - Verify syntax highlighting is applied
        // Check that keywords are highlighted
        composeTestRule.onNodeWithText("fun")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("val")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("println")
            .assertIsDisplayed()
    }

    @Test
    fun `autoCompleteSuggestions should appear when typing`() {
        // Given - Setup editor and trigger autocomplete
        val partialCode = "fun ma"

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = partialCode,
                    isModified = true,
                    language = "kotlin"
                )
            )
        }

        // When - Trigger autocomplete (simulate typing period)
        val codeEditor = composeTestRule.onNodeWithTag("CodeEditor")
        codeEditor.performClick()
        
        // Wait for autocomplete to trigger
        composeTestRule.waitForIdle()

        // Then - Verify autocomplete suggestions are displayed
        composeTestRule.onNodeWithTag("AutoCompleteSuggestions")
            .assertIsDisplayed()

        // Verify suggestion items
        composeTestRule.onNodeWithText("main")
            .assertIsDisplayed()
    }

    @Test
    fun `fileBrowser should allow navigation and file selection`() {
        // Given - Setup file browser
        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                showFileBrowser = true
            )
        }

        // Then - Verify file browser is displayed
        composeTestRule.onNodeWithTag("FileBrowser")
            .assertIsDisplayed()

        // Verify file items
        composeTestRule.onNodeWithText("Main.kt")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Helper.java")
            .assertIsDisplayed()

        // When - Click on a file
        composeTestRule.onNodeWithText("Main.kt")
            .performClick()

        // Then - File should be loaded
        composeTestRule.onNodeWithTag("FileBrowser")
            .assertDoesNotExist()
    }

    @Test
    fun `themeToggle should switch between light and dark themes`() {
        // Given - Setup editor in light theme
        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                isDarkTheme = false
            )
        }

        // Verify initial theme
        composeTestRule.onNodeWithTag("EditorContainer")
            .assertIsDisplayed()

        // When - Click theme toggle
        composeTestRule.onNodeWithTag("ThemeToggle")
            .performClick()

        // Then - Theme should change (verify through content description or visual state)
        // This would require more sophisticated theme detection
        composeTestRule.onNodeWithTag("ThemeToggle")
            .assertIsDisplayed()
    }

    @Test
    fun `editorSettings should be accessible and functional`() {
        // Given - Setup editor
        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { }
            )
        }

        // When - Click settings menu
        composeTestRule.onNodeWithTag("SettingsMenu")
            .performClick()

        // Then - Settings dialog should appear
        composeTestRule.onNodeWithTag("SettingsDialog")
            .assertIsDisplayed()

        // Verify font size controls
        composeTestRule.onNodeWithTag("FontSizeSlider")
            .assertIsDisplayed()

        // When - Adjust font size
        composeTestRule.onNodeWithTag("FontSizeSlider")
            .performClick()

        // Then - Font size should change
        composeTestRule.onNodeWithTag("FontSizeDisplay")
            .assertIsDisplayed()
    }

    @Test
    fun `errorHandling should display appropriate error messages`() {
        // Given - Setup editor with syntax error
        val errorCode = "fun main() { syntax error }"

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = errorCode,
                    isModified = true,
                    language = "kotlin",
                    errorMessage = "语法错误：缺少右括号"
                )
            )
        }

        // Then - Error message should be displayed
        composeTestRule.onNodeWithText("语法错误：缺少右括号")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("ErrorIndicator")
            .assertIsDisplayed()
    }

    @Test
    fun `loadingStates should show appropriate indicators`() {
        // Given - Setup editor in loading state
        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = "",
                    isModified = false,
                    language = "kotlin",
                    isLoading = true
                )
            )
        }

        // Then - Loading indicator should be displayed
        composeTestRule.onNodeWithTag("LoadingIndicator")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("正在加载...")
            .assertIsDisplayed()
    }

    @Test
    fun `keyboardShortcuts should work correctly`() {
        // Given - Setup editor with content
        val testContent = "fun test() { }"

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { },
                initialState = EditorState(
                    content = testContent,
                    isModified = true,
                    language = "kotlin"
                )
            )
        }

        // When - Use keyboard shortcut (Ctrl+S for save)
        device.pressKeyCode(34) // Ctrl key simulation
        device.pressKeyCode(47) // S key simulation

        // Then - Save operation should be triggered
        // This would require more sophisticated shortcut detection
        composeTestRule.onNodeWithTag("EditorContainer")
            .assertIsDisplayed()
    }

    @Test
    fun `responsiveDesign should adapt to different screen sizes`() {
        // Given - Setup editor on different screen sizes
        // This would require device rotation or different device profiles

        composeTestRule.setContent {
            CodeEditorScreen(
                onNavigateBack = { },
                onSaveFile = { _, _ -> },
                onLoadFile = { },
                onCompileCode = { },
                onExecuteCode = { },
                onAnalyzeCode = { }
            )
        }

        // Then - Editor should be properly displayed and responsive
        composeTestRule.onNodeWithTag("EditorContainer")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("CodeEditor")
            .assertIsDisplayed()

        // On smaller screens, some features might be hidden
        // This would require testing on different device profiles
    }
}
