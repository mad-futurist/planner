package app.planner.application.port

import kotlinx.coroutines.flow.Flow

interface UserPreferences {
    val values: Flow<Map<String, String>>
    suspend fun set(key: String, value: String)
    suspend fun capturePrivacy(private: Boolean, informedConsent: Boolean = false)
    suspend fun captureDraft(text: String, hints: String)
}
