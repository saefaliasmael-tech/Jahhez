package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.sync.ProductSyncDao
import com.example.data.sync.ProductSyncEntity
import com.example.data.sync.SyncMetadataDao
import com.example.data.sync.SyncMetadataEntity

@Database(
    entities = [
        ProductEntity::class,
        ProductUnitEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        ProductSyncEntity::class,
        SyncMetadataEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun productSyncDao(): ProductSyncDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create product_units table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_units` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `unitName` TEXT NOT NULL,
                        `price` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `minQuantity` INTEGER NOT NULL,
                        FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 2. Migrate existing product units and prices from products table
                db.execSQL(
                    """
                    INSERT INTO `product_units` (`productId`, `unitName`, `price`, `isDefault`, `minQuantity`)
                    SELECT `id`, `unit`, `price`, 1, 1 FROM `products`
                    """.trimIndent()
                )

                // 3. Create index for product_units.productId
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_units_productId` ON `product_units` (`productId`)")

                // 4. Reconstruct products table without price and unit, adding categoryId and isActive
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `imageUri` TEXT,
                        `categoryId` INTEGER,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `products_new` (`id`, `name`, `imageUri`, `categoryId`, `isActive`, `createdAt`, `updatedAt`)
                    SELECT `id`, `name`, `imageUri`, NULL, 1, `createdAt`, `updatedAt` FROM `products`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `products`")
                db.execSQL("ALTER TABLE `products_new` RENAME TO `products`")

                // 5. Add optional productUnitId column to order_items table
                db.execSQL("ALTER TABLE `order_items` ADD COLUMN `productUnitId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderId` ON `order_items` (`orderId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_sync_queue` (
                        `productId` INTEGER PRIMARY KEY NOT NULL,
                        `storeId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_metadata` (
                        `key` TEXT PRIMARY KEY NOT NULL,
                        `lastSyncTimestamp` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jahhez_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
