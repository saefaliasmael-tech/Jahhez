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
        val productId = productDao.insertProductWithUnits(
            ProductEntity(name = "بيبسي 250"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true))
        )

        val draftItem = OrderItemDraft(
            productId = productId,
            productUnitId = null,
            productNameSnapshot = "بيبسي 250",
            productImageUriSnapshot = null,
            unitSnapshot = "صندوق",
            unitPriceSnapshot = 31500L,
            quantity = 3
        )
        assertEquals(94500L, draftItem.lineTotal)

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
        assertEquals(94500L, retrievedOrder.order.totalAmount)

        assertEquals(1, retrievedOrder.items.size)
        val retrievedItem = retrievedOrder.items[0]
        assertEquals("بيبسي 250", retrievedItem.productNameSnapshot)
        assertEquals(31500L, retrievedItem.unitPriceSnapshot)
        assertEquals(3, retrievedItem.quantity)
        assertEquals(94500L, retrievedItem.lineTotal)
    }

    @Test
    fun testOrderWithMultipleItemsTotal() = runBlocking {
        val p1Id = productDao.insertProductWithUnits(
            ProductEntity(name = "بيبسي 250"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true))
        )
        val p2Id = productDao.insertProductWithUnits(
            ProductEntity(name = "سفن أب 250"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 30000L, isDefault = true))
        )

        val item1 = OrderItemDraft(
            productId = p1Id,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "صندوق",
            unitPriceSnapshot = 31500L,
            quantity = 3
        )
        val item2 = OrderItemDraft(
            productId = p2Id,
            productNameSnapshot = "سفن أب 250",
            unitSnapshot = "صندوق",
            unitPriceSnapshot = 30000L,
            quantity = 2
        )

        val totalAmount = item1.lineTotal + item2.lineTotal
        assertEquals(154500L, totalAmount)

        val order = OrderEntity(status = OrderEntity.STATUS_READY, totalAmount = totalAmount)
        val orderId = orderDao.saveOrderWithItems(
            order,
            listOf(item1.toEntity(0L), item2.toEntity(0L))
        )

        val retrieved = orderDao.getOrderWithItemsDirect(orderId)
        assertNotNull(retrieved)
        assertEquals(154500L, retrieved!!.order.totalAmount)
        assertEquals(2, retrieved.items.size)
    }

    @Test
    fun testUpdateOrder() = runBlocking {
        val pId = productDao.insertProductWithUnits(
            ProductEntity(name = "بيبسي 250"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true))
        )
        val item1 = OrderItemDraft(
            productId = pId,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "صندوق",
            unitPriceSnapshot = 31500L,
            quantity = 1
        )
        val orderId = orderDao.saveOrderWithItems(
            OrderEntity(status = OrderEntity.STATUS_DRAFT, totalAmount = 31500L),
            listOf(item1.toEntity(0L))
        )

        val updatedItem = item1.copy(quantity = 4)
        assertEquals(126000L, updatedItem.lineTotal)

        val updatedOrder = OrderEntity(
            id = orderId,
            status = OrderEntity.STATUS_READY,
            totalAmount = updatedItem.lineTotal
        )
        orderDao.updateOrderWithItems(updatedOrder, listOf(updatedItem.toEntity(orderId)))

        val retrieved = orderDao.getOrderWithItemsDirect(orderId)
        assertNotNull(retrieved)
        assertEquals(OrderEntity.STATUS_READY, retrieved!!.order.status)
        assertEquals(126000L, retrieved.order.totalAmount)
        assertEquals(1, retrieved.items.size)
        assertEquals(4, retrieved.items[0].quantity)
        assertEquals(126000L, retrieved.items[0].lineTotal)
    }

    @Test
    fun testDeleteOrderCascadeDeletesItems() = runBlocking {
        val pId = productDao.insertProductWithUnits(
            ProductEntity(name = "بيبسي 250"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true))
        )
        val item = OrderItemDraft(
            productId = pId,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "صندوق",
            unitPriceSnapshot = 31500L,
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
        val productId = productDao.insertProductWithUnits(
            ProductEntity(name = "بيبسي 250"),
            listOf(ProductUnitEntity(productId = 0L, unitName = "صندوق", price = 31500L, isDefault = true))
        )

        val draftItem = OrderItemDraft(
            productId = productId,
            productNameSnapshot = "بيبسي 250",
            unitSnapshot = "صندوق",
            unitPriceSnapshot = 31500L,
            quantity = 3
        )
        val orderId = orderDao.saveOrderWithItems(
            OrderEntity(status = OrderEntity.STATUS_READY, totalAmount = draftItem.lineTotal),
            listOf(draftItem.toEntity(0L))
        )

        // Now merchant updates price of this product's unit to 35,000
        val productWithUnits = productDao.getProductWithUnitsById(productId)
        assertNotNull(productWithUnits)
        val updatedUnits = productWithUnits!!.units.map { it.copy(price = 35000L) }
        productDao.updateProductWithUnits(productWithUnits.product, updatedUnits)

        val currentProduct = productDao.getProductWithUnitsById(productId)
        assertEquals(35000L, currentProduct!!.displayPrice)

        // Historical order MUST still preserve the original snapshot price 31,500
        val retrievedOrder = orderDao.getOrderWithItemsDirect(orderId)
        assertNotNull(retrievedOrder)

        val itemSnapshot = retrievedOrder!!.items[0]
        assertEquals(31500L, itemSnapshot.unitPriceSnapshot)
        assertEquals(94500L, itemSnapshot.lineTotal)
        assertEquals(94500L, retrievedOrder.order.totalAmount)
    }

    @Test
    fun testRoomMigration1To2PreservesProducts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_1_2.db")
        if (dbFile.exists()) dbFile.delete()

        val v1Config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_1_2.db")
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

        val migratedRoomDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_1_2.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val list = migratedRoomDb.productDao().getAllProductsWithUnits().first()
            assertEquals(1, list.size)
            assertEquals("منتج قديم", list[0].product.name)
            assertEquals(15000L, list[0].displayPrice)
            assertEquals("قطعة", list[0].displayUnitName)
        }

        migratedRoomDb.close()
        dbFile.delete()
    }

    @Test
    fun testRoomMigration2To3PreservesDataAndMigratesUnits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_2_3.db")
        if (dbFile.exists()) dbFile.delete()

        // Create a real schema v2 database
        val v2Config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_2_3.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
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
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `orders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `totalAmount` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `order_items` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `orderId` INTEGER NOT NULL,
                            `productId` INTEGER NOT NULL,
                            `productNameSnapshot` TEXT NOT NULL,
                            `productImageUriSnapshot` TEXT,
                            `unitSnapshot` TEXT NOT NULL,
                            `unitPriceSnapshot` INTEGER NOT NULL,
                            `quantity` INTEGER NOT NULL,
                            `lineTotal` INTEGER NOT NULL,
                            FOREIGN KEY(`orderId`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderId` ON `order_items` (`orderId`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(v2Config)
        val v2Db = helper.writableDatabase
        // Insert product 1: Pepsi, price 31500, unit صندوق
        v2Db.execSQL(
            "INSERT INTO products (id, name, imageUri, price, unit, createdAt, updatedAt) VALUES (1, 'بيبسي', 'local/img1.jpg', 31500, 'صندوق', 2000, 2000)"
        )
        // Insert product 2: Chips, price 5000, unit كارتونة
        v2Db.execSQL(
            "INSERT INTO products (id, name, imageUri, price, unit, createdAt, updatedAt) VALUES (2, 'جبس', NULL, 5000, 'كارتونة', 3000, 3000)"
        )
        // Insert order 10 with item from Pepsi at 31500
        v2Db.execSQL(
            "INSERT INTO orders (id, createdAt, updatedAt, status, totalAmount) VALUES (10, 4000, 4000, 'READY', 63000)"
        )
        v2Db.execSQL(
            "INSERT INTO order_items (id, orderId, productId, productNameSnapshot, productImageUriSnapshot, unitSnapshot, unitPriceSnapshot, quantity, lineTotal) VALUES (101, 10, 1, 'بيبسي', 'local/img1.jpg', 'صندوق', 31500, 2, 63000)"
        )
        v2Db.close()

        // Now open with AppDatabase applying MIGRATION_2_3 and MIGRATION_3_4
        val migratedRoomDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_2_3.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val productsWithUnits = migratedRoomDb.productDao().getAllProductsWithUnits().first()
            assertEquals(2, productsWithUnits.size)

            val pepsi = productsWithUnits.find { it.product.id == 1L }
            assertNotNull(pepsi)
            assertEquals("بيبسي", pepsi!!.product.name)
            assertEquals("local/img1.jpg", pepsi.product.imageUri)
            assertEquals(1, pepsi.units.size)
            val pepsiUnit = pepsi.units[0]
            assertEquals(1L, pepsiUnit.productId)
            assertEquals("صندوق", pepsiUnit.unitName)
            assertEquals(31500L, pepsiUnit.price)
            assertTrue(pepsiUnit.isDefault)
            assertEquals(1, pepsiUnit.minQuantity)

            val chips = productsWithUnits.find { it.product.id == 2L }
            assertNotNull(chips)
            assertEquals("جبس", chips!!.product.name)
            assertEquals("كارتونة", chips.displayUnitName)
            assertEquals(5000L, chips.displayPrice)

            // Verify order history completely intact
            val order = migratedRoomDb.orderDao().getOrderWithItemsDirect(10L)
            assertNotNull(order)
            assertEquals(63000L, order!!.order.totalAmount)
            assertEquals(1, order.items.size)
            assertEquals("بيبسي", order.items[0].productNameSnapshot)
            assertEquals("صندوق", order.items[0].unitSnapshot)
            assertEquals(31500L, order.items[0].unitPriceSnapshot)
            assertEquals(2, order.items[0].quantity)
            assertEquals(63000L, order.items[0].lineTotal)
        }

        migratedRoomDb.close()
        dbFile.delete()
    }
}
