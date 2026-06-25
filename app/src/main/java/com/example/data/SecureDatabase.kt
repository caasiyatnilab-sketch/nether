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
// version bumped 2 -> 3: encryption scheme changed (AES/ECB -> Keystore AES/GCM);
// old ciphertext is undecryptable, so force a destructive migration of stale rows.
@Database(entities = [EncryptedHistoryEntry::class, VectorDocument::class], version = 3, exportSchema = false)
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
