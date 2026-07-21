package com.example.data

import com.example.BuildConfig
import com.example.data.api.ContentDto
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GeminiApiService
import com.example.data.api.PartDto
import com.example.data.db.AssistantDao
import com.example.data.db.ConversationEntity
import com.example.data.db.MessageEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.db.CustomErrorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AssistantRepository(
    private val assistantDao: AssistantDao,
    private val apiService: GeminiApiService
) {
    private val kodyarApiService = com.example.data.api.KodyarRetrofitClient.service

    suspend fun getKodyarDatabase() = withContext(Dispatchers.IO) {
        kodyarApiService.getDatabase()
    }

    suspend fun getSubscriptionPlans() = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getSubscriptionPlans()
        } catch (e: Exception) {
            com.example.data.model.KodyarPlansResponse(
                status = "error",
                plans = null
            )
        }
    }

    suspend fun login(phone: String, pass: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.login(com.example.data.api.LoginRequest(phone, pass))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در اتصال به سرور: لطفا اتصال اینترنت خود را بررسی کنید.",
                repairs = null,
                data = null,
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun register(
        phone: String,
        pass: String,
        name: String,
        role: String? = null,
        city: String? = null,
        categories: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.register(com.example.data.api.RegisterRequest(phone, pass, name, role, city, categories))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در اتصال به سرور: لطفا اتصال اینترنت خود را بررسی کنید.",
                repairs = null,
                data = null,
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun getMe(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getMe(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در اتصال به سرور: لطفا اتصال اینترنت خود را بررسی کنید.",
                repairs = null,
                data = null,
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun getRepairs(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getRepairs(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در دریافت سفارش‌ها از سایت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    // TODO: Official repair creation endpoint pending confirmation. Using api/repairs/create for now.
    suspend fun createRepair(token: String, techId: String, desc: String, city: String): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        val finalTechId = if (techId.isBlank()) null else techId
        val request = com.example.data.api.RepairRequest(finalTechId, desc, city)
        try {
            kodyarApiService.createRepair(token, request)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در ثبت درخواست در سایت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun verifyCard(token: String, cardHolder: String, trackNumber: String, productId: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.verifyCard(token, com.example.data.api.CardVerifyRequest(cardHolder, trackNumber, productId))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در تأیید کارت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun purchasePart(
        token: String,
        partId: String,
        partName: String,
        qty: Int,
        price: Double,
        total: Double,
        address: String,
        notes: String,
        cardHolder: String,
        track: String
    ): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        try {
            // Call the correct card-verify endpoint as requested by user
            kodyarApiService.verifyCard(token, com.example.data.api.CardVerifyRequest(cardHolder, track, partId))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در ثبت فیش خرید در سایت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun getFreeStatus(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getFreeStatus(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "ok",
                user = null,
                error = null,
                repairs = null,
                data = null,
                error_count = 5,
                problem_count = 5
            )
        }
    }

    suspend fun useFree(token: String, type: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.useFree(token, com.example.data.api.FreeUseRequest(type))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "ok",
                user = null,
                error = null,
                repairs = null,
                data = null,
                error_count = 5,
                problem_count = 5
            )
        }
    }

    val allConversations: Flow<List<ConversationEntity>> = assistantDao.getAllConversations()

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> =
        assistantDao.getMessagesForConversation(conversationId)

    suspend fun createConversation(title: String, personaName: String): Long = withContext(Dispatchers.IO) {
        val conversation = ConversationEntity(title = title, personaName = personaName)
        assistantDao.insertConversation(conversation)
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        assistantDao.deleteConversationById(id)
    }

    suspend fun updateConversationTitle(id: Long, title: String) = withContext(Dispatchers.IO) {
        assistantDao.updateConversationTitle(id, title)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        assistantDao.clearAllConversations()
    }

    // Saved Errors (Bookmarks)
    val allSavedErrors: Flow<List<SavedErrorEntity>> = assistantDao.getAllSavedErrors()

    suspend fun toggleSavedError(code: String, brand: String, category: String, isCurrentlySaved: Boolean) = withContext(Dispatchers.IO) {
        if (isCurrentlySaved) {
            assistantDao.deleteSavedError(code, brand, category)
        } else {
            assistantDao.insertSavedError(
                SavedErrorEntity(code = code, brand = brand, category = category)
            )
        }
    }

    fun isErrorSaved(code: String, brand: String, category: String): Flow<Boolean> =
        assistantDao.isErrorSaved(code, brand, category)

    // Custom Errors
    val allCustomErrors: Flow<List<CustomErrorEntity>> = assistantDao.getAllCustomErrors()

    suspend fun addCustomError(customError: CustomErrorEntity) = withContext(Dispatchers.IO) {
        assistantDao.insertCustomError(customError)
    }

    suspend fun deleteCustomError(id: Long) = withContext(Dispatchers.IO) {
        assistantDao.deleteCustomError(id)
    }

    suspend fun updateCustomErrorNote(id: Long, note: String) = withContext(Dispatchers.IO) {
        assistantDao.updateCustomErrorNote(id, note)
    }

    suspend fun sendMessage(
        conversationId: Long,
        userText: String,
        systemInstructionText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Insert user message in database
            val userMessage = MessageEntity(
                conversationId = conversationId,
                role = "user",
                text = userText
            )
            assistantDao.insertMessage(userMessage)

            // 2. Fetch full history to send as context to Gemini
            val history = assistantDao.getMessagesList(conversationId)
            val contents = history.map { msg ->
                ContentDto(
                    role = msg.role,
                    parts = listOf(PartDto(text = msg.text))
                )
            }

            // 3. Prepare system instruction
            val systemInstruction = if (systemInstructionText.isNotEmpty()) {
                ContentDto(parts = listOf(PartDto(text = systemInstructionText)))
            } else {
                null
            }

            // 4. Check if API key is configured
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("Gemini API key is not configured. Please set your GEMINI_API_KEY in the Secrets panel in AI Studio."))
            }

            // 5. Send API request
            val request = GenerateContentRequest(
                contents = contents,
                systemInstruction = systemInstruction
            )
            val response = apiService.generateContent(apiKey, request)

            // 6. Parse response & insert model reply
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (replyText != null) {
                val modelMessage = MessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    text = replyText
                )
                assistantDao.insertMessage(modelMessage)
                Result.success(replyText)
            } else {
                Result.failure(Exception("Received empty or invalid response from Gemini API."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
