package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
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
class OrderDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var productDao: ProductDao
    private lateinit var orderDao: OrderDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productDao = db.productDao()
        orderDao = db.orderDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testCreateAndReadOrderWithCalculations() = runBlocking {
        val productId = productDao.insertProduct(
            ProductEntity(name = "بيبسي 250", price = 18500L, unit = "كارتون")
        )

        val draftItem = OrderItemDraft(
            productId = productId,
            productNameSnapshot = "بيبسي 250",
            productImageUriSnapshot = null,
            unitSnapshot = "كارتون",
            unitPriceSnapshot = 18500L,
            quantity = 3
        )
        assertEquals(55500L, draftItem.lineTotal)

        val order = OrderEntity(
            status = OrderEntity.STATUS_READY,
            totalAmount = draftItem.lineTotal
        )
        val orderId = orderDao.saveOrderWithItems(order, listOf(draftItem.toEntity(0L)))
        assertTrue(orderId > 0)

        val savedOrders = orderDao.getOrdersWithItems().first()
        assertEquals(1, savedOrders.size)

        val retrievedOrder = savedOrders[0]
        assertEquals(orderId, retrievedOrder.order.id)
        assertEquals(OrderEntity.STATUS_READY, retrievedOrder.order.status)
        assertEquals(55500L, retrievedOrder.order.totalAmount)

        assertEquals(1, retrievedOrder.items.size)
        val retrievedItem = retrievedOrder.items[0]
        assertEquals("بيبسي 250", retrievedItem.productNameSnapshot)
        assertEquals(18500L, retrievedItem.unitPriceSnapshot)
        assertEquals(3, retrievedItem.quantity)
        assertEquals(55500L, retrievedItem.lineTotal)
    }

    @Test
    fun testOrderWithMultipleItemsTotal() = runBlocking {
        val p1Id = productDao.insertProduct(ProductEntity(name = "بيبسي 250", price = 18500L, unit = "كارتون"))
        val p2Id = productDao.insertProduct(ProductEntity(name = "سفن أب 250", price = 17000L, unit = "كارتون"))

        val item1 = OrderItemDraft(
            productId = p1Id,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "كارتون",
            unitPriceSnapshot = 18500L,
            quantity = 3
        )
        val item2 = OrderItemDraft(
            productId = p2Id,
            productNameSnapshot = "سفن أب 250",
            unitSnapshot = "كارتون",
            unitPriceSnapshot = 17000L,
            quantity = 2
        )

        val totalAmount = item1.lineTotal + item2.lineTotal
        assertEquals(89500L, totalAmount)

        val order = OrderEntity(status = OrderEntity.STATUS_READY, totalAmount = totalAmount)
        val orderId = orderDao.saveOrderWithItems(
            order,
            listOf(item1.toEntity(0L), item2.toEntity(0L))
        )

        val retrieved = orderDao.getOrderWithItemsDirect(orderId)
        assertNotNull(retrieved)
        assertEquals(89500L, retrieved!!.order.totalAmount)
        assertEquals(2, retrieved.items.size)
    }

    @Test
    fun testUpdateOrder() = runBlocking {
        val pId = productDao.insertProduct(ProductEntity(name = "بيبسي 250", price = 18500L, unit = "كارتون"))
        val item1 = OrderItemDraft(
            productId = pId,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "كارتون",
            unitPriceSnapshot = 18500L,
            quantity = 1
        )
        val orderId = orderDao.saveOrderWithItems(
            OrderEntity(status = OrderEntity.STATUS_DRAFT, totalAmount = 18500L),
            listOf(item1.toEntity(0L))
        )

        val updatedItem = item1.copy(quantity = 4)
        assertEquals(74000L, updatedItem.lineTotal)

        val updatedOrder = OrderEntity(
            id = orderId,
            status = OrderEntity.STATUS_READY,
            totalAmount = updatedItem.lineTotal
        )
        orderDao.updateOrderWithItems(updatedOrder, listOf(updatedItem.toEntity(orderId)))

        val retrieved = orderDao.getOrderWithItemsDirect(orderId)
        assertNotNull(retrieved)
        assertEquals(OrderEntity.STATUS_READY, retrieved!!.order.status)
        assertEquals(74000L, retrieved.order.totalAmount)
        assertEquals(1, retrieved.items.size)
        assertEquals(4, retrieved.items[0].quantity)
        assertEquals(74000L, retrieved.items[0].lineTotal)
    }

    @Test
    fun testDeleteOrderCascadeDeletesItems() = runBlocking {
        val pId = productDao.insertProduct(ProductEntity(name = "بيبسي 250", price = 18500L, unit = "كارتون"))
        val item = OrderItemDraft(
            productId = pId,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "كارتون",
            unitPriceSnapshot = 18500L,
            quantity = 2
        )
        val order = OrderEntity(status = OrderEntity.STATUS_READY, totalAmount = item.lineTotal)
        val orderId = orderDao.saveOrderWithItems(order, listOf(item.toEntity(0L)))

        val savedOrder = orderDao.getOrderWithItemsDirect(orderId)
        assertNotNull(savedOrder)

        orderDao.deleteOrder(savedOrder!!.order)

        val afterDelete = orderDao.getOrderWithItemsDirect(orderId)
        assertEquals(null, afterDelete)

        val allOrders = orderDao.getOrdersWithItems().first()
        assertTrue(allOrders.isEmpty())
    }

    @Test
    fun testCriticalPriceSnapshotPreservedWhenProductPriceChangesLater() = runBlocking {
        val productId = productDao.insertProduct(
            ProductEntity(name = "بيبسي 250", price = 18500L, unit = "كارتون")
        )

        val draftItem = OrderItemDraft(
            productId = productId,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "كارتون",
            unitPriceSnapshot = 18500L,
            quantity = 3
        )
        val orderId = orderDao.saveOrderWithItems(
            OrderEntity(status = OrderEntity.STATUS_READY, totalAmount = draftItem.lineTotal),
            listOf(draftItem.toEntity(0L))
        )

        val currentProduct = productDao.getProductById(productId)
        assertNotNull(currentProduct)
        productDao.updateProduct(currentProduct!!.copy(price = 20000L))

        val updatedProduct = productDao.getProductById(productId)
        assertEquals(20000L, updatedProduct!!.price)

        val retrievedOrder = orderDao.getOrderWithItemsDirect(orderId)
        assertNotNull(retrievedOrder)

        val itemSnapshot = retrievedOrder!!.items[0]
        assertEquals(18500L, itemSnapshot.unitPriceSnapshot)
        assertEquals(55500L, itemSnapshot.lineTotal)
        assertEquals(55500L, retrievedOrder.order.totalAmount)
    }

    @Test
    fun testRoomMigration1To2PreservesProducts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        if (dbFile.exists()) dbFile.delete()

        val v1Config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `products` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `imageUri` TEXT,
                            `price` INTEGER NOT NULL,
                            `unit` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(v1Config)
        val v1Db = helper.writableDatabase
        v1Db.execSQL(
            "INSERT INTO products (id, name, imageUri, price, unit, createdAt, updatedAt) VALUES (1, 'منتج قديم', NULL, 15000, 'قطعة', 1000, 1000)"
        )
        v1Db.close()

        val migratedRoomDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val oldProduct = migratedRoomDb.productDao().getAllProducts()
        runBlocking {
            val list = oldProduct.first()
            assertEquals(1, list.size)
            assertEquals("منتج قديم", list[0].name)
            assertEquals(15000L, list[0].price)
            assertEquals("قطعة", list[0].unit)
        }

        runBlocking {
            val orderId = migratedRoomDb.orderDao().insertOrder(
                OrderEntity(status = OrderEntity.STATUS_READY, totalAmount = 15000L)
            )
            assertTrue(orderId > 0)
        }

        migratedRoomDb.close()
        dbFile.delete()
    }
}
