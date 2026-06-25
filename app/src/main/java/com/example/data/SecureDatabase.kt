package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entity for Chat/Terminal history
@Entity(tableName = "chat_history")
data class EncryptedHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val encryptedText: String,
    val originalLength: Int,
    val targetModel: String,
    val encryptionKeyUsedHash: String,
    // ChatRole.name (USER / ASSISTANT). Only conversation turns are persisted.
    val role: String = ChatRole.ASSISTANT.name,
    val timestamp: Long = System.currentTimeMillis()
)

// 2. Entity for local high-dimensional vector documents
@Entity(tableName = "vector_documents")
data class VectorDocument(
    @PrimaryKey val vectorId: String,
    val title: String,
    val chunkText: String,
    val embeddingJson: String, // Comma separated float strings
    val timestamp: Long = System.currentTimeMillis()
)

// 3. DAO (Data Access Object)
@Dao
interface SecureDao {
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<EncryptedHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entry: EncryptedHistoryEntry)

    @Query("DELETE FROM chat_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM vector_documents ORDER BY timestamp DESC")
    suspend fun getAllVectorDocuments(): List<VectorDocument>

    @Query("SELECT * FROM vector_documents ORDER BY timestamp DESC")
    fun getAllVectorDocumentsFlow(): Flow<List<VectorDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVectorDocument(doc: VectorDocument)

    @Query("DELETE FROM vector_documents WHERE vectorId = :vectorId")
    suspend fun deleteVectorDocument(vectorId: String)
}

// 4. Abstract Room Database
// version bumped 3 -> 4: chat_history gained a `role` column so both user prompts
// and assistant replies persist as a single conversation thread. Rows from older
// schemas can't be mapped cleanly, so a destructive migration is acceptable here.
@Database(entities = [EncryptedHistoryEntry::class, VectorDocument::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun secureDao(): SecureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "SecureAI.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// 5. Repository Pattern
class SecureRepository(private val dao: SecureDao) {
    val allMessages: Flow<List<EncryptedHistoryEntry>> = dao.getAllMessagesFlow()
    val allDocumentsFlow: Flow<List<VectorDocument>> = dao.getAllVectorDocumentsFlow()

    suspend fun insertMessage(entry: EncryptedHistoryEntry) {
        dao.insertMessage(entry)
    }

    suspend fun clearChatHistory() {
        dao.clearHistory()
    }

    suspend fun getAllVectorDocuments(): List<VectorDocument> {
        return dao.getAllVectorDocuments()
    }

    suspend fun insertVectorDocument(doc: VectorDocument) {
        dao.insertVectorDocument(doc)
    }

    suspend fun deleteVectorDocument(vectorId: String) {
        dao.deleteVectorDocument(vectorId)
    }
}
