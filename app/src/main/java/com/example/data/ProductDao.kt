package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    fun getAllProductsWithUnits(): Flow<List<ProductWithUnits>>

    @Transaction
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchProductsWithUnits(query: String): Flow<List<ProductWithUnits>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductWithUnitsById(id: Long): ProductWithUnits?

    @Transaction
    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductWithUnitsByIdFlow(id: Long): Flow<ProductWithUnits?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT id FROM products WHERE isActive = 1")
    suspend fun getActiveProductIds(): List<Long>

    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductUnit(unit: ProductUnitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductUnits(units: List<ProductUnitEntity>): List<Long>

    @Update
    suspend fun updateProductUnit(unit: ProductUnitEntity)

    @Delete
    suspend fun deleteProductUnit(unit: ProductUnitEntity)

    @Query("DELETE FROM product_units WHERE productId = :productId")
    suspend fun deleteUnitsByProductId(productId: Long)

    @Query("SELECT * FROM product_units WHERE productId = :productId")
    fun getUnitsForProduct(productId: Long): Flow<List<ProductUnitEntity>>

    @Query("SELECT * FROM product_units WHERE productId = :productId")
    suspend fun getUnitsForProductDirect(productId: Long): List<ProductUnitEntity>

    @Query("SELECT * FROM product_units WHERE id = :unitId")
    suspend fun getUnitById(unitId: Long): ProductUnitEntity?

    @Transaction
    suspend fun insertProductWithUnits(product: ProductEntity, units: List<ProductUnitEntity>): Long {
        val productId = insertProduct(product)
        val unitsWithProductId = units.map { it.copy(productId = productId) }
        insertProductUnits(unitsWithProductId)
        return productId
    }

    @Transaction
    suspend fun updateProductWithUnits(product: ProductEntity, units: List<ProductUnitEntity>) {
        updateProduct(product)
        deleteUnitsByProductId(product.id)
        val unitsWithProductId = units.map { it.copy(productId = product.id) }
        insertProductUnits(unitsWithProductId)
    }
}
