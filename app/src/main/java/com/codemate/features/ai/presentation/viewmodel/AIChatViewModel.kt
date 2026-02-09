package com.codemate.features.ai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.usecase.*
import com.codemate.features.ai.domain.repository.AIServiceRepository
import com.codemate.features.ai.domain.repository.AIConfigRepository
import com.codemate.features.ai.domain.repository.AISafetyRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI聊天界面ViewModel
 * 管理聊天界面的状态和业务逻辑
 */
class AIChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val codeGenerationUseCase: CodeGenerationUseCase,
    private val aiSafetyUseCase: AISafetyUseCase,
    private val aiConfigRepository: AIConfigRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()
    
    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()
    
    /**
     * 发送聊天消息
     */
    fun sendMessage(message: String, context: ConversationContext, modelType: AIModelType) {
        val conversationId = _currentConversation.value?.id ?: ""
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val result = chatUseCase.sendMessage(conversationId, message, context, modelType)
                result.fold(
                    onSuccess = { response ->
                        if (response.status == AIResponseStatus.COMPLETED) {
                            _uiState.update { 
                                it.copy(
                                    messages = it.messages + 
                                        AIMessage(role = MessageRole.USER, content = message) +
                                        AIMessage(role = MessageRole.ASSISTANT, content = response.content),
                                    isLoading = false
                                )
                            }
                        }
                    },
                    onFailure = { exception ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = exception.message ?: "发送消息失败"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "未知错误"
                    )
                }
            }
        }
    }
    
    /**
     * 发送流式聊天消息
     */
    fun sendStreamingMessage(message: String, context: ConversationContext, modelType: AIModelType) {
        val conversationId = _currentConversation.value?.id ?: ""
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isStreaming = true,
                    error = null,
                    streamingMessage = AIMessage(role = MessageRole.ASSISTANT, content = "")
                )
            }
            
            try {
                chatUseCase.sendStreamingMessage(conversationId, message, context, modelType)
                    .collect { response ->
                        if (response.status == AIResponseStatus.STREAMING) {
                            _uiState.update { state ->
                                val currentMessage = state.streamingMessage?.copy(
                                    content = state.streamingMessage.content + response.contentDelta
                                ) ?: AIMessage(role = MessageRole.ASSISTANT, content = response.contentDelta)
                                
                                state.copy(streamingMessage = currentMessage)
                            }
                        } else if (response.status == AIResponseStatus.COMPLETED) {
                            _uiState.update { state ->
                                val newMessage = state.streamingMessage?.copy(
                                    content = state.streamingMessage.content + response.contentDelta
                                )
                                
                                state.copy(
                                    messages = state.messages + 
                                        AIMessage(role = MessageRole.USER, content = message) +
                                        (newMessage ?: AIMessage(role = MessageRole.ASSISTANT, content = "")),
                                    isStreaming = false,
                                    streamingMessage = null
                                )
                            }
                        } else if (response.status == AIResponseStatus.ERROR) {
                            _uiState.update { 
                                it.copy(
                                    isStreaming = false,
                                    streamingMessage = null,
                                    error = "流式响应错误: ${response.contentDelta}"
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isStreaming = false,
                        streamingMessage = null,
                        error = e.message ?: "流式响应失败"
                    )
                }
            }
        }
    }
    
    /**
     * 创建新对话
     */
    fun createNewConversation() {
        viewModelScope.launch {
            val result = chatUseCase.createNewConversation()
            result.fold(
                onSuccess = { conversation ->
                    _currentConversation.value = conversation
                    _uiState.update { 
                        it.copy(
                            conversationId = conversation.id,
                            messages = conversation.messages
                        )
                    }
                },
                onFailure = { exception ->
                    _uiState.update { 
                        it.copy(error = "创建对话失败: ${exception.message}")
                    }
                }
            )
        }
    }
    
    /**
     * 加载对话
     */
    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val result = chatUseCase.getConversation(conversationId)
            result.fold(
                onSuccess = { conversation ->
                    _currentConversation.value = conversation
                    _uiState.update { 
                        it.copy(
                            conversationId = conversation.id,
                            messages = conversation.messages
                        )
                    }
                },
                onFailure = { exception ->
                    _uiState.update { 
                        it.copy(error = "加载对话失败: ${exception.message}")
                    }
                }
            )
        }
    }
    
    /**
     * 获取可用模型
     */
    fun getAvailableModels() {
        viewModelScope.launch {
            try {
                val configs = aiConfigRepository.getAllConfigs()
                val models = configs.map { it.model }
                
                _uiState.update { it.copy(availableModels = models) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "获取模型列表失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * 停止流式响应
     */
    fun stopStreaming() {
        _uiState.update { 
            it.copy(
                isStreaming = false,
                streamingMessage = null
            )
        }
    }
}

/**
 * AI聊天界面状态
 */
data class AIChatUiState(
    val conversationId: String = "",
    val messages: List<AIMessage> = emptyList(),
    val streamingMessage: AIMessage? = null,
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val availableModels: List<AIModelConfig> = emptyList(),
    val currentModel: AIModelConfig? = null
)