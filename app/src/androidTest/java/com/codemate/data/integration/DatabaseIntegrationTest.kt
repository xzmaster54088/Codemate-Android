package com.codemate.data.integration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codemate.data.database.CodeMateDatabase
import com.codemate.data.dao.*
import com.codemate.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 数据库操作集成测试
 * 测试Room数据库的增删改查操作、事务处理和关联查询
 */
@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest {

    private lateinit var database: CodeMateDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var fileDao: FileDao
    private lateinit var codeSnippetDao: CodeSnippetDao
    private lateinit var conversationDao: ConversationDao
    private lateinit var aiMessageDao: AIMessageDao
    private lateinit var executorService: ExecutorService

    @Before
    fun setup() {
        // 使用内存数据库进行测试
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CodeMateDatabase::class.java
        ).build()
        
        projectDao = database.projectDao()
        fileDao = database.fileDao()
        codeSnippetDao = database.codeSnippetDao()
        conversationDao = database.conversationDao()
        aiMessageDao = database.aiMessageDao()
        
        executorService = Executors.newCachedThreadPool()
    }

    @After
    fun teardown() {
        database.close()
        executorService.shutdown()
    }

    @Test
    fun `project CRUD operations should work correctly`() = runBlocking {
        // Given
        val project = ProjectEntity(
            id = 1L,
            name = "Test Project",
            description = "A test project for database testing",
            language = "kotlin",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        // When & Then - Insert
        projectDao.insertProject(project)
        val insertedProject = projectDao.getProjectById(1L)
        assert(insertedProject != null)
        assert(insertedProject?.name == "Test Project")
        assert(insertedProject?.language == "kotlin")

        // When & Then - Update
        val updatedProject = project.copy(
            name = "Updated Test Project",
            lastModifiedAt = System.currentTimeMillis()
        )
        projectDao.updateProject(updatedProject)
        val projectAfterUpdate = projectDao.getProjectById(1L)
        assert(projectAfterUpdate?.name == "Updated Test Project")

        // When & Then - Delete
        projectDao.deleteProject(1L)
        val projectAfterDelete = projectDao.getProjectById(1L)
        assert(projectAfterDelete == null)
    }

    @Test
    fun `file operations with project association should work correctly`() = runBlocking {
        // Given
        val project = ProjectEntity(
            id = 1L,
            name = "Test Project",
            description = "A test project",
            language = "kotlin",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )
        projectDao.insertProject(project)

        val file1 = FileEntity(
            id = 1L,
            projectId = 1L,
            name = "Main.kt",
            path = "/src/Main.kt",
            content = "fun main() { println(\"Hello\") }",
            fileType = "source",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        val file2 = FileEntity(
            id = 2L,
            projectId = 1L,
            name = "Helper.kt",
            path = "/src/Helper.kt",
            content = "fun helper() { }",
            fileType = "source",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        // When & Then - Insert files
        fileDao.insertFile(file1)
        fileDao.insertFile(file2)
        
        val files = fileDao.getFilesByProjectId(1L)
        assert(files.size == 2)
        assert(files.any { it.name == "Main.kt" })
        assert(files.any { it.name == "Helper.kt" })

        // When & Then - Update file content
        val updatedFile = file1.copy(
            content = "fun main() { println(\"Hello World\") }",
            lastModifiedAt = System.currentTimeMillis()
        )
        fileDao.updateFile(updatedFile)
        val updatedFileResult = fileDao.getFileById(1L)
        assert(updatedFileResult?.content?.contains("Hello World") == true)

        // When & Then - Search files
        val searchResults = fileDao.searchFiles("Helper", 1L)
        assert(searchResults.isNotEmpty())
        assert(searchResults.first().name == "Helper.kt")
    }

    @Test
    fun `code snippet operations should work correctly`() = runBlocking {
        // Given
        val snippet1 = CodeSnippetEntity(
            id = 1L,
            title = "Kotlin Hello World",
            description = "Simple Hello World example",
            language = "kotlin",
            code = """
                fun main() {
                    println("Hello, World!")
                }
            """.trimIndent(),
            tags = listOf("hello", "world", "kotlin"),
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis(),
            usageCount = 0
        )

        val snippet2 = CodeSnippetEntity(
            id = 2L,
            title = "Java Hello World",
            description = "Simple Hello World example",
            language = "java",
            code = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
            """.trimIndent(),
            tags = listOf("hello", "world", "java"),
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis(),
            usageCount = 0
        )

        // When & Then - Insert snippets
        codeSnippetDao.insertSnippet(snippet1)
        codeSnippetDao.insertSnippet(snippet2)

        // Test get all snippets
        val allSnippets = codeSnippetDao.getAllSnippets()
        assert(allSnippets.size == 2)

        // Test search by language
        val kotlinSnippets = codeSnippetDao.getSnippetsByLanguage("kotlin")
        assert(kotlinSnippets.size == 1)
        assert(kotlinSnippets.first().title == "Kotlin Hello World")

        // Test search by tag
        val helloSnippets = codeSnippetDao.getSnippetsByTag("hello")
        assert(helloSnippets.size == 2)

        // Test update usage count
        codeSnippetDao.incrementUsageCount(1L)
        val updatedSnippet = codeSnippetDao.getSnippetById(1L)
        assert(updatedSnippet?.usageCount == 1)
    }

    @Test
    fun `conversation and message operations should work correctly`() = runBlocking {
        // Given
        val conversation = ConversationEntity(
            id = 1L,
            title = "Kotlin Programming Help",
            createdAt = System.currentTimeMillis(),
            lastMessageAt = System.currentTimeMillis(),
            messageCount = 0
        )

        val message1 = AIMessageEntity(
            id = 1L,
            conversationId = 1L,
            content = "How do I create a data class in Kotlin?",
            isUser = true,
            timestamp = System.currentTimeMillis(),
            aiModel = "gpt-3.5-turbo"
        )

        val message2 = AIMessageEntity(
            id = 2L,
            conversationId = 1L,
            content = "You can create a data class like this:\n\n```kotlin\ndata class Person(val name: String, val age: Int)\n```",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            aiModel = "gpt-3.5-turbo"
        )

        // When & Then - Insert conversation
        conversationDao.insertConversation(conversation)
        val insertedConversation = conversationDao.getConversationById(1L)
        assert(insertedConversation != null)
        assert(insertedConversation?.title == "Kotlin Programming Help")

        // When & Then - Insert messages
        aiMessageDao.insertMessage(message1)
        aiMessageDao.insertMessage(message2)

        // Test get messages for conversation
        val messages = aiMessageDao.getMessagesByConversationId(1L)
        assert(messages.size == 2)
        assert(messages[0].content == "How do I create a data class in Kotlin?")
        assert(messages[1].content.startsWith("You can create a data class"))

        // Test update conversation message count
        conversationDao.updateMessageCount(1L, 2)
        val updatedConversation = conversationDao.getConversationById(1L)
        assert(updatedConversation?.messageCount == 2)
    }

    @Test
    fun `complex queries with joins should work correctly`() = runBlocking {
        // Given - Create related data
        val project = ProjectEntity(
            id = 1L,
            name = "Test Project",
            description = "A test project",
            language = "kotlin",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )
        projectDao.insertProject(project)

        val file1 = FileEntity(
            id = 1L,
            projectId = 1L,
            name = "Main.kt",
            path = "/src/Main.kt",
            content = "fun main() { }",
            fileType = "source",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        val file2 = FileEntity(
            id = 2L,
            projectId = 1L,
            name = "Helper.kt",
            path = "/src/Helper.kt",
            content = "fun helper() { }",
            fileType = "source",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )
        fileDao.insertFile(file1)
        fileDao.insertFile(file2)

        val snippet = CodeSnippetEntity(
            id = 1L,
            title = "Test Snippet",
            description = "A test snippet",
            language = "kotlin",
            code = "fun test() { }",
            tags = listOf("test"),
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis(),
            usageCount = 0
        )
        codeSnippetDao.insertSnippet(snippet)

        // When & Then - Test project with files query
        val projectsWithFiles = projectDao.getProjectsWithFiles()
        assert(projectsWithFiles.isNotEmpty())
        val projectWithFiles = projectsWithFiles.first { it.project.id == 1L }
        assert(projectWithFiles.files.size == 2)
        assert(projectWithFiles.files.any { it.name == "Main.kt" })
        assert(projectWithFiles.files.any { it.name == "Helper.kt" })

        // Test search functionality
        val searchResults = fileDao.searchFiles("Helper", 1L)
        assert(searchResults.isNotEmpty())
        assert(searchResults.first().name == "Helper.kt")

        val snippetSearchResults = codeSnippetDao.searchSnippets("test")
        assert(snippetSearchResults.isNotEmpty())
        assert(snippetSearchResults.first().title == "Test Snippet")
    }

    @Test
    fun `transaction operations should work correctly`() = runBlocking {
        // Given
        val project = ProjectEntity(
            id = 1L,
            name = "Transaction Test",
            description = "Test project for transactions",
            language = "kotlin",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        val files = (1..5).map { index ->
            FileEntity(
                id = index.toLong(),
                projectId = 1L,
                name = "File$index.kt",
                path = "/src/File$index.kt",
                content = "fun file$index() { }",
                fileType = "source",
                createdAt = System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis()
            )
        }

        // When & Then - Batch insert in transaction
        database.runInTransaction {
            projectDao.insertProject(project)
            files.forEach { fileDao.insertFile(it) }
        }

        // Verify all data was inserted
        val insertedProject = projectDao.getProjectById(1L)
        assert(insertedProject != null)
        
        val insertedFiles = fileDao.getFilesByProjectId(1L)
        assert(insertedFiles.size == 5)
    }

    @Test
    fun `data integrity constraints should be enforced`() = runBlocking {
        // Given - Try to insert file with non-existent project ID
        val invalidFile = FileEntity(
            id = 1L,
            projectId = 999L, // Non-existent project
            name = "Invalid.kt",
            path = "/src/Invalid.kt",
            content = "fun invalid() { }",
            fileType = "source",
            createdAt = System.currentTimeMillis(),
            lastModifiedAt = System.currentTimeMillis()
        )

        // When & Then - Should not insert file with invalid foreign key
        try {
            fileDao.insertFile(invalidFile)
            // If we get here, check if the file was actually inserted
            val files = fileDao.getFilesByProjectId(999L)
            assert(files.isEmpty()) // Should be empty since project doesn't exist
        } catch (e: Exception) {
            // Expected behavior - foreign key constraint violation
            assert(e.message?.contains("FOREIGN KEY") == true || 
                   e.message?.contains("constraint") == true)
        }
    }

    @Test
    fun `pagination queries should work correctly`() = runBlocking {
        // Given - Insert multiple snippets for pagination testing
        val snippets = (1..20).map { index ->
            CodeSnippetEntity(
                id = index.toLong(),
                title = "Snippet $index",
                description = "Description for snippet $index",
                language = if (index % 2 == 0) "kotlin" else "java",
                code = "fun snippet$index() { }",
                tags = listOf("tag$index"),
                createdAt = System.currentTimeMillis() + index,
                lastModifiedAt = System.currentTimeMillis() + index,
                usageCount = index
            )
        }

        snippets.forEach { codeSnippetDao.insertSnippet(it) }

        // When & Then - Test pagination
        val firstPage = codeSnippetDao.getSnippetsPaged(0, 10)
        assert(firstPage.size == 10)
        assert(firstPage.first().title == "Snippet 1")
        assert(firstPage.last().title == "Snippet 10")

        val secondPage = codeSnippetDao.getSnippetsPaged(10, 10)
        assert(secondPage.size == 10)
        assert(secondPage.first().title == "Snippet 11")
        assert(secondPage.last().title == "Snippet 20")

        // Test sorting by usage count
        val sortedByUsage = codeSnippetDao.getSnippetsSortedByUsage()
        assert(sortedByUsage.first().usageCount == 20)
        assert(sortedByUsage.last().usageCount == 1)
    }
}
