package com.codemate.features.github.data.remote

import com.codemate.features.github.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub API远程客户端
 * 实现与GitHub REST API v3的交互
 */
class GitHubAPIClient(
    private val baseUrl: String = "https://api.github.com",
    private val timeout: Long = 30_000,
    private val headers: Map<String, String> = defaultHeaders()
) {
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .headers(headers)
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .build()
    }
    
    /**
     * 获取用户仓库列表
     */
    suspend fun getUserRepositories(
        username: String? = null,
        type: RepositoryType = RepositoryType.OWNER,
        sort: RepositorySort = RepositorySort.UPDATED,
        direction: SortDirection = SortDirection.DESC,
        page: Int = 1,
        perPage: Int = 30
    ): GitHubResponse<PaginatedResponse<GitRepository>> {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = when {
                    username != null -> "/users/$username/repos"
                    else -> "/user/repos"
                }
                
                val url = buildString {
                    append(baseUrl)
                    append(endpoint)
                    append("?type=${type.name.lowercase()}")
                    append("&sort=${sort.name.lowercase()}")
                    append("&direction=${direction.name.lowercase()}")
                    append("&page=$page")
                    append("&per_page=$perPage")
                }
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(response.body?.string() ?: "[]")
                    val repositories = parseRepositories(jsonArray)
                    
                    GitHubResponse.Success(
                        PaginatedResponse(
                            data = repositories,
                            pagination = parsePagination(response.headers)
                        )
                    )
                } else {
                    GitHubResponse.Error(
                        message = "Failed to fetch repositories: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 获取仓库详情
     */
    suspend fun getRepository(owner: String, repo: String): GitHubResponse<GitRepository> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/repos/$owner/$repo"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body?.string() ?: "{}")
                    val repository = parseRepository(jsonObject)
                    GitHubResponse.Success(repository)
                } else {
                    GitHubResponse.Error(
                        message = "Failed to fetch repository: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 创建仓库
     */
    suspend fun createRepository(
        name: String,
        description: String? = null,
        private: Boolean = false,
        hasIssues: Boolean = true,
        hasProjects: Boolean = true,
        hasWiki: Boolean = true
    ): GitHubResponse<GitRepository> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/user/repos"
                
                val jsonBody = JSONObject().apply {
                    put("name", name)
                    put("description", description ?: "")
                    put("private", private)
                    put("has_issues", hasIssues)
                    put("has_projects", hasProjects)
                    put("has_wiki", hasWiki)
                }
                
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body?.string() ?: "{}")
                    val repository = parseRepository(jsonObject)
                    GitHubResponse.Success(repository)
                } else {
                    GitHubResponse.Error(
                        message = "Failed to create repository: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 获取Issues列表
     */
    suspend fun getIssues(
        owner: String,
        repo: String,
        state: IssueState = IssueState.OPEN,
        labels: List<String>? = null,
        sort: IssueSort = IssueSort.CREATED,
        direction: SortDirection = SortDirection.DESC,
        since: Date? = null,
        page: Int = 1,
        perPage: Int = 30
    ): GitHubResponse<PaginatedResponse<GitHubIssue>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildString {
                    append(baseUrl)
                    append("/repos/$owner/$repo/issues")
                    append("?state=${state.name.lowercase()}")
                    append("&sort=${sort.name.lowercase()}")
                    append("&direction=${direction.name.lowercase()}")
                    append("&page=$page")
                    append("&per_page=$perPage")
                    if (labels != null && labels.isNotEmpty()) {
                        append("&labels=${labels.joinToString(",")}")
                    }
                    if (since != null) {
                        append("&since=${since.toISO8601()}")
                    }
                }
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(response.body?.string() ?: "[]")
                    val issues = parseIssues(jsonArray)
                    
                    GitHubResponse.Success(
                        PaginatedResponse(
                            data = issues,
                            pagination = parsePagination(response.headers)
                        )
                    )
                } else {
                    GitHubResponse.Error(
                        message = "Failed to fetch issues: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 创建Issue
     */
    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String? = null,
        assignees: List<String>? = null,
        milestone: Int? = null,
        labels: List<String>? = null
    ): GitHubResponse<GitHubIssue> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/repos/$owner/$repo/issues"
                
                val jsonBody = JSONObject().apply {
                    put("title", title)
                    put("body", body ?: "")
                    assignees?.let { put("assignees", JSONArray(it)) }
                    milestone?.let { put("milestone", it) }
                    labels?.let { put("labels", JSONArray(it)) }
                }
                
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body?.string() ?: "{}")
                    val issue = parseIssue(jsonObject)
                    GitHubResponse.Success(issue)
                } else {
                    GitHubResponse.Error(
                        message = "Failed to create issue: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 获取Pull Requests列表
     */
    suspend fun getPullRequests(
        owner: String,
        repo: String,
        state: PRState = PRState.OPEN,
        head: String? = null,
        base: String? = null,
        sort: PRSort = PRSort.CREATED,
        direction: SortDirection = SortDirection.DESC,
        page: Int = 1,
        perPage: Int = 30
    ): GitHubResponse<PaginatedResponse<GitHubPR>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildString {
                    append(baseUrl)
                    append("/repos/$owner/$repo/pulls")
                    append("?state=${state.name.lowercase()}")
                    append("&sort=${sort.name.lowercase()}")
                    append("&direction=${direction.name.lowercase()}")
                    append("&page=$page")
                    append("&per_page=$perPage")
                    head?.let { append("&head=$it") }
                    base?.let { append("&base=$it") }
                }
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(response.body?.string() ?: "[]")
                    val prs = parsePRs(jsonArray)
                    
                    GitHubResponse.Success(
                        PaginatedResponse(
                            data = prs,
                            pagination = parsePagination(response.headers)
                        )
                    )
                } else {
                    GitHubResponse.Error(
                        message = "Failed to fetch pull requests: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 创建Pull Request
     */
    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false
    ): GitHubResponse<GitHubPR> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/repos/$owner/$repo/pulls"
                
                val jsonBody = JSONObject().apply {
                    put("title", title)
                    put("head", head)
                    put("base", base)
                    put("body", body ?: "")
                    put("draft", draft)
                }
                
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body?.string() ?: "{}")
                    val pr = parsePR(jsonObject)
                    GitHubResponse.Success(pr)
                } else {
                    GitHubResponse.Error(
                        message = "Failed to create pull request: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 搜索仓库
     */
    suspend fun searchRepositories(
        query: String,
        sort: SearchSort? = null,
        order: SearchOrder = SortDirection.DESC,
        page: Int = 1,
        perPage: Int = 30
    ): GitHubResponse<SearchResponse<GitRepository>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildString {
                    append(baseUrl)
                    append("/search/repositories")
                    append("?q=${query.encodeURL()}")
                    append("&page=$page")
                    append("&per_page=$perPage")
                    sort?.let { append("&sort=${it.name.lowercase()}") }
                    append("&order=${order.name.lowercase()}")
                }
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body?.string() ?: "{}")
                    val itemsArray = jsonObject.getJSONArray("items")
                    val repositories = parseRepositories(itemsArray)
                    
                    GitHubResponse.Success(
                        SearchResponse(
                            data = repositories,
                            totalCount = jsonObject.getInt("total_count"),
                            incompleteResults = jsonObject.getBoolean("incomplete_results"),
                            pagination = parsePagination(response.headers)
                        )
                    )
                } else {
                    GitHubResponse.Error(
                        message = "Failed to search repositories: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 获取速率限制信息
     */
    suspend fun getRateLimit(): GitHubResponse<RateLimit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/rate_limit"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body?.string() ?: "{}")
                    val rateJson = jsonObject.getJSONObject("rate")
                    
                    val rateLimit = RateLimit(
                        remaining = rateJson.getInt("remaining"),
                        limit = rateJson.getInt("limit"),
                        reset = Date(rateJson.getLong("reset") * 1000),
                        used = rateJson.getInt("used")
                    )
                    
                    GitHubResponse.Success(rateLimit)
                } else {
                    GitHubResponse.Error(
                        message = "Failed to fetch rate limit: ${response.message}",
                        statusCode = response.code
                    )
                }
            } catch (e: Exception) {
                GitHubResponse.Error(message = e.message ?: "Unknown error")
            }
        }
    }
    
    // 解析方法
    private fun parseRepositories(jsonArray: JSONArray): List<GitRepository> {
        val repositories = mutableListOf<GitRepository>()
        
        for (i in 0 until jsonArray.length()) {
            repositories.add(parseRepository(jsonArray.getJSONObject(i)))
        }
        
        return repositories
    }
    
    private fun parseRepository(jsonObject: JSONObject): GitRepository {
        return GitRepository(
            id = jsonObject.getLong("id"),
            name = jsonObject.getString("name"),
            fullName = jsonObject.getString("full_name"),
            description = jsonObject.optString("description"),
            owner = jsonObject.getJSONObject("owner").getString("login"),
            private = jsonObject.getBoolean("private"),
            htmlUrl = jsonObject.getString("html_url"),
            cloneUrl = jsonObject.getString("clone_url"),
            sshUrl = jsonObject.getString("ssh_url"),
            defaultBranch = jsonObject.getString("default_branch"),
            createdAt = Date(jsonObject.getString("created_at")),
            updatedAt = Date(jsonObject.getString("updated_at")),
            pushedAt = if (jsonObject.has("pushed_at")) Date(jsonObject.getString("pushed_at")) else null,
            language = jsonObject.optString("language"),
            stargazersCount = jsonObject.getInt("stargazers_count"),
            watchersCount = jsonObject.getInt("watchers_count"),
            forksCount = jsonObject.getInt("forks_count"),
            openIssuesCount = jsonObject.getInt("open_issues_count"),
            size = jsonObject.getInt("size"),
            topics = jsonObject.getJSONArray("topics").toStringList()
        )
    }
    
    private fun parseIssues(jsonArray: JSONArray): List<GitHubIssue> {
        val issues = mutableListOf<GitHubIssue>()
        
        for (i in 0 until jsonArray.length()) {
            issues.add(parseIssue(jsonArray.getJSONObject(i)))
        }
        
        return issues
    }
    
    private fun parseIssue(jsonObject: JSONObject): GitHubIssue {
        val labels = jsonObject.getJSONArray("labels").toLabelList()
        val assignees = jsonObject.getJSONArray("assignees").toUserList()
        
        return GitHubIssue(
            id = jsonObject.getLong("id"),
            number = jsonObject.getInt("number"),
            title = jsonObject.getString("title"),
            body = jsonObject.optString("body"),
            state = IssueState.valueOf(jsonObject.getString("state").uppercase()),
            labels = labels,
            milestone = if (jsonObject.has("milestone") && !jsonObject.isNull("milestone")) {
                parseMilestone(jsonObject.getJSONObject("milestone"))
            } else null,
            assignee = if (jsonObject.has("assignee") && !jsonObject.isNull("assignee")) {
                parseUser(jsonObject.getJSONObject("assignee"))
            } else null,
            assignees = assignees,
            comments = jsonObject.getInt("comments"),
            createdAt = Date(jsonObject.getString("created_at")),
            updatedAt = Date(jsonObject.getString("updated_at")),
            closedAt = if (jsonObject.has("closed_at") && !jsonObject.isNull("closed_at")) {
                Date(jsonObject.getString("closed_at"))
            } else null,
            author = parseUser(jsonObject.getJSONObject("user")),
            commentsList = emptyList(), // 需要额外请求获取
            events = emptyList(), // 需要额外请求获取
            htmlUrl = jsonObject.getString("html_url"),
            repository = jsonObject.getString("repository_url").substringAfterLast("/"),
            locked = jsonObject.getBoolean("locked"),
            activeLockReason = if (jsonObject.has("active_lock_reason") && !jsonObject.isNull("active_lock_reason")) {
                LockReason.valueOf(jsonObject.getString("active_lock_reason").uppercase())
            } else null
        )
    }
    
    private fun parsePRs(jsonArray: JSONArray): List<GitHubPR> {
        val prs = mutableListOf<GitHubPR>()
        
        for (i in 0 until jsonArray.length()) {
            prs.add(parsePR(jsonArray.getJSONObject(i)))
        }
        
        return prs
    }
    
    private fun parsePR(jsonObject: JSONObject): GitHubPR {
        return GitHubPR(
            id = jsonObject.getLong("id"),
            number = jsonObject.getInt("number"),
            title = jsonObject.getString("title"),
            body = jsonObject.optString("body"),
            state = PRState.valueOf(jsonObject.getString("state").uppercase()),
            base = parsePRBranch(jsonObject.getJSONObject("base")),
            head = parsePRBranch(jsonObject.getJSONObject("head")),
            user = parseUser(jsonObject.getJSONObject("user")),
            assignees = jsonObject.getJSONArray("assignees").toUserList(),
            reviewers = emptyList(), // 需要额外请求获取
            requestedReviewers = jsonObject.getJSONArray("requested_reviewers").toUserList(),
            labels = jsonObject.getJSONArray("labels").toLabelList(),
            milestone = if (jsonObject.has("milestone") && !jsonObject.isNull("milestone")) {
                parseMilestone(jsonObject.getJSONObject("milestone"))
            } else null,
            draft = jsonObject.getBoolean("draft"),
            mergeable = jsonObject.optBoolean("mergeable"),
            mergeableState = if (jsonObject.has("mergeable_state") && !jsonObject.isNull("mergeable_state")) {
                MergeableState.valueOf(jsonObject.getString("mergeable_state").uppercase())
            } else null,
            merged = jsonObject.getBoolean("merged"),
            mergedAt = if (jsonObject.has("merged_at") && !jsonObject.isNull("merged_at")) {
                Date(jsonObject.getString("merged_at"))
            } else null,
            mergedBy = if (jsonObject.has("merged_by") && !jsonObject.isNull("merged_by")) {
                parseUser(jsonObject.getJSONObject("merged_by"))
            } else null,
            comments = jsonObject.getInt("comments"),
            reviewComments = jsonObject.getInt("review_comments"),
            commits = jsonObject.getInt("commits"),
            additions = jsonObject.getInt("additions"),
            deletions = jsonObject.getInt("deletions"),
            changedFiles = jsonObject.getInt("changed_files"),
            createdAt = Date(jsonObject.getString("created_at")),
            updatedAt = Date(jsonObject.getString("updated_at")),
            closedAt = if (jsonObject.has("closed_at") && !jsonObject.isNull("closed_at")) {
                Date(jsonObject.getString("closed_at"))
            } else null,
            htmlUrl = jsonObject.getString("html_url"),
            diffUrl = jsonObject.getString("diff_url"),
            patchUrl = jsonObject.getString("patch_url"),
            commitsUrl = jsonObject.getString("commits_url"),
            commentsUrl = jsonObject.getString("comments_url"),
            reviewCommentsUrl = jsonObject.getString("review_comments_url"),
            reviews = emptyList(), // 需要额外请求获取
            repository = jsonObject.getJSONObject("base").getJSONObject("repo").getString("full_name")
        )
    }
    
    private fun parsePRBranch(jsonObject: JSONObject): PRBranch {
        return PRBranch(
            label = jsonObject.getString("label"),
            ref = jsonObject.getString("ref"),
            sha = jsonObject.getString("sha"),
            user = parseUser(jsonObject.getJSONObject("user")),
            repo = parseRepository(jsonObject.getJSONObject("repo"))
        )
    }
    
    private fun parseUser(jsonObject: JSONObject): User {
        return User(
            id = jsonObject.getLong("id"),
            login = jsonObject.getString("login"),
            name = jsonObject.optString("name"),
            email = jsonObject.optString("email"),
            avatarUrl = jsonObject.getString("avatar_url"),
            htmlUrl = jsonObject.getString("html_url"),
            type = UserType.valueOf(jsonObject.getString("type").uppercase()),
            siteAdmin = jsonObject.getBoolean("site_admin"),
            company = jsonObject.optString("company"),
            location = jsonObject.optString("location"),
            publicRepos = jsonObject.getInt("public_repos"),
            publicGists = jsonObject.getInt("public_gists"),
            followers = jsonObject.getInt("followers"),
            following = jsonObject.getInt("following"),
            createdAt = Date(jsonObject.getString("created_at"))
        )
    }
    
    private fun parseLabel(jsonObject: JSONObject): Label {
        return Label(
            id = jsonObject.getLong("id"),
            nodeId = jsonObject.getString("node_id"),
            name = jsonObject.getString("name"),
            color = jsonObject.getString("color"),
            description = jsonObject.optString("description"),
            default = jsonObject.getBoolean("default")
        )
    }
    
    private fun parseMilestone(jsonObject: JSONObject): Milestone {
        return Milestone(
            id = jsonObject.getLong("id"),
            number = jsonObject.getInt("number"),
            title = jsonObject.getString("title"),
            description = jsonObject.optString("description"),
            state = MilestoneState.valueOf(jsonObject.getString("state").uppercase()),
            openIssues = jsonObject.getInt("open_issues"),
            closedIssues = jsonObject.getInt("closed_issues"),
            createdAt = Date(jsonObject.getString("created_at")),
            updatedAt = Date(jsonObject.getString("updated_at")),
            dueOn = if (jsonObject.has("due_on") && !jsonObject.isNull("due_on")) {
                Date(jsonObject.getString("due_on"))
            } else null,
            htmlUrl = jsonObject.getString("html_url")
        )
    }
    
    private fun parsePagination(headers: okhttp3.Headers): PaginationInfo {
        val linkHeader = headers.get("Link") ?: return PaginationInfo(1, 30, 0, 0, false, false, null, null)
        
        val links = linkHeader.split(",").associate { link ->
            val parts = link.split(";")
            val url = parts[0].trim().removePrefix("<").removeSuffix(">")
            val rel = parts[1].trim().removePrefix("rel=\"").removeSuffix("\"")
            
            val pageParam = Regex("page=(\\d+)").find(url)?.groupValues?.get(1)?.toInt() ?: 1
            val perPageParam = Regex("per_page=(\\d+)").find(url)?.groupValues?.get(1)?.toInt() ?: 30
            
            rel to PaginationInfo(pageParam, perPageParam, 0, 0, false, false, null, null)
        }
        
        return PaginationInfo(
            page = links["next"]?.page ?: 1,
            perPage = links["next"]?.perPage ?: 30,
            total = 0, // GitHub doesn't provide total count in pagination
            totalPages = 0,
            hasNext = links.containsKey("next"),
            hasPrev = links.containsKey("prev"),
            nextPage = links["next"]?.page,
            prevPage = links["prev"]?.page
        )
    }
    
    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(getString(i))
        }
        return list
    }
    
    private fun JSONArray.toUserList(): List<User> {
        val list = mutableListOf<User>()
        for (i in 0 until length()) {
            list.add(parseUser(getJSONObject(i)))
        }
        return list
    }
    
    private fun JSONArray.toLabelList(): List<Label> {
        val list = mutableListOf<Label>()
        for (i in 0 until length()) {
            list.add(parseLabel(getJSONObject(i)))
        }
        return list
    }
    
    private fun Date.toISO8601(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(this)
    }
    
    private fun String.encodeURL(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
    
    private fun defaultHeaders(): Map<String, String> {
        return mapOf(
            "Accept" to "application/vnd.github.v3+json",
            "User-Agent" to "CodeMate-Mobile-GitHub-Client/1.0"
        )
    }
    
    fun updateAuthToken(token: String) {
        // 这里需要更新认证令牌
        // 实际实现中需要考虑安全存储
    }
}