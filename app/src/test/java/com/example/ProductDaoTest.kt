package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProductDao
import com.example.data.ProductEntity
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
    fun writeAndReadProduct() = runBlocking {
        val product = ProductEntity(
            name = "بيبسي 250",
            imageUri = null,
            price = 18500L,
            unit = "كارتون"
        )
        val id = productDao.insertProduct(product)
        assertTrue(id > 0)

        val products = productDao.getAllProducts().first()
        assertEquals(1, products.size)
        assertEquals("بيبسي 250", products[0].name)
        assertEquals(18500L, products[0].price)
        assertEquals("كارتون", products[0].unit)
    }

    @Test
    @Throws(Exception::class)
    fun updateProductTest() = runBlocking {
        val product = ProductEntity(
            name = "بيبسي 250",
            imageUri = null,
            price = 18500L,
            unit = "كارتون"
        )
        val id = productDao.insertProduct(product)
        val inserted = productDao.getProductById(id)
        assertNotNull(inserted)

        val updated = inserted!!.copy(price = 19000L, name = "بيبسي 300")
        productDao.updateProduct(updated)

        val fetched = productDao.getProductById(id)
        assertNotNull(fetched)
        assertEquals("بيبسي 300", fetched!!.name)
        assertEquals(19000L, fetched.price)
    }

    @Test
    @Throws(Exception::class)
    fun deleteProductTest() = runBlocking {
        val product = ProductEntity(
            name = "بيبسي 250",
            imageUri = null,
            price = 18500L,
            unit = "كارتون"
        )
        val id = productDao.insertProduct(product)
        val inserted = productDao.getProductById(id)
        assertNotNull(inserted)

        productDao.deleteProduct(inserted!!)
        val products = productDao.getAllProducts().first()
        assertTrue(products.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun searchProductsTest() = runBlocking {
        productDao.insertProduct(ProductEntity(name = "بيبسي 250", price = 18500L, unit = "كارتون"))
        productDao.insertProduct(ProductEntity(name = "كوكا كولا", price = 19000L, unit = "كارتون"))

        val searchResult = productDao.searchProducts("بيبسي").first()
        assertEquals(1, searchResult.size)
        assertEquals("بيبسي 250", searchResult[0].name)
    }
}
