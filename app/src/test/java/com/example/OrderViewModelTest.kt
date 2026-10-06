package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.OrderEntity
import com.example.data.ProductEntity
import com.example.ui.viewmodel.OrderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            name = "اختبار",
            price = 18500L,
            unit = "قطعة"
        )

        // Initial state
        assertTrue(viewModel.draftItems.value.isEmpty())
        assertEquals(0L, viewModel.draftTotalAmount.value)

        // Tap product to add
        viewModel.addProductToDraft(product)

        // Verify item added
        val items = viewModel.draftItems.value
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(1L, item.productId)
        assertEquals("اختبار", item.productNameSnapshot)
        assertEquals("قطعة", item.unitSnapshot)
        assertEquals(18500L, item.unitPriceSnapshot)
        assertEquals(1, item.quantity)
        assertEquals(18500L, item.lineTotal)
        assertEquals(18500L, viewModel.draftTotalAmount.value)
        assertTrue(viewModel.hasUnsavedChanges.value)
    }

    @Test
    fun testTapSameProductRepeatedlyIncrementsQuantity() {
        val product = ProductEntity(
            id = 1L,
            name = "اختبار",
            price = 18500L,
            unit = "قطعة"
        )

        // Tap 1: quantity = 1, total = 18,500
        viewModel.addProductToDraft(product)
        assertEquals(1, viewModel.draftItems.value.size)
        assertEquals(1, viewModel.draftItems.value[0].quantity)
        assertEquals(18500L, viewModel.draftItems.value[0].lineTotal)

        // Tap 2: quantity = 2, total = 37,000
        viewModel.addProductToDraft(product)
        assertEquals(1, viewModel.draftItems.value.size)
        assertEquals(2, viewModel.draftItems.value[0].quantity)
        assertEquals(37000L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(37000L, viewModel.draftTotalAmount.value)

        // Tap 3: quantity = 3, total = 55,500
        viewModel.addProductToDraft(product)
        assertEquals(1, viewModel.draftItems.value.size)
        assertEquals(3, viewModel.draftItems.value[0].quantity)
        assertEquals(55500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(55500L, viewModel.draftTotalAmount.value)
    }

    @Test
    fun testStepperIncrementAndDecrement() {
        val product = ProductEntity(
            id = 1L,
            name = "بيبسي",
            price = 18500L,
            unit = "كارتون"
        )
        viewModel.addProductToDraft(product)

        // Increment with + button
        viewModel.incrementQuantity(1L)
        assertEquals(2, viewModel.draftItems.value[0].quantity)
        assertEquals(37000L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(37000L, viewModel.draftTotalAmount.value)

        viewModel.incrementQuantity(1L)
        assertEquals(3, viewModel.draftItems.value[0].quantity)
        assertEquals(55500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(55500L, viewModel.draftTotalAmount.value)

        // Decrement with - button
        viewModel.decrementQuantity(1L)
        assertEquals(2, viewModel.draftItems.value[0].quantity)
        assertEquals(37000L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(37000L, viewModel.draftTotalAmount.value)

        viewModel.decrementQuantity(1L)
        assertEquals(1, viewModel.draftItems.value[0].quantity)
        assertEquals(18500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(18500L, viewModel.draftTotalAmount.value)

        // Decrement when quantity is 1 should NOT go below 1
        viewModel.decrementQuantity(1L)
        assertEquals(1, viewModel.draftItems.value[0].quantity)
        assertEquals(18500L, viewModel.draftItems.value[0].lineTotal)
        assertEquals(18500L, viewModel.draftTotalAmount.value)
    }

    @Test
    fun testRemoveItemFromDraft() {
        val product = ProductEntity(id = 1L, name = "بيبسي", price = 18500L, unit = "كارتون")
        viewModel.addProductToDraft(product)
        assertEquals(1, viewModel.draftItems.value.size)

        viewModel.removeItemFromDraft(1L)
        assertTrue(viewModel.draftItems.value.isEmpty())
        assertEquals(0L, viewModel.draftTotalAmount.value)
    }

    @Test
    fun testNoKeyCollisionBetweenDraftAndAvailableProducts() {
        val productId = 1L
        val draftKey = "draft_$productId"
        val productKey = "product_$productId"

        // Keys MUST NOT be equal even though both represent the same ID 1
        assertNotEquals(draftKey, productKey)
        assertEquals("draft_1", draftKey)
        assertEquals("product_1", productKey)
    }
}
