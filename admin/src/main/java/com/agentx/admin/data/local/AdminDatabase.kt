package com.agentx.admin.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey
    val key: String,
    val clientName: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isRevoked: Boolean = false,
    val totalRequests: Long = 0L
)

@Entity(tableName = "client_sessions")
data class ClientSessionEntity(
    @PrimaryKey
    val ipAddress: String,
    val id: String,
    val deviceName: String = "Client Device",
    val firstSeenMs: Long = System.currentTimeMillis(),
    val lastSeenMs: Long = System.currentTimeMillis(),
    val totalRequests: Long = 0L,
    val totalTokens: Long = 0L,
    val isBlocked: Boolean = false
)

@Entity(tableName = "traffic_logs")
data class TrafficLogEntity(
    @PrimaryKey
    val id: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val clientIp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val latencyMs: Long,
    val tokensEmitted: Int
)

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY createdAtMs DESC")
    fun getAllFlow(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY createdAtMs DESC")
    suspend fun getAll(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(apiKey: ApiKeyEntity)

    @Update
    suspend fun update(apiKey: ApiKeyEntity)

    @Query("UPDATE api_keys SET isRevoked = 1 WHERE `key` = :key")
    suspend fun revokeKey(key: String)

    @Query("UPDATE api_keys SET totalRequests = totalRequests + 1 WHERE `key` = :key")
    suspend fun incrementRequests(key: String)
}

@Dao
interface ClientSessionDao {
    @Query("SELECT * FROM client_sessions ORDER BY lastSeenMs DESC")
    fun getAllFlow(): Flow<List<ClientSessionEntity>>

    @Query("SELECT * FROM client_sessions ORDER BY lastSeenMs DESC")
    suspend fun getAll(): List<ClientSessionEntity>

    @Query("SELECT * FROM client_sessions WHERE ipAddress = :ip LIMIT 1")
    suspend fun getByIp(ip: String): ClientSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: ClientSessionEntity)

    @Query("UPDATE client_sessions SET isBlocked = :blocked WHERE ipAddress = :ip")
    suspend fun updateBlockedStatus(ip: String, blocked: Boolean)
}

@Dao
interface TrafficLogDao {
    @Query("SELECT * FROM traffic_logs ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentFlow(limit: Int = 100): Flow<List<TrafficLogEntity>>

    @Query("SELECT * FROM traffic_logs ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<TrafficLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: TrafficLogEntity)

    @Query("DELETE FROM traffic_logs WHERE timestampMs < :cutoffMs")
    suspend fun pruneOldLogs(cutoffMs: Long)
}

@Database(
    entities = [
        ApiKeyEntity::class,
        ClientSessionEntity::class,
        TrafficLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AdminDatabase : RoomDatabase() {
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun clientSessionDao(): ClientSessionDao
    abstract fun trafficLogDao(): TrafficLogDao

    companion object {
        @Volatile
        private var INSTANCE: AdminDatabase? = null

        fun getInstance(context: Context): AdminDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdminDatabase::class.java,
                    "agentx_admin.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
