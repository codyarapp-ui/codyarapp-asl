package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val personaName: String
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_errors", indices = [Index(value = ["code", "brand", "category"], unique = true)])
data class SavedErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val brand: String,
    val category: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_errors")
data class CustomErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val brand: String,
    val category: String,
    val description: String,
    val solution: String,
    val severity: String,
    val userNote: String = "",
    val createdAt: Long = System.currentTimeMillis()
)