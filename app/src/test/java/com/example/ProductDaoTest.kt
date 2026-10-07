package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProductDao
import com.example.data.ProductEntity
import com.example.data.ProductUnitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ProductDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var productDao: ProductDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productDao = db.productDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAndReadProductWithUnits() = runBlocking {
        val product = ProductEntity(name = "بيبسي 250", imageUri = null)
        val unit = ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true)

        val id = productDao.insertProductWithUnits(product, listOf(unit))
        assertTrue(id > 0)

        val productsWithUnits = productDao.getAllProductsWithUnits().first()
        assertEquals(1, productsWithUnits.size)
        val p = productsWithUnits[0]
        assertEquals("بيبسي 250", p.product.name)
        assertEquals(1, p.units.size)
        assertEquals("صندوق", p.units[0].unitName)
        assertEquals(31500L, p.units[0].price)
        assertTrue(p.units[0].isDefault)
        assertEquals(31500L, p.displayPrice)
        assertEquals("صندوق", p.displayUnitName)
    }

    @Test
    @Throws(Exception::class)
    fun testProductWithMultipleUnits() = runBlocking {
        val product = ProductEntity(name = "نستله")
        val unit1 = ProductUnitEntity(productId = 0L, unitName = "كارتونة", price = 12000L, isDefault = true)
        val unit2 = ProductUnitEntity(productId = 0L, unitName = "كرتون كامل", price = 72000L, isDefault = false)

        val id = productDao.insertProductWithUnits(product, listOf(unit1, unit2))
        val fetched = productDao.getProductWithUnitsById(id)

        assertNotNull(fetched)
        assertEquals(2, fetched!!.units.size)
        assertEquals("كارتونة", fetched.defaultUnit?.unitName)
        assertEquals(12000L, fetched.defaultUnit?.price)

        val secondUnit = fetched.units.find { it.unitName == "كرتون كامل" }
        assertNotNull(secondUnit)
        assertEquals(72000L, secondUnit!!.price)
    }

    @Test
    @Throws(Exception::class)
    fun updateProductAndUnitsTest() = runBlocking {
        val product = ProductEntity(name = "بيبسي 250")
        val unit = ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true)
        val id = productDao.insertProductWithUnits(product, listOf(unit))

        val inserted = productDao.getProductWithUnitsById(id)
        assertNotNull(inserted)

        val updatedProduct = inserted!!.product.copy(name = "بيبسي 300")
        val updatedUnit = inserted.units[0].copy(price = 33000L)
        productDao.updateProductWithUnits(updatedProduct, listOf(updatedUnit))

        val fetched = productDao.getProductWithUnitsById(id)
        assertNotNull(fetched)
        assertEquals("بيبسي 300", fetched!!.product.name)
        assertEquals(33000L, fetched.displayPrice)
    }

    @Test
    @Throws(Exception::class)
    fun deleteProductCascadeDeletesUnitsTest() = runBlocking {
        val product = ProductEntity(name = "جبس")
        val unit = ProductUnitEntity(productId = 0L, unitName = "كارتونة", price = 5000L, isDefault = true)
        val id = productDao.insertProductWithUnits(product, listOf(unit))

        val inserted = productDao.getProductWithUnitsById(id)
        assertNotNull(inserted)
        assertEquals(1, inserted!!.units.size)

        productDao.deleteProduct(inserted.product)

        val products = productDao.getAllProductsWithUnits().first()
        assertTrue(products.isEmpty())

        val units = productDao.getUnitsForProductDirect(id)
        assertTrue(units.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun searchProductsWithUnitsTest() = runBlocking {
        productDao.insertProductWithUnits(
            ProductEntity(name = "بيبسي 250"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true))
        )
        productDao.insertProductWithUnits(
            ProductEntity(name = "كوكا كولا"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 30000L, isDefault = true))
        )

        val searchResult = productDao.searchProductsWithUnits("بيبسي").first()
        assertEquals(1, searchResult.size)
        assertEquals("بيبسي 250", searchResult[0].product.name)
    }
}
