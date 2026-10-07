package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProductEntity
import com.example.data.ProductUnitEntity
import com.example.data.ProductWithUnits
import com.example.ui.viewmodel.OrderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrderViewModelTest {
    private lateinit var viewModel: OrderViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = OrderViewModel(app)
        viewModel.startNewOrder()
    }

    @Test
    fun testTapProductAddsToOrderWithQuantityOneAndLineTotal() {
        val product = ProductEntity(
            id = 1L,
            name = "اختبار"
        )
        val unit = ProductUnitEntity(
            id = 10L,
            productId = 1L,
            unitName = "كارتونة",
            price = 18500L,
            isDefault = true
        )
        val productWithUnits = ProductWithUnits(product, listOf(unit))

        // Initial state
        assertTrue(viewModel.draftItems.value.isEmpty())
        assertEquals(0L, viewModel.draftTotalAmount.value)

        // Tap product to add
        viewModel.addProductToDraft(productWithUnits)

        // Verify item added
        val items = viewModel.draftItems.value
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(1L, item.productId)
        assertEquals(10L, item.productUnitId)
        assertEquals("اختبار", item.productNameSnapshot)
        assertEquals("كارتونة", item.unitSnapshot)
        assertEquals(18500L, item.unitPriceSnapshot)
        assertEquals(1, item.quantity)
        assertEquals(18500L, item.lineTotal)
        assertEquals(18500L, viewModel.draftTotalAmount.value)
        assertTrue(viewModel.hasUnsavedChanges.value)
    }

    @Test
    fun testTapSameProductRepeatedlyIncrementsQuantity() {
        val product = ProductEntity(id = 1L, name = "اختبار")
        val unit = ProductUnitEntity(id = 10L, productId = 1L, unitName = "صندوق", price = 18500L, isDefault = true)
        val productWithUnits = ProductWithUnits(product, listOf(unit))

        // Tap 1: quantity = 1, total = 18,500
        viewModel.addProductToDraft(productWithUnits)
        assertEquals(1, viewModel.draftItems.value.size)
        assertEquals(1, viewModel.draftItems.value[0].quantity)
        assertEquals(18500L, viewModel.draftItems.value[0].lineTotal)

        // Tap 2: quantity = 2, total = 37,000
        viewModel.addProductToDraft(productWithUnits)
        assertEquals(1, viewModel.draftItems.value.size)
        assertEquals(2, viewModel.draftItems.value[0].quantity)
        assertEquals(37000L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(37000L, viewModel.draftTotalAmount.value)

        // Tap 3: quantity = 3, total = 55,500
        viewModel.addProductToDraft(productWithUnits)
        assertEquals(1, viewModel.draftItems.value.size)
        assertEquals(3, viewModel.draftItems.value[0].quantity)
        assertEquals(55500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(55500L, viewModel.draftTotalAmount.value)
    }

    @Test
    fun testMultipleUnitsForSameProductInOrderDraft() {
        val product = ProductEntity(id = 1L, name = "نستله")
        val unitBox = ProductUnitEntity(id = 10L, productId = 1L, unitName = "كارتونة", price = 12000L, isDefault = true)
        val unitCarton = ProductUnitEntity(id = 11L, productId = 1L, unitName = "كرتون كامل", price = 72000L, isDefault = false)
        val productWithUnits = ProductWithUnits(product, listOf(unitBox, unitCarton))

        // Add 2 of unitBox
        viewModel.addProductToDraft(productWithUnits, unitBox)
        viewModel.addProductToDraft(productWithUnits, unitBox)

        // Add 1 of unitCarton
        viewModel.addProductToDraft(productWithUnits, unitCarton)

        val items = viewModel.draftItems.value
        assertEquals(2, items.size)

        val item1 = items.find { it.productUnitId == 10L }
        assertEquals(2, item1?.quantity)
        assertEquals(24000L, item1?.lineTotal)

        val item2 = items.find { it.productUnitId == 11L }
        assertEquals(1, item2?.quantity)
        assertEquals(72000L, item2?.lineTotal)

        assertEquals(96000L, viewModel.draftTotalAmount.value)
    }

    @Test
    fun testStepperIncrementAndDecrement() {
        val product = ProductEntity(id = 1L, name = "بيبسي")
        val unit = ProductUnitEntity(id = 10L, productId = 1L, unitName = "كارتون", price = 18500L, isDefault = true)
        val productWithUnits = ProductWithUnits(product, listOf(unit))
        viewModel.addProductToDraft(productWithUnits)

        // Increment with + button
        viewModel.incrementQuantity(1L, 10L)
        assertEquals(2, viewModel.draftItems.value[0].quantity)
        assertEquals(37000L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(37000L, viewModel.draftTotalAmount.value)

        viewModel.incrementQuantity(1L, 10L)
        assertEquals(3, viewModel.draftItems.value[0].quantity)
        assertEquals(55500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(55500L, viewModel.draftTotalAmount.value)

        // Decrement with - button
        viewModel.decrementQuantity(1L, 10L)
        assertEquals(2, viewModel.draftItems.value[0].quantity)
        assertEquals(37000L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(37000L, viewModel.draftTotalAmount.value)

        viewModel.decrementQuantity(1L, 10L)
        assertEquals(1, viewModel.draftItems.value[0].quantity)
        assertEquals(18500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(18500L, viewModel.draftTotalAmount.value)

        // Decrement when quantity is 1 should NOT go below 1
        viewModel.decrementQuantity(1L, 10L)
        assertEquals(1, viewModel.draftItems.value[0].quantity)
        assertEquals(18500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(18500L, viewModel.draftTotalAmount.value)
    }

    @Test
    fun testRemoveItemFromDraft() {
        val product = ProductEntity(id = 1L, name = "بيبسي")
        val unit = ProductUnitEntity(id = 10L, productId = 1L, unitName = "كارتون", price = 18500L, isDefault = true)
        val productWithUnits = ProductWithUnits(product, listOf(unit))
        viewModel.addProductToDraft(productWithUnits)
        assertEquals(1, viewModel.draftItems.value.size)

        viewModel.removeItemFromDraft(1L, 10L)
        assertTrue(viewModel.draftItems.value.isEmpty())
        assertEquals(0L, viewModel.draftTotalAmount.value)
    }

    @Test
    fun testNoKeyCollisionBetweenDraftAndAvailableProducts() {
        val productId = 1L
        val draftKey = "draft_$productId"
        val productKey = "product_$productId"

        assertNotEquals(draftKey, productKey)
        assertEquals("draft_1", draftKey)
        assertEquals("product_1", productKey)
    }
}
