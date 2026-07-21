package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesList(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateConversationTitle(id: Long, title: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    // Saved Errors (Bookmarks)
    @Query("SELECT * FROM saved_errors ORDER BY savedAt DESC")
    fun getAllSavedErrors(): Flow<List<SavedErrorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedError(savedError: SavedErrorEntity): Long

    @Query("DELETE FROM saved_errors WHERE code = :code AND brand = :brand AND category = :category")
    suspend fun deleteSavedError(code: String, brand: String, category: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_errors WHERE code = :code AND brand = :brand AND category = :category LIMIT 1)")
    fun isErrorSaved(code: String, brand: String, category: String): Flow<Boolean>

    // Custom Errors (User notes / Custom entries)
    @Query("SELECT * FROM custom_errors ORDER BY createdAt DESC")
    fun getAllCustomErrors(): Flow<List<CustomErrorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomError(customError: CustomErrorEntity): Long

    @Query("DELETE FROM custom_errors WHERE id = :id")
    suspend fun deleteCustomError(id: Long)

    @Query("UPDATE custom_errors SET userNote = :note WHERE id = :id")
    suspend fun updateCustomErrorNote(id: Long, note: String)
}
