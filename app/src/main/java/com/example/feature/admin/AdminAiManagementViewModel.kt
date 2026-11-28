package com.example.feature.admin

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.toUserFriendlyBackendMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

@Immutable
data class AdminAiManagementUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val windowDays: Int = 7,
    val overview: AdminAiManagementOverview? = null,
    val draftConfig: AiConfigModel = AiConfigModel(),
    val hasUnsavedChanges: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class AdminAiManagementViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdminAiManagementUiState())
    val state: StateFlow<AdminAiManagementUiState> = _state.asStateFlow()

    fun loadInitial(forceRefresh: Boolean = false) {
        val current = _state.value
        if (current.loading) return
        if (!forceRefresh && current.overview != null) return
        fetchOverview(windowDays = current.windowDays, syncDraft = !current.hasUnsavedChanges || forceRefresh)
    }

    fun selectWindowDays(days: Int) {
        val safeDays = if (days in listOf(1, 7, 30)) days else 7
        _state.update { it.copy(windowDays = safeDays) }
        fetchOverview(windowDays = safeDays, syncDraft = !_state.value.hasUnsavedChanges)
    }

    private fun fetchOverview(windowDays: Int, syncDraft: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            AdminAiManagementRepository.fetchOverview(windowDays)
                .onSuccess { data ->
                    _state.update { prev ->
                        prev.copy(
                            loading = false,
                            overview = data,
                            draftConfig = if (syncDraft || prev.overview == null) data.config else prev.draftConfig,
                            hasUnsavedChanges = if (syncDraft || prev.overview == null) false else prev.hasUnsavedChanges,
                            error = null
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = err.toUserFriendlyBackendMessage("Could not load AI management settings.")
                        )
                    }
                }
        }
    }

    fun updateDraft(transform: (AiConfigModel) -> AiConfigModel) {
        _state.update { prev ->
            prev.copy(
                draftConfig = transform(prev.draftConfig),
                hasUnsavedChanges = true,
                error = null
            )
        }
    }

    fun selectProvider(providerId: String) {
        val providers = _state.value.overview?.providers.orEmpty()
        val chosen = providers.firstOrNull { it.id == providerId }
        val defaultModel = chosen?.defaultModel ?: when (providerId) {
            "groq" -> "llama-3.3-70b-versatile"
            "anthropic" -> "claude-haiku-5-5"
            else -> "gemini-2.0-flash"
        }
        val modelPricing = chosen?.models?.firstOrNull { it.id == defaultModel }
        updateDraft { cfg ->
            cfg.copy(
                provider = providerId,
                model = defaultModel,
                pricing = if (modelPricing != null) {
                    AiPricingConfig(
                        inputPerMillionUsd = modelPricing.inputPerMillionUsd,
                        outputPerMillionUsd = modelPricing.outputPerMillionUsd
                    )
                } else cfg.pricing
            )
        }
    }

    fun selectPresetModel(modelOption: AiModelOption) {
        updateDraft { cfg ->
            cfg.copy(
                model = modelOption.id,
                pricing = AiPricingConfig(
                    inputPerMillionUsd = modelOption.inputPerMillionUsd,
                    outputPerMillionUsd = modelOption.outputPerMillionUsd
                )
            )
        }
    }

    fun toggleGlobalEnabled(enabled: Boolean) {
        val payload = JSONObject().apply {
            put("isEnabled", enabled)
        }
        submitPatch(payload, if (enabled) "AI engine resumed." else "AI engine paused for all students.")
    }

    fun saveInstructions() {
        val draft = _state.value.draftConfig
        val payload = JSONObject().apply {
            put("instructions", draft.instructions.toJson())
        }
        submitPatch(payload, "AI instructions and prompts saved.")
    }

    fun saveLimits() {
        val draft = _state.value.draftConfig
        val payload = JSONObject().apply {
            put("isEnabled", draft.isEnabled)
            put("limits", draft.limits.toJson())
            put("pricing", draft.pricing.toJson())
            put(
                "conversation",
                JSONObject().apply {
                    put("speakingEnabled", draft.conversation.speakingEnabled)
                    put("chatGrammarEnabled", draft.conversation.chatGrammarEnabled)
                    put("vocabEvalEnabled", draft.conversation.vocabEvalEnabled)
                }
            )
        }
        submitPatch(payload, "Usage limits & cost guardrails saved.")
    }

    fun saveModelAndConversation() {
        val draft = _state.value.draftConfig
        val cleanModel = draft.model.trim()
        if (cleanModel.isBlank()) {
            _state.update { it.copy(error = "Model ID cannot be empty.") }
            return
        }
        val payload = JSONObject().apply {
            put("provider", draft.provider)
            put("model", cleanModel)
            put("temperature", draft.temperature.toDouble())
            put("maxOutputTokens", draft.maxOutputTokens)
            put("requestTimeoutMs", draft.requestTimeoutMs)
            put("conversation", draft.conversation.toJson())
            put("pricing", draft.pricing.toJson())
        }
        submitPatch(payload, "Model and conversation configuration saved.")
    }

    fun resetSection(section: String) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            AdminAiManagementRepository.resetSection(section, _state.value.windowDays)
                .onSuccess { data ->
                    _state.update {
                        it.copy(
                            saving = false,
                            overview = data,
                            draftConfig = data.config,
                            hasUnsavedChanges = false,
                            message = "Settings reset to defaults.",
                            error = null
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = err.toUserFriendlyBackendMessage("Failed to reset AI settings.")
                        )
                    }
                }
        }
    }

    private fun submitPatch(payload: JSONObject, successMessage: String) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            AdminAiManagementRepository.updateConfig(payload, _state.value.windowDays)
                .onSuccess { data ->
                    _state.update {
                        it.copy(
                            saving = false,
                            overview = data,
                            draftConfig = data.config,
                            hasUnsavedChanges = false,
                            message = successMessage,
                            error = null
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = err.toUserFriendlyBackendMessage("Failed to save AI settings.")
                        )
                    }
                }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }
}
