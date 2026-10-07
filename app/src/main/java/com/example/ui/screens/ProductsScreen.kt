package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ProductEntity
import com.example.data.ProductUnitEntity
import com.example.data.ProductWithUnits
import com.example.data.store.UserRole
import com.example.data.sync.SyncStatus
import com.example.ui.viewmodel.ProductViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(viewModel: ProductViewModel) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val pendingCount by viewModel.pendingSyncCount.collectAsState()

    val displayProducts = remember(products) {
        products.filter { it.product.isActive }
    }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingProductWithUnits by remember { mutableStateOf<ProductWithUnits?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    placeholder = { Text("ابحث عن منتج...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            syncStatus == SyncStatus.SYNCING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "جاري المزامنة...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            pendingCount > 0 || syncStatus == SyncStatus.PENDING -> {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "بانتظار المزامنة ($pendingCount)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            syncStatus == SyncStatus.SYNCED -> {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تمت المزامنة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            syncStatus == SyncStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تعذر المزامنة (سيتم المحاولة لاحقاً)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {
                                Text(
                                    text = if (userRole == UserRole.MERCHANT) "وضع التاجر" else "وضع العميل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.triggerSync() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "مزامنة الآن",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (userRole == UserRole.MERCHANT) {
                FloatingActionButton(
                    onClick = {
                        editingProductWithUnits = null
                        showAddEditDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة منتج")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (displayProducts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (searchQuery.isBlank()) {
                            Text(
                                text = "ما عندك منتجات بعد",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (userRole == UserRole.MERCHANT) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        editingProductWithUnits = null
                                        showAddEditDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "+ إضافة منتج")
                                }
                            }
                        } else {
                            Text(
                                text = "ما لقينا منتج بهذا الاسم.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayProducts, key = { it.product.id }) { productWithUnits ->
                        ProductItemCard(
                            productWithUnits = productWithUnits,
                            canEdit = userRole == UserRole.MERCHANT,
                            onEdit = {
                                editingProductWithUnits = productWithUnits
                                showAddEditDialog = true
                            },
                            onDelete = {
                                productToDelete = productWithUnits.product
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditProductDialog(
            productWithUnits = editingProductWithUnits,
            viewModel = viewModel,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, imageUri, units ->
                if (editingProductWithUnits == null) {
                    viewModel.addProduct(
                        name = name,
                        imageUri = imageUri,
                        units = units,
                        onSuccess = { showAddEditDialog = false },
                        onError = {}
                    )
                } else {
                    viewModel.updateProduct(
                        id = editingProductWithUnits!!.product.id,
                        name = name,
                        imageUri = imageUri,
                        units = units,
                        createdAt = editingProductWithUnits!!.product.createdAt,
                        oldImageUri = editingProductWithUnits!!.product.imageUri,
                        onSuccess = { showAddEditDialog = false },
                        onError = {}
                    )
                }
            }
        )
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("حذف المنتج") },
            text = { Text("هل تريد حذف هذا المنتج مع جميع وحدات البيع المرتبطة به؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        productToDelete?.let { viewModel.deleteProduct(it) }
                        productToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun ProductItemCard(
    productWithUnits: ProductWithUnits,
    canEdit: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val product = productWithUnits.product
    val defaultUnit = productWithUnits.defaultUnit
    val units = productWithUnits.units

    val formattedPrice = remember(defaultUnit?.price) {
        val price = defaultUnit?.price ?: 0L
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        "${formatter.format(price)} د.ع"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (!product.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = Uri.parse(product.imageUri),
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$formattedPrice / ${defaultUnit?.unitName ?: "بدون وحدة"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (units.size > 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${units.size} وحدات بيع متاحة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (canEdit) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (units.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    units.forEach { unit ->
                        val priceFormatter = NumberFormat.getNumberInstance(Locale.US)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (unit.isDefault) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Text(
                                text = "${unit.unitName}: ${priceFormatter.format(unit.price)} د.ع",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (unit.isDefault) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class UnitInputState(
    val id: Long = 0L,
    val unitName: String = "",
    val priceStr: String = "",
    val isDefault: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    productWithUnits: ProductWithUnits?,
    viewModel: ProductViewModel,
    onDismiss: () -> Unit,
    onSave: (name: String, imageUri: String?, units: List<ProductUnitEntity>) -> Unit
) {
    var name by remember { mutableStateOf(productWithUnits?.product?.name ?: "") }
    var imageUri by remember { mutableStateOf(productWithUnits?.product?.imageUri) }

    var unitStates by remember {
        mutableStateOf(
            if (productWithUnits != null && productWithUnits.units.isNotEmpty()) {
                productWithUnits.units.map {
                    UnitInputState(
                        id = it.id,
                        unitName = it.unitName,
                        priceStr = it.price.toString(),
                        isDefault = it.isDefault
                    )
                }
            } else {
                listOf(UnitInputState(unitName = "", priceStr = "", isDefault = true))
            }
        )
    }

    var nameError by remember { mutableStateOf(false) }
    var unitsError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val permanentUri = viewModel.saveImagePermanently(uri)
            if (permanentUri != null) {
                imageUri = permanentUri
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (productWithUnits == null) "إضافة منتج جديد" else "تعديل المنتج")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Photo picker with internal persistence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = Uri.parse(imageUri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "اختر صورة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (name.isNotBlank()) nameError = false
                    },
                    label = { Text("اسم المنتج (مثال: بيبسي 250) *") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError) {
                    Text(
                        text = "الرجاء إدخال اسم المنتج",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "وحدات البيع والأسعار",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            unitStates = unitStates + UnitInputState(
                                unitName = "",
                                priceStr = "",
                                isDefault = unitStates.isEmpty()
                            )
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة وحدة")
                    }
                }

                unitStates.forEachIndexed { index, unitState ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = unitState.unitName,
                                    onValueChange = { newName ->
                                        unitStates = unitStates.mapIndexed { i, item ->
                                            if (i == index) item.copy(unitName = newName) else item
                                        }
                                        unitsError = null
                                    },
                                    label = { Text("الوحدة (مثال: كارتونة)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedTextField(
                                    value = unitState.priceStr,
                                    onValueChange = { newPrice ->
                                        val digitsOnly = newPrice.filter { it.isDigit() }
                                        unitStates = unitStates.mapIndexed { i, item ->
                                            if (i == index) item.copy(priceStr = digitsOnly) else item
                                        }
                                        unitsError = null
                                    },
                                    label = { Text("السعر د.ع") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                if (unitStates.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val remaining = unitStates.filterIndexed { i, _ -> i != index }
                                            unitStates = if (unitState.isDefault && remaining.isNotEmpty()) {
                                                remaining.mapIndexed { i, item ->
                                                    if (i == 0) item.copy(isDefault = true) else item
                                                }
                                            } else {
                                                remaining
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "حذف الوحدة",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = unitState.isDefault,
                                    onClick = {
                                        unitStates = unitStates.mapIndexed { i, item ->
                                            item.copy(isDefault = (i == index))
                                        }
                                    }
                                )
                                Text(
                                    text = if (unitState.isDefault) "الوحدة الافتراضية للبيع" else "تعيين كوحدة افتراضية",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (unitState.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (unitsError != null) {
                    Text(
                        text = unitsError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    if (unitStates.isEmpty()) {
                        unitsError = "الرجاء إضافة وحدة بيع واحدة على الأقل"
                        return@Button
                    }
                    val invalidUnit = unitStates.find { it.unitName.isBlank() || it.priceStr.toLongOrNull() == null }
                    if (invalidUnit != null) {
                        unitsError = "الرجاء كتابة اسم الوحدة وسعر صحيح لكل الوحدات"
                        return@Button
                    }

                    val finalUnits = unitStates.map { state ->
                        ProductUnitEntity(
                            id = state.id,
                            productId = productWithUnits?.product?.id ?: 0L,
                            unitName = state.unitName.trim(),
                            price = state.priceStr.toLong(),
                            isDefault = state.isDefault,
                            minQuantity = 1
                        )
                    }
                    onSave(name, imageUri, finalUnits)
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
