package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<ContentDto>,
    val generationConfig: GenerationConfigDto? = null,
    val systemInstruction: ContentDto? = null
)

@JsonClass(generateAdapter = true)
data class ContentDto(
    val role: String? = null, // "user" or "model" (corresponds to Gemini API roles)
    val parts: List<PartDto>
)

@JsonClass(generateAdapter = true)
data class PartDto(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfigDto(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<CandidateDto>?
)

@JsonClass(generateAdapter = true)
data class CandidateDto(
    val content: ContentDto?,
    val finishReason: String?
)
