package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_courses", primaryKeys = ["userId", "courseId"])
data class CachedCourseEntity(
    val userId: String,
    val courseId: String,
    val title: String,
    val slug: String,
    val subtitle: String,
    val description: String,
    val thumbnailUrl: String,
    val thumbnailPublicId: String,
    val level: String,
    val category: String,
    val durationDays: Int,
    val isPremium: Boolean,
    val price: Double,
    val isPublished: Boolean,
    val totalModules: Int,
    val totalLessons: Int,
    val completedLessons: Int,
    val completionPercentage: Int,
    val enrollmentStatus: String,
    val continueLessonId: String?,
    val hasAccess: Boolean,
    val isOfflineAvailable: Boolean,
    val updatedAt: String?,
    val cachedAtMs: Long
)

@Entity(tableName = "cached_modules", primaryKeys = ["userId", "moduleId"])
data class CachedModuleEntity(
    val userId: String,
    val moduleId: String,
    val courseId: String,
    val title: String,
    val description: String,
    val moduleOrder: Int,
    val isPublished: Boolean,
    val lessonCount: Int,
    val updatedAt: String?
)

@Entity(tableName = "cached_lessons", primaryKeys = ["userId", "lessonId"])
data class CachedLessonEntity(
    val userId: String,
    val lessonId: String,
    val courseId: String,
    val moduleId: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val lessonOrder: Int,
    val estimatedDurationMinutes: Int,
    val isPremium: Boolean,
    val isPublished: Boolean,
    val isLocked: Boolean,
    val contentCount: Int,
    val contentTypesCsv: String,
    val contentJson: String,
    val hasFullContent: Boolean,
    val updatedAt: String?
)

@Entity(tableName = "cached_lesson_progress", primaryKeys = ["userId", "lessonId"])
data class CachedLessonProgressEntity(
    val userId: String,
    val lessonId: String,
    val courseId: String,
    val moduleId: String,
    val isCompleted: Boolean,
    val progressPercentage: Int,
    val lastContentId: String,
    val lastPositionSeconds: Int,
    val startedAt: String?,
    val completedAt: String?,
    val updatedAt: String?,
    val pendingSync: Boolean
)

@Dao
interface CourseOfflineDao {
    @Query("SELECT * FROM cached_courses WHERE userId = :userId ORDER BY updatedAt DESC, title ASC")
    fun observeCourses(userId: String): Flow<List<CachedCourseEntity>>

    @Query("SELECT * FROM cached_courses WHERE userId = :userId ORDER BY updatedAt DESC, title ASC")
    suspend fun getCourses(userId: String): List<CachedCourseEntity>

    @Query("SELECT * FROM cached_courses WHERE userId = :userId AND courseId = :courseId LIMIT 1")
    suspend fun getCourse(userId: String, courseId: String): CachedCourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(items: List<CachedCourseEntity>)

    @Query("UPDATE cached_courses SET isOfflineAvailable = :available WHERE userId = :userId AND courseId = :courseId")
    suspend fun setCourseOfflineAvailable(userId: String, courseId: String, available: Boolean)

    @Query("SELECT * FROM cached_modules WHERE userId = :userId AND courseId = :courseId ORDER BY moduleOrder ASC")
    suspend fun getModulesForCourse(userId: String, courseId: String): List<CachedModuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModules(items: List<CachedModuleEntity>)

    @Query("DELETE FROM cached_modules WHERE userId = :userId AND courseId = :courseId")
    suspend fun deleteModulesForCourse(userId: String, courseId: String)

    @Query("SELECT * FROM cached_lessons WHERE userId = :userId AND courseId = :courseId ORDER BY lessonOrder ASC")
    suspend fun getLessonsForCourse(userId: String, courseId: String): List<CachedLessonEntity>

    @Query("SELECT * FROM cached_lessons WHERE userId = :userId AND lessonId = :lessonId LIMIT 1")
    suspend fun getLesson(userId: String, lessonId: String): CachedLessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessons(items: List<CachedLessonEntity>)

    @Query("DELETE FROM cached_lessons WHERE userId = :userId AND courseId = :courseId")
    suspend fun deleteLessonsForCourse(userId: String, courseId: String)

    @Query("SELECT * FROM cached_lesson_progress WHERE userId = :userId AND courseId = :courseId")
    suspend fun getProgressForCourse(userId: String, courseId: String): List<CachedLessonProgressEntity>

    @Query("SELECT * FROM cached_lesson_progress WHERE userId = :userId AND lessonId = :lessonId LIMIT 1")
    suspend fun getLessonProgress(userId: String, lessonId: String): CachedLessonProgressEntity?

    @Query("SELECT * FROM cached_lesson_progress WHERE userId = :userId AND pendingSync = 1")
    suspend fun getPendingSyncProgress(userId: String): List<CachedLessonProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(items: List<CachedLessonProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSingleProgress(item: CachedLessonProgressEntity)
}

@Database(
    entities = [
        CachedCourseEntity::class,
        CachedModuleEntity::class,
        CachedLessonEntity::class,
        CachedLessonProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CourseOfflineDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseOfflineDao

    companion object {
        @Volatile
        private var INSTANCE: CourseOfflineDatabase? = null

        fun getInstance(context: Context): CourseOfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CourseOfflineDatabase::class.java,
                    "course_offline_cache.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
