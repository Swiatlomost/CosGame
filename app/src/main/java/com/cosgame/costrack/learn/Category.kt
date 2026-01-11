package com.cosgame.costrack.learn

import androidx.room.*

/**
 * User-defined category for training classifiers.
 * Categories can use different sensor combinations.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val description: String = "",

    // Sensors to use for this category
    val useTouch: Boolean = true,
    val useAccelerometer: Boolean = false,
    val useGyroscope: Boolean = false,

    // Color for UI (hex)
    val color: String = "#2196F3",

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),

    // Training stats
    val touchSamples: Int = 0,
    val movementSamples: Int = 0
) {
    val totalSamples: Int get() = touchSamples + movementSamples

    val sensorList: List<String> get() = buildList {
        if (useTouch) add("Touch")
        if (useAccelerometer) add("Accelerometer")
        if (useGyroscope) add("Gyroscope")
    }

    val sensorsDescription: String get() = sensorList.joinToString(", ").ifEmpty { "None" }
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategories(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Query("UPDATE categories SET touchSamples = touchSamples + 1 WHERE name = :name")
    suspend fun incrementTouchSamples(name: String)

    @Query("UPDATE categories SET movementSamples = movementSamples + 1 WHERE name = :name")
    suspend fun incrementMovementSamples(name: String)

    @Query("SELECT name FROM categories ORDER BY name ASC")
    suspend fun getAllCategoryNames(): List<String>
}

/**
 * Repository for category operations.
 */
class CategoryRepository(private val dao: CategoryDao) {

    suspend fun insert(category: Category): Long = dao.insert(category)

    suspend fun update(category: Category) = dao.update(category)

    suspend fun delete(category: Category) = dao.delete(category)

    suspend fun getAllCategories(): List<Category> = dao.getAllCategories()

    suspend fun getCategoryById(id: Long): Category? = dao.getCategoryById(id)

    suspend fun getCategoryByName(name: String): Category? = dao.getCategoryByName(name)

    suspend fun getCategoryCount(): Int = dao.getCategoryCount()

    suspend fun getAllCategoryNames(): List<String> = dao.getAllCategoryNames()

    suspend fun incrementTouchSamples(name: String) = dao.incrementTouchSamples(name)

    suspend fun incrementMovementSamples(name: String) = dao.incrementMovementSamples(name)

    /**
     * Ensure default categories exist.
     */
    suspend fun ensureDefaults() {
        if (getCategoryCount() == 0) {
            insert(Category(
                name = "me",
                description = "My personal touch and movement patterns",
                useTouch = true,
                useAccelerometer = true,
                useGyroscope = true,
                color = "#4CAF50"
            ))
            insert(Category(
                name = "other",
                description = "Other users for comparison",
                useTouch = true,
                useAccelerometer = true,
                useGyroscope = true,
                color = "#FF5722"
            ))
        }
    }
}
