package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.example.R
import com.example.BuildConfig
import com.example.data.db.CustomErrorEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.model.*
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.focus.onFocusChanged

// Custom Theme Colors matching Codyar HTML
import androidx.compose.material3.MaterialTheme
val CodyarNavy: Color @Composable get() = MaterialTheme.colorScheme.primary
val CodyarRed: Color @Composable get() = MaterialTheme.colorScheme.secondary
val CodyarBg: Color @Composable get() = MaterialTheme.colorScheme.background
val CodyarSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val CodyarOnSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val CodyarBorder: Color @Composable get() = MaterialTheme.colorScheme.outline
val CodyarTextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val CodyarTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

fun normalizePersian(input: String?): String {
    if (input == null) return ""
    return input.trim()
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("ة", "ه")
        .replace("\\s+".toRegex(), " ")
}

val voiceWordMapping = mapOf(
    "صفر" to "0",
    "یک" to "1",
    "دو" to "2",
    "سه" to "3",
    "چهار" to "4",
    "پنج" to "5",
    "شش" to "6",
    "شیش" to "6",
    "هفت" to "7",
    "هشت" to "8",
    "نه" to "9",
    "ده" to "10",
    "ای" to "e",
    "اِی" to "e",
    "یی" to "e",
    "اف" to "f",
    "اِف" to "f",
    "پی" to "p",
    "دی" to "d",
    "سی" to "c",
    "اچ" to "h",
    "اِچ" to "h",
    "یو" to "u",
    "ال" to "l",
    "ار" to "r",
    "او" to "o",
    "تی" to "t",
    "آی" to "i",
    "س" to "c",
    "ف" to "f",
    "پ" to "p"
)

fun normalizeVoiceSearchText(text: String): String {
    if (text.isEmpty()) return ""
    
    // Convert Persian digits to English digits
    var normalized = text
        .replace('۰', '0')
        .replace('۱', '1')
        .replace('۲', '2')
        .replace('۳', '3')
        .replace('۴', '4')
        .replace('۵', '5')
        .replace('۶', '6')
        .replace('۷', '7')
        .replace('۸', '8')
        .replace('۹', '9')
    
    val noiseWords = listOf("ارور", "خطای", "خطا", "کد", "کدهای")
    val words = normalized.split(Regex("\\s+"))
    val resultWords = mutableListOf<String>()
    
    for (word in words) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty() || noiseWords.contains(cleanWord)) {
            continue
        }
        
        val mapped = voiceWordMapping[cleanWord.lowercase()]
        if (mapped != null) {
            resultWords.add(mapped)
        } else {
            resultWords.add(cleanWord)
        }
    }
    
    val sb = java.lang.StringBuilder()
    for (i in resultWords.indices) {
        val current = resultWords[i]
        sb.append(current)
        if (i < resultWords.size - 1) {
            val next = resultWords[i + 1]
            val currentIsShort = current.length == 1 || (current.all { it.isDigit() || (it in 'a'..'z') || (it in 'A'..'Z') })
            val nextIsShort = next.length == 1 || (next.all { it.isDigit() || (it in 'a'..'z') || (it in 'A'..'Z') })
            if (!(currentIsShort && nextIsShort)) {
                sb.append(" ")
            }
        }
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onPurchasePlan: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                val normalizedText = normalizeVoiceSearchText(spokenText)
                viewModel.updateSearchFilters(normalizedText, viewModel.selectedBrand.value, viewModel.selectedCategory.value)
            }
        }
    }

    // Screen navigation state
    // "home", "search", "problems", "store", "profile", "technicians", "orders", "ai_chat"
    var activeTab by remember { mutableStateOf("home") }
    var showSplashScreen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Absolute maximum splash screen duration of 5 seconds to prevent being stuck on slow connections
        kotlinx.coroutines.delay(5000)
        showSplashScreen = false
    }

    // Dialog & Sheet states
    var showAuthDialog by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf("login") } // "login" or "register"

    var showCartDialog by remember { mutableStateOf(false) }
    var cartStep by remember { mutableStateOf("cart") } // "cart" or "payment"

    var showPlansDialog by remember { mutableStateOf(false) }

    // Detail view states
    var selectedErrorDetail by remember { mutableStateOf<KodyarErrorCode?>(null) }
    var selectedProblemDetail by remember { mutableStateOf<KodyarCommonProblem?>(null) }

    // Restricted access dialogs
    var showRegisterRequiredDialog by remember { mutableStateOf(false) }
    var showPremiumRequiredDialog by remember { mutableStateOf(false) }

    // Form inputs
    var authPhone by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authName by remember { mutableStateOf("") }
    var authRole by remember { mutableStateOf("customer") } // "customer" or "technician"
    var authCity by remember { mutableStateOf("تهران") }
    var authSelectedCategories by remember { mutableStateOf(setOf<String>()) }

    var cartAddress by remember { mutableStateOf("") }
    var cartNotes by remember { mutableStateOf("") }
    var cartCardHolder by remember { mutableStateOf("") }
    var cartTrackNumber by remember { mutableStateOf("") }

    // Live variables from ViewModel
    val currentUser by viewModel.currentUser.collectAsState()
    val isDatabaseLoading by viewModel.isDatabaseLoading.collectAsState()
    LaunchedEffect(isDatabaseLoading) {
        if (!isDatabaseLoading) {
            // Dismiss after a nice, short delay once database is loaded (cache or network)
            kotlinx.coroutines.delay(1200)
            showSplashScreen = false
        }
    }
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    val liveSpareParts by viewModel.liveSpareParts.collectAsState()
    val liveTechnicians by viewModel.liveTechnicians.collectAsState()
    val liveCommonProblems by viewModel.liveCommonProblems.collectAsState()

    val cartItemsList by viewModel.cart.collectAsState()
    val cartQtyMap by viewModel.cartQty.collectAsState()
    val isPurchaseLoading by viewModel.isPurchaseLoading.collectAsState()
    val purchaseSuccess by viewModel.purchaseSuccess.collectAsState()

    val repairOrders by viewModel.repairOrders.collectAsState()
    val isRepairsLoading by viewModel.isRepairsLoading.collectAsState()

    val isCardVerifyLoading by viewModel.isCardVerifyLoading.collectAsState()
    val cardVerifySuccess by viewModel.cardVerifySuccess.collectAsState()

    val freeErrorCount by viewModel.freeErrorCount.collectAsState()
    val freeProblemCount by viewModel.freeProblemCount.collectAsState()

    val subscriptionPlans by viewModel.subscriptionPlans.collectAsState()
    val isPlansLoading by viewModel.isPlansLoading.collectAsState()
    val appUpdateNotification by viewModel.appUpdateNotification.collectAsState()

    LaunchedEffect(showPlansDialog) {
        if (showPlansDialog) {
            viewModel.fetchSubscriptionPlans()
        }
    }

    val isPremium = currentUser?.subscription?.is_premium == true

    // Trigger RTL context for Persian/Arabic UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = modifier
                    .fillMaxSize()
                    .background(CodyarBg),
            topBar = {
                Surface(
                    color = CodyarNavy,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(CodyarRed, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "کدیار۲۴",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "لوازم خانگی",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPremium) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFC9A227), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "ویژه",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Cart Button
                            Box {
                                IconButton(
                                    onClick = {
                                        if (cartItemsList.isNotEmpty()) {
                                            cartStep = "cart"
                                            showCartDialog = true
                                        } else {
                                            activeTab = "store"
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = "سبد خرید",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                if (cartItemsList.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = (-3).dp, y = (-3).dp)
                                            .background(CodyarRed, CircleShape)
                                            .size(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cartItemsList.size.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Profile Button
                            IconButton(
                                onClick = {
                                    if (currentUser != null) {
                                        activeTab = "profile"
                                    } else {
                                        authMode = "login"
                                        showAuthDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "پروفایل",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            },
           bottomBar = {
                // منوی ناوبری مدرن با گوشه‌های کاملاً گرد، دایره‌های هم‌اندازه و ارتفاع بسیار ظریف
                Surface(
                    color = Color.White,
                    tonalElevation = 0.dp, // غیرفعال کردن سایه رنگی خودکار اندروید جهت سفید ماندن پس‌زمینه
                    shadowElevation = 4.dp, // افکت سایه ملایم و شیک بالا
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), // گرد کردن گوشه‌های بالای منو
                    border = BorderStroke(1.dp, Color(0xFFEAECEF).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding() // اعمال پدینگ سیستمی بر روی ردیف داخلی جهت چسبیدن پس‌زمینه سفید به پایین گوشی
                            .padding(top = 2.dp, bottom = 0.dp) // پدینگ عمودی بسیار فشرده برای کاهش ارتفاع کلی بدون ایجاد فاصله خالی
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val navigationItems = listOf(
                            Triple("home", Icons.Default.Home, "خانه"),
                            Triple("search", Icons.Default.Search, "کد خطا"),
                            Triple("problems", Icons.Default.Warning, "مشکلات"),
                            Triple("store", Icons.Default.ShoppingCart, "فروشگاه"),
                            Triple("profile", Icons.Default.Person, "پروفایل")
                        )

                        navigationItems.forEach { (id, icon, label) ->
                            val active = activeTab == id || (id == "profile" && activeTab == "orders") || (id == "search" && activeTab == "technicians")
                            val itemColor = if (active) CodyarRed else CodyarTextSecondary

                            Column(
                                modifier = Modifier
                                    .weight(1f) // تقسیم متوازن عرض برای تمام آیتم‌ها به یک اندازه
                                    .clip(RoundedCornerShape(12.dp)) // اصلاح شکل لمس کلیدها به حالت دایره‌ای/گرد
                                    .clickable {
                                        selectedErrorDetail = null
                                        selectedProblemDetail = null
                                        if (id == "search") {
                                            viewModel.setShowOnlySaved(false)
                                        }
                                        activeTab = id
                                    }
                                    .padding(vertical = 2.dp), // فشرده‌سازی مناسب و ظریف پدینگ عمودی برای ارتفاع کلی کم
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp) // اندازه بهینه و استاندارد دایره پس‌زمینه آیکون‌ها
                                        .clip(CircleShape)
                                        .background(if (active) CodyarRed.copy(alpha = 0.12f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = itemColor,
                                        modifier = Modifier.size(22.dp) // اندازه کاملا خوانا و مناسب آیکون داخلی
                                    )
                                }
                                Text(
                                    text = label,
                                    fontSize = 10.sp, // قلم استاندارد و کاملاً خوانا
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = itemColor,
                                    modifier = Modifier.padding(top = 2.dp) // فاصله ظریف و مناسب تا دایره بالا
                                )
                            }
                        }
                    }
                }
            }) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CodyarBg)
            ) {
                when (activeTab) {
                        "home" -> HomeScreen(
                            viewModel = viewModel,
                            onNavigateToSearch = { activeTab = "search" },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            onNavigateToStore = { activeTab = "store" },
                            onShowPlans = { showPlansDialog = true },
                            onOpenErrorCode = { err ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueErrorCodesViewed()
                                    val codeKey = "${err.brand}_${err.category}_${err.code}"
                                    if (isPremium || viewedSet.contains(codeKey) || viewedSet.size < 2) {
                                        viewModel.recordErrorCodeView(codeKey)
                                        selectedErrorDetail = err
                                        activeTab = "search"
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            }
                        )
                        "search" -> SearchScreen(
                            viewModel = viewModel,
                            selectedErrorDetail = selectedErrorDetail,
                            onSelectError = { err ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueErrorCodesViewed()
                                    val codeKey = "${err.brand}_${err.category}_${err.code}"
                                    if (isPremium || viewedSet.contains(codeKey) || viewedSet.size < 2) {
                                        viewModel.recordErrorCodeView(codeKey)
                                        selectedErrorDetail = err
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            },
                            onBack = { selectedErrorDetail = null },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            onNavigateToStore = { activeTab = "store" },
                            isPremium = isPremium,
                            freeErrorCount = freeErrorCount,
                            onShowPlans = { showPlansDialog = true }
                        )
                        "problems" -> ProblemsScreen(
                            viewModel = viewModel,
                            liveProblems = viewModel.liveCommonProblems,
                            selectedProblemDetail = selectedProblemDetail,
                            onSelectProblem = { prob ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueProblemsViewed()
                                    val problemKey = "${prob.brand}_${prob.category}_${prob.title}"
                                    if (isPremium || viewedSet.contains(problemKey) || viewedSet.size < 1) {
                                        viewModel.recordProblemView(problemKey)
                                        selectedProblemDetail = prob
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            },
                            onBack = { selectedProblemDetail = null },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            isPremium = isPremium,
                            freeProblemCount = freeProblemCount,
                            onShowPlans = { showPlansDialog = true }
                        )
                        "store" -> StoreScreen(
                            viewModel = viewModel,
                            parts = liveSpareParts,
                            cartItems = cartItemsList,
                            onAddToCart = { viewModel.addToCart(it) }
                        )
                        "profile" -> ProfileScreen(
                            viewModel = viewModel,
                            currentUser = currentUser,
                            onShowAuth = {
                                authMode = "login"
                                showAuthDialog = true
                            },
                            onShowPlans = { showPlansDialog = true },
                            onNavigateToOrders = {
                                viewModel.loadRepairs()
                                activeTab = "orders"
                            },
                            onNavigateToSaved = {
                                viewModel.setShowOnlySaved(true)
                                activeTab = "search"
                            }
                        )
                        "technicians" -> TechniciansScreen(
                            viewModel = viewModel,
                            liveTechs = viewModel.liveTechnicians,
                            currentUser = currentUser,
                            onShowAuth = {
                                authMode = "login"
                                showAuthDialog = true
                            }
                        )
                        "orders" -> OrdersScreen(
                            viewModel = viewModel,
                            repairOrders = repairOrders,
                            isRepairsLoading = isRepairsLoading,
                            onBack = { activeTab = "profile" },
                            onNavigateToTechs = { activeTab = "technicians" }
                        )
                        "ai_chat" -> AiChatScreen(viewModel = viewModel)
                    }
                }
            }

        // --- AUTH DIALOG ---
        if (showAuthDialog) {
            AlertDialog(
                onDismissRequest = { showAuthDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (authMode == "login") "ورود به حساب کاربری" else "ثبت‌نام حساب جدید",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { showAuthDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Toggle bar for Login / Register
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp))
                                .padding(3.dp)
                        ) {
                            Button(
                                onClick = { authMode = "login" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authMode == "login") Color.White else Color.Transparent,
                                    contentColor = if (authMode == "login") CodyarTextPrimary else CodyarTextSecondary
                                ),
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("ورود", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { authMode = "register" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authMode == "register") Color.White else Color.Transparent,
                                    contentColor = if (authMode == "register") CodyarTextPrimary else CodyarTextSecondary
                                ),
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("ثبت‌نام", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Sub-toggle for Customer / Technician registration
                        if (authMode == "register") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(9.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(9.dp))
                                    .padding(3.dp)
                            ) {
                                Button(
                                    onClick = { authRole = "customer" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (authRole == "customer") CodyarRed else Color.Transparent,
                                        contentColor = if (authRole == "customer") Color.White else Color(0xFF64748B)
                                    ),
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("ثبت‌نام مشتری", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { authRole = "technician" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (authRole == "technician") CodyarNavy else Color.Transparent,
                                        contentColor = if (authRole == "technician") Color.White else Color(0xFF64748B)
                                    ),
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("ثبت‌نام تکنسین", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        if (authMode == "register") {
                            OutlinedTextField(
                                value = authName,
                                onValueChange = { authName = it },
                                placeholder = { Text("نام و نام خانوادگی") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(9.dp),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = authPhone,
                            onValueChange = { authPhone = it },
                            placeholder = { Text("شماره موبایل (مثلا: 0912...)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(9.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )

                        OutlinedTextField(
                            value = authPassword,
                            onValueChange = { authPassword = it },
                            placeholder = { Text("رمز عبور") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(9.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        // City field for both customer and technician registration
                        if (authMode == "register") {
                            var cityExpanded by remember { mutableStateOf(false) }
                            val liveCitiesList by viewModel.liveCities.collectAsState()
                            val filteredCities = remember(authCity, liveCitiesList) {
                                val cleanQuery = authCity.trim().lowercase()
                                if (cleanQuery.isEmpty()) {
                                    liveCitiesList.filter { it != "همه" }
                                } else {
                                    liveCitiesList.filter { it != "همه" && it.lowercase().contains(cleanQuery) }
                                }
                            }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = authCity,
                                    onValueChange = { 
                                        authCity = it
                                        cityExpanded = true
                                    },
                                    placeholder = { Text(if (authRole == "technician") "شهر / محله محل فعالیت (انتخاب از لیست)" else "شهر / محله محل سکونت (انتخاب از لیست)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                cityExpanded = true
                                            }
                                        },
                                    shape = RoundedCornerShape(9.dp),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { cityExpanded = !cityExpanded }) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                                                contentDescription = "انتخاب شهر ومحله"
                                            )
                                        }
                                    }
                                )
                                
                                if (cityExpanded && filteredCities.isNotEmpty()) {
                                    DropdownMenu(
                                        expanded = cityExpanded,
                                        onDismissRequest = { cityExpanded = false },
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)
                                    ) {
                                        filteredCities.forEach { cityItem ->
                                            DropdownMenuItem(
                                                text = { Text(cityItem, fontSize = 12.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                                onClick = {
                                                    authCity = cityItem
                                                    cityExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Technician-specific fields (categories)
                        if (authMode == "register" && authRole == "technician") {

                            val dbCategories by viewModel.liveCategories.collectAsState()
                            val availableCategories = remember(dbCategories) {
                                dbCategories.filter { it != "همه" }.ifEmpty {
                                    listOf("ماشین لباسشویی", "ماشین ظرفشویی", "یخچال و فریزر", "مایکروویو", "جاروبرقی", "کولر گازی")
                                }
                            }
                            Text("انتخاب تخصص‌ها:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CodyarNavy)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableCategories.forEach { cat ->
                                    val isSelected = authSelectedCategories.contains(cat)
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isSelected) CodyarRed else Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) CodyarRed else Color(0xFFCBD5E1),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                authSelectedCategories = if (isSelected) {
                                                    authSelectedCategories - cat
                                                } else {
                                                    authSelectedCategories + cat
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else Color(0xFF475569),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (authError != null) {
                            Text(
                                text = authError ?: "",
                                color = CodyarRed,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFDF0EE), RoundedCornerShape(7.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (authPhone.isBlank() || authPassword.isBlank()) {
                                Toast.makeText(context, "لطفاً تمام موارد را پر کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (authMode == "login") {
                                viewModel.login(authPhone, authPassword) { success, err ->
                                    if (success) {
                                        showAuthDialog = false
                                        Toast.makeText(context, "خوش آمدید!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                if (authName.isBlank()) {
                                    Toast.makeText(context, "نام الزامی است", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (authCity.isBlank()) {
                                    Toast.makeText(context, "لطفاً شهر خود را وارد کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (authRole == "technician" && authSelectedCategories.isEmpty()) {
                                    Toast.makeText(context, "لطفاً حداقل یک تخصص انتخاب کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.register(
                                    phone = authPhone,
                                    pass = authPassword,
                                    name = authName,
                                    role = authRole,
                                    city = authCity.trim(),
                                    categories = if (authRole == "technician") authSelectedCategories.toList() else null
                                ) { success, err ->
                                    if (success) {
                                        showAuthDialog = false
                                        val welcomeMsg = if (authRole == "technician") {
                                            "ثبت‌نام تکنسین با موفقیت انجام شد! حساب شما فعال است."
                                        } else {
                                            "ثبت‌نام مشتری با موفقیت انجام شد!"
                                        }
                                        Toast.makeText(context, welcomeMsg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_button"),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isAuthLoading
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = if (authMode == "login") "ورود به حساب" else if (authRole == "technician") "ثبت‌نام تکنسین" else "ثبت‌نام مشتری",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }
        // --- CART DIALOG ---
        if (showCartDialog) {
            AlertDialog(
                onDismissRequest = { showCartDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سبد خرید قطعات شما",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { showCartDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (cartItemsList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("🛒", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("سبد خرید شما خالی است", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        } else {
                            cartItemsList.forEach { partId ->
                                val part = liveSpareParts.find { it.id == partId }
                                if (part != null) {
                                    val qty = cartQtyMap[partId] ?: 1
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .background(CodyarSurface, RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val finalImg = part.image ?: part.imageUrl ?: ""
                                                if (finalImg.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = if (finalImg.startsWith("http")) finalImg else "https://kodyar24.ir/${finalImg.removePrefix("/")}",
                                                        contentDescription = part.name,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                } else {
                                                    Text("⚙️", fontSize = 24.sp)
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    part.name ?: "",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = CodyarTextPrimary
                                                )
                                                Text(
                                                    "${formatToman(part.price ?: 0.0)} تومان",
                                                    fontSize = 11.sp,
                                                    color = CodyarTextSecondary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { viewModel.updateCartQty(partId, qty - 1) },
                                                        modifier = Modifier
                                                            .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(5.dp))
                                                            .size(26.dp)
                                                    ) {
                                                        Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Text(
                                                        text = qty.toString(),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        modifier = Modifier.width(22.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    IconButton(
                                                        onClick = { viewModel.updateCartQty(partId, qty + 1) },
                                                        modifier = Modifier
                                                            .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(5.dp))
                                                            .size(26.dp)
                                                    ) {
                                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    TextButton(
                                                        onClick = { viewModel.removeFromCart(partId) },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = CodyarRed)
                                                    ) {
                                                        Text("حذف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val total = cartItemsList.sumOf { partId ->
                                val part = liveSpareParts.find { it.id == partId }
                                val qty = cartQtyMap[partId] ?: 1
                                (part?.price ?: 0.0) * qty
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("جمع کل اقلام:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${formatToman(total)} تومان",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = CodyarRed
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "🛍️ تکمیل خرید در وب‌سایت کدیار۲۴",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E40AF)
                                    )
                                    Text(
                                        text = "قطعات انتخابی شما در سبد خرید ذخیره شد. برای پرداخت (زرین‌پال یا کارت‌به‌کارت) و ثبت نهایی سفارش، به وب‌سایت رسمی کدیار۲۴ مراجعه کنید.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E40AF),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (cartItemsList.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (currentUser == null) {
                                    Toast.makeText(context, "لطفاً ابتدا وارد حساب کاربری خود شوید.", Toast.LENGTH_SHORT).show()
                                    showAuthDialog = true
                                } else {
                                    try {
                                        val token = viewModel.getSessionToken() ?: ""
                                        val phone = currentUser?.phone ?: ""
                              val webUri = "https://kodyar24.ir/?entry=client&token=$token#parts-store-section"
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(webUri))
                                        context.startActivity(intent)
                                        showCartDialog = false
                                        Toast.makeText(context, "برای تکمیل خرید و پرداخت به وب‌سایت کدیار۲۴ منتقل شدید.", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_order_button"),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Text("تکمیل خرید در وب‌سایت 🌐", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { showCartDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("بستن", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        // --- SUBSCRIPTION PLANS DIALOG ---
        if (showPlansDialog) {
            val appliedReferralCode by viewModel.appliedReferralCode.collectAsState()
            val referralDiscountPercent by viewModel.referralDiscountPercent.collectAsState()

            val displayPlans = listOf(
                mapOf(
                    "id" to "ir.golden.com",
                    "name" to "اشتراک ۳۰ روزه",
                    "price" to 120000.0,
                    "tag" to "",
                    "description" to "دسترسی نامحدود به کدهای خطا و راهنمای عیب‌یابی به مدت ۳۰ روز کامل"
                ),
                mapOf(
                    "id" to "ir.silver.com",
                    "name" to "اشتراک ۹۰ روزه",
                    "price" to 290000.0,
                    "tag" to "",
                    "description" to "دسترسی نامحدود به کدهای خطا و راهنمای عیب‌یابی به مدت ۹۰ روز"
                ),
                mapOf(
                    "id" to "ir.almas.com",
                    "name" to "اشتراک ۱۸۰ روزه",
                    "price" to 490000.0,
                    "tag" to "بهترین ارزش",
                    "description" to "دسترسی نامحدود به کدهای خطا و راهنمای عیب‌یابی به مدت ۱۸۰ روز"
                ),
                mapOf(
                    "id" to "ir.12-month.com",
                    "name" to "اشتراک ۳۶۵ روزه",
                    "price" to 790000.0,
                    "tag" to "خرید به صرفه",
                    "description" to "دسترسی نامحدود به کدهای خطا و بخش‌های برنامه کدیار۲۴ به مدت ۳۶۵ روز"
                )
            )

            AlertDialog(
                onDismissRequest = { showPlansDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "پکیج اشتراک کدیار۲۴",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { showPlansDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "جهت دسترسی کامل به تمامی بخش‌های برنامه، کدهای خطا و راهنمای عیب‌یابی، یکی از پکیج‌های زیر را تهیه کنید.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CodyarNavy,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CodyarBg, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        )

                        // Referral/Coupon Code Section
                        var inviteCodeInput by remember { mutableStateOf("") }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CodyarBg),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "کد تخفیف / معرف دارید؟",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = CodyarNavy
                                )
                                if (appliedReferralCode != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "کد اعمال شده: $appliedReferralCode (تخفیف ${referralDiscountPercent}٪)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E8449)
                                        )
                                        TextButton(
                                            onClick = { viewModel.removeReferralCode() },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("حذف کد", fontSize = 11.sp, color = CodyarRed)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = inviteCodeInput,
                                            onValueChange = { inviteCodeInput = it },
                                            placeholder = { Text("کد معرف یا تخفیف", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1.5f).height(48.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = CodyarNavy,
                                                unfocusedBorderColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (inviteCodeInput.isNotBlank()) {
                                                    val success = viewModel.applyReferralCode(inviteCodeInput)
                                                    if (success) {
                                                        Toast.makeText(context, "کد معرف با ۲۵٪ تخفیف اعمال شد", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "کد معرف معتبر نیست (حداقل ۴ کاراکتر)", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(0.8f).height(42.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("اعمال کد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (isPlansLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CodyarNavy)
                            }
                        } else {
                            displayPlans.forEach { plan ->
                                val hasTag = (plan["tag"] as String).isNotEmpty()
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = plan["name"] as String,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = CodyarNavy
                                                )
                                                if (hasTag) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(CodyarRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = plan["tag"] as String,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = CodyarRed
                                                        )
                                                    }
                                                }
                                            }

                                            val origPrice = plan["price"] as Double
                                            val discountedPrice = if (referralDiscountPercent > 0) {
                                                origPrice * (1.0 - (referralDiscountPercent / 100.0))
                                            } else {
                                                origPrice
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                if (referralDiscountPercent > 0) {
                                                    Text(
                                                        text = "${formatToman(origPrice)} ت",
                                                        fontWeight = FontWeight.Normal,
                                                        fontSize = 11.sp,
                                                        color = Color.Gray,
                                                        style = androidx.compose.ui.text.TextStyle(
                                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                                        )
                                                    )
                                                }
                                                Text(
                                                    text = "${formatToman(discountedPrice)} ت",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (referralDiscountPercent > 0) Color(0xFF1E8449) else CodyarRed
                                                )
                                            }
                                        }

                                        val desc = plan["description"] as? String
                                        if (!desc.isNullOrEmpty()) {
                                            Text(
                                                text = desc,
                                                fontSize = 11.sp,
                                                color = CodyarTextSecondary,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (currentUser == null) {
                                                    showPlansDialog = false
                                                    authMode = "login"
                                                    showAuthDialog = true
                                                    Toast.makeText(context, "ابتدا وارد حساب کاربری شوید", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                
                                                // Check if Cafe Bazaar is installed on the device
                                                val hasBazaar = try {
                                                    context.packageManager.getPackageInfo("com.farsitel.bazaar", 0)
                                                    true
                                                } catch (e: Exception) {
                                                    try {
                                                        context.packageManager.getPackageInfo("ir.cafebazaar.pardakht", 0)
                                                        true
                                                    } catch (e2: Exception) {
                                                        false
                                                    }
                                                }

                                                if (hasBazaar) {
                                                    showPlansDialog = false
                                                    onPurchasePlan(plan["id"] as String)
                                                } else {
                                                    Toast.makeText(context, "برنامه بازار بر روی این دستگاه نصب نیست. لطفاً ابتدا بازار را نصب کنید.", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = CodyarNavy
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ShoppingCart,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color.White
                                                )
                                                Text(
                                                    text = "خرید و فعال‌سازی اشتراک از بازار",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Manual payment option removed to fully comply with Cafe Bazaar single in-app billing policies


        // --- LIVE WEBSITE UPDATE NOTIFICATION DIALOG ---
        if (appUpdateNotification != null) {
            val updates = appUpdateNotification!!
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateNotification() },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(CodyarNavy.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = CodyarNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "اطلاعات جدید در سایت کدیار۲۴",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CodyarNavy
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "هم‌اکنون کدهای خطا، مشکلات فنی یا قطعات جدیدی در وب‌سایت ثبت شده و به صورت آنلاین در اپلیکیشن هماهنگ و لود گردید:",
                            fontSize = 12.sp,
                            color = CodyarTextSecondary,
                            lineHeight = 18.sp
                        )

                        // 1. New Error Codes
                        if (updates.newErrors.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🔧", fontSize = 14.sp)
                                    Text("کدهای خطای جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E8449))
                                }
                                updates.newErrors.forEach { error ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "کد ${error.code} (${error.brand})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF14532D)
                                            )
                                            if (!error.title.isNullOrBlank()) {
                                                Text(
                                                    text = error.title,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF166534),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. New Common Problems
                        if (updates.newProblems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("💡", fontSize = 14.sp)
                                    Text("مشکلات و ایرادات فنی جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                }
                                updates.newProblems.forEach { prob ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = prob.title ?: "",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF78350F)
                                            )
                                            if (!prob.brand.isNullOrBlank() || !prob.category.isNullOrBlank()) {
                                                Text(
                                                    text = "دستگاه: ${prob.brand ?: ""} - ${prob.category ?: ""}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF92400E),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. New Spare Parts / Products
                        if (updates.newParts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("📦", fontSize = 14.sp)
                                    Text("محصولات و قطعات یدکی جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                }
                                updates.newParts.forEach { part ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = part.name ?: "",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF1E3A8A)
                                            )
                                            Text(
                                                text = "برند: ${part.brand ?: ""} | قیمت: ${formatToman(part.price ?: 0.0)} تومان",
                                                fontSize = 11.sp,
                                                color = Color(0xFF1E40AF),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissUpdateNotification() },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("متوجه شدم و بررسی کدهای جدید", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
        }

        // --- REGISTRATION REQUIRED DIALOG ---
        if (showRegisterRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showRegisterRequiredDialog = false },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "ثبت‌نام یا ورود به حساب کاربری",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "برای مشاهده جزئیات کدهای خطا، مشکلات و راهکارهای تخصصی تعمیر، ثبت‌نام یا ورود به حساب کاربری الزامی است.",
                            fontSize = 13.sp,
                            color = CodyarTextSecondary,
                            textAlign = TextAlign.Right
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRegisterRequiredDialog = false
                            authMode = "register"
                            showAuthDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ثبت‌نام / ورود", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRegisterRequiredDialog = false }) {
                        Text("انصراف", color = Color(0xFF718096))
                    }
                }
            )
        }

        // --- PREMIUM REQUIRED DIALOG ---
        if (showPremiumRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showPremiumRequiredDialog = false },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "ارتقای اشتراک",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "شما به سقف محدودیت مشاهده رایگان رسیده‌اید. برای دسترسی نامحدود به تمامی کدهای خطا، مشکلات و راهکارهای وب‌سایت کدیار۲۴، لطفا اشتراک خود را ارتقا دهید و پس از تایید مدیریت دسترسی کامل شما فعال خواهد شد.",
                            fontSize = 13.sp,
                            color = CodyarTextSecondary,
                            textAlign = TextAlign.Right
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPremiumRequiredDialog = false
                            showPlansDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("خرید اشتراک کدیار۲۴", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPremiumRequiredDialog = false }) {
                        Text("انصراف", color = Color(0xFF718096))
                    }
                }
            )
        }

        if (showSplashScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CodyarNavy),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "کدیار۲۴",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "کدیار۲۴",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "دستیار هوشمند و تخصصی تعمیرات لوازم خانگی",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(200.dp)
                    ) {
                        LinearProgressIndicator(
                            color = CodyarRed,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "در حال بارگذاری اطلاعات...",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "نسخه ۱.۵ • کدیار۲۴",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
        }
    }
}

// --- TAB 1: HOME SCREEN ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: AssistantViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit,
    onShowPlans: () -> Unit,
    onOpenErrorCode: (KodyarErrorCode) -> Unit
) {
    val scrollState = rememberScrollState()
    var homeSearchQuery by remember { mutableStateOf("") }
    val liveErrorCodes by viewModel.liveErrorCodes.collectAsState()
    val liveTechs by viewModel.liveTechnicians.collectAsState()
    val liveParts by viewModel.liveSpareParts.collectAsState()
    val rawCategories by viewModel.liveCategories.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val displayCategories = remember(rawCategories) {
        val filtered = rawCategories.filter { it != "همه" && it.isNotBlank() }
        if (filtered.isEmpty()) {
            listOf("کولر گازی", "یخچال و فریزر", "ماشین لباسشویی", "ماشین ظرفشویی")
        } else {
            filtered
        }
    }

    val displayTechs = remember(liveTechs) {
        if (liveTechs.isEmpty()) {
            listOf(
                KodyarTechnician(id = "1", name = "مهندس براتی", city = "تهران", isVerified = true, completedOrders = 120, bio = "متخصص لباسشویی و ظرفشویی", categories = listOf("ماشین لباسشویی"), rating = 4.9, satisfactionRate = 98),
                KodyarTechnician(id = "2", name = "مهندس خمسه", city = "کرج", isVerified = true, completedOrders = 95, bio = "متخصص یخچال و فریزر", categories = listOf("یخچال و فریزر"), rating = 4.8, satisfactionRate = 96),
                KodyarTechnician(id = "3", name = "مهندس شفتی", city = "اصفهان", isVerified = true, completedOrders = 150, bio = "تکنسین پکیج و کولر گازی", categories = listOf("کولر گازی"), rating = 4.9, satisfactionRate = 99)
            )
        } else {
            liveTechs.sortedWith(
                compareByDescending<KodyarTechnician> { it.rating ?: 0.0 }
                    .thenByDescending { it.satisfactionRate ?: 0 }
                    .thenByDescending { it.completedOrders ?: 0 }
            ).take(5)
        }
    }

    val defaultCatTechMap = remember {
        mapOf(
            "کولر گازی" to "سید کامران شفتی",
            "یخچال و فریزر" to "مبین عباسی",
            "ماشین لباسشویی" to "جلال سراوانی",
            "ماشین ظرفشویی" to "فرید سیفی زاده",
            "پکیج دیواری" to "مهندس علیزاده",
            "جاروبرقی" to "مهندس رضایی",
            "مایکروویو" to "مهندس کریمی"
        )
    }

    val avatars = remember {
        listOf(
            "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1566492031773-4f4e44671857?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=150&h=150&fit=crop"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Hero search banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodyarNavy)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مرجع عیب‌یابی لوازم خانگی",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "از کد خطا تا راه حل، هوشمند و سریع",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 28.sp
                        )
                    }
                    
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val shareMessage = if (currentUser?.role == "technician") {
                                "سلام همکار گرامی!\nبرنامه تخصصی کدیار۲۴ (مرجع عیب‌یابی هوشمند و کدهای خطای لوازم خانگی مخصوص تکنسین‌ها و تعمیرکاران) رو دانلود کن و همیشه همراهت داشته باش:\nhttps://kodyar24.ir/download/app.apk"
                            } else {
                                "سلام!\nاگر برای عیب‌یابی لوازم خانگی خونت به یک دستیار هوشمند و سریع نیاز داری، اپلیکیشن کدیار۲۴ رو از لینک زیر دانلود کن:\nhttps://kodyar24.ir/download/app.apk"
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "دانلود اپلیکیشن کدیار۲۴")
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری کدیار۲۴ از طریق:"))
                        },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "اشتراک‌گذاری برنامه",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Search field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodyarSurface, RoundedCornerShape(12.dp))
                        .padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجو",
                        tint = CodyarTextSecondary,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 8.dp)
                            .size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (homeSearchQuery.isEmpty()) {
                            Text(
                                "مثلاً: E1، F2، بوتان...",
                                color = Color(0xFFB0B8C4),
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = homeSearchQuery,
                            onValueChange = { homeSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp,
                                color = CodyarTextPrimary,
                                textAlign = TextAlign.Start
                            )
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.updateSearchFilters(homeSearchQuery, "همه", "همه")
                            onNavigateToSearch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("جستجو", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Stats card (Floating offset over hero)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset(y = (-20).dp),
            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFEAECEF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stat 1
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔴", fontSize = 17.sp)
                    Text(
                        text = if (liveErrorCodes.isNotEmpty()) "${liveErrorCodes.size}+" else "۵۰۰+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarTextPrimary
                    )
                    Text("کد خطا", fontSize = 10.sp, color = CodyarTextSecondary)
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEAECEF))
                        .width(1.dp)
                        .height(35.dp)
                )

                // Stat 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔧", fontSize = 17.sp)
                    Text(
                        text = if (liveTechs.isNotEmpty()) "${liveTechs.size}+" else "۲۰۰+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarTextPrimary
                    )
                    Text("تکنسین", fontSize = 10.sp, color = CodyarTextSecondary)
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEAECEF))
                        .width(1.dp)
                        .height(35.dp)
                )

                // Stat 3
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📦", fontSize = 17.sp)
                    Text(
                        text = if (liveParts.isNotEmpty()) "${liveParts.size}+" else "۱۰۰۰+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarTextPrimary
                    )
                    Text("قطعه", fontSize = 10.sp, color = CodyarTextSecondary)
                }
            }
        }

        // Quick action grid (4 actions)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset(y = (-10).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2
        ) {
            val quickActions = listOf(
                QuadAction(
                    icon = Icons.Default.Search,
                    tint = CodyarRed,
                    label = "جستجوی کد خطا",
                    sub = "عیب‌یابی سریع",
                    action = onNavigateToSearch
                ),
                QuadAction(
                    icon = Icons.Default.Build,
                    tint = CodyarNavy,
                    label = "اعزام تکنسین",
                    sub = "در شهر شما",
                    action = onNavigateToTechnicians
                ),
                QuadAction(
                    icon = Icons.Default.ShoppingCart,
                    tint = Color(0xFF1E8449),
                    label = "فروشگاه قطعات",
                    sub = "قطعات اصل",
                    action = onNavigateToStore
                ),
                QuadAction(
                    icon = Icons.Default.Star,
                    tint = Color(0xFFC9A227),
                    label = "اشتراک ویژه",
                    sub = "از ۱۵۰,۰۰۰ تومان",
                    action = onShowPlans
                )
            )

            quickActions.forEach { item ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { item.action() }
                        .padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(0.dp, Color.Transparent)
                            )
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(item.tint.copy(alpha = 0.1f), CircleShape)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = item.tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = item.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary
                            )
                            Text(
                                text = item.sub,
                                fontSize = 10.sp,
                                color = CodyarTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- SECTION: Colleagues & Top Technicians Recommendations ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFEAECEF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with a smiling technician icon
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🔧", fontSize = 16.sp)
                        Text(
                            text = "توصیه‌کنندگان و همکاران برتر",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CodyarTextPrimary
                        )
                    }
                    Text(
                        text = "بانک ارور",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CodyarRed
                    )
                }

                // Horizontal list of circular avatars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayTechs.forEachIndexed { idx, tech ->
                        val avatarUrl = if (!tech.image.isNullOrBlank()) tech.image else if (!tech.imageUrl.isNullOrBlank()) tech.imageUrl else null
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (idx % 2 == 0) CodyarRed else CodyarNavy, CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = tech.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = tech.name,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tech.name ?: "همکار کدیار",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- SECTION: Category recommendations matching user screenshot ---
        Text(
            text = "دسته‌بندی‌های پیشنهادی همکاران",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CodyarTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        val categoryColors = listOf(
            Color(0xFF0EA5E9), // Sky Blue
            Color(0xFF8B5CF6), // Purple
            Color(0xFFF97316), // Orange
            Color(0xFF10B981), // Emerald Green
            Color(0xFFEF4444), // Rose Red
            Color(0xFF06B6D4)  // Cyan
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            displayCategories.forEachIndexed { index, cat ->
                val cardColor = categoryColors[index % categoryColors.size]
                
                val recommendingTech = remember(liveTechs, cat) {
                    val matchingTechs = liveTechs.filter { it.categories?.any { specialty -> specialty.contains(cat, ignoreCase = true) } == true }
                    if (matchingTechs.isNotEmpty()) {
                        matchingTechs.maxByOrNull { (it.rating ?: 0.0) * 100 + (it.satisfactionRate ?: 0) }
                    } else {
                        liveTechs.maxByOrNull { (it.rating ?: 0.0) * 100 + (it.satisfactionRate ?: 0) }
                            ?: KodyarTechnician(id = "fallback", name = defaultCatTechMap[cat] ?: "همکار کدیار", city = "تهران", isVerified = true, completedOrders = 110, bio = "تکنسین برتر", categories = listOf(cat), rating = 4.9, satisfactionRate = 98)
                    }
                }
                
                val techName = recommendingTech?.name ?: defaultCatTechMap[cat] ?: "همکار کدیار"
                val avatarUrl = if (recommendingTech != null && !recommendingTech.image.isNullOrBlank()) recommendingTech.image else if (recommendingTech != null && !recommendingTech.imageUrl.isNullOrBlank()) recommendingTech.imageUrl else null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable {
                            viewModel.updateSearchFilters("", "همه", cat)
                            onNavigateToSearch()
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Technician image on left
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = techName,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(bottomStart = 18.dp, topStart = 18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = techName,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                        // Text details on right
                        Column(
                            modifier = Modifier
                                .weight(1.8f)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "بانک ارور $cat",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "+ $techName پیشنهاد می‌کند",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION: Engaging Share App Banner ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left/Start side: Nice share visual indicator
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFEFF6FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📢", fontSize = 24.sp)
                }

                // Center: Text details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "معرفی کدیار۲۴ به همکاران",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CodyarTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "با ارسال لینک برنامه برای سایر همکاران و تکنسین‌ها، به آنها در حل سریع کدهای خطا کمک کنید.",
                        fontSize = 11.sp,
                        color = CodyarTextSecondary,
                        lineHeight = 16.sp
                    )
                }

                // Right/End side: Styled Button
                val context = LocalContext.current
                Button(
                    onClick = {
                        val shareMessage = if (currentUser?.role == "technician") {
                            "سلام همکار گرامی!\nبرنامه تخصصی کدیار۲۴ (مرجع عیب‌یابی هوشمند و کدهای خطای لوازم خانگی مخصوص تکنسین‌ها و تعمیرکاران) رو دانلود کن و همیشه همراهت داشته باش:\nhttps://kodyar24.ir/download/app.apk"
                        } else {
                            "سلام!\nاگر برای عیب‌یابی لوازم خانگی خونت به یک دستیار هوشمند و سریع نیاز داری، اپلیکیشن کدیار۲۴ رو از لینک زیر دانلود کن:\nhttps://kodyar24.ir/download/app.apk"
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "دانلود اپلیکیشن کدیار۲۴")
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری کدیار۲۴ از طریق:"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("ارسال", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // High frequency errors header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CodyarRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "پرتکرارترین خطاها",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CodyarTextPrimary
                )
            }

            TextButton(onClick = onNavigateToSearch) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("همه", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CodyarRed)
                    Icon(
                        imageVector = Icons.Default.ArrowForward, // Mirrored dynamically
                        contentDescription = null,
                        tint = CodyarRed,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // List of top 5 errors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            liveErrorCodes.take(5).forEach { err ->
                val severityLevel = err.hazardLevel ?: "medium"
                val (color, bg, label) = when (severityLevel) {
                    "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "خطرناک")
                    "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "کم‌خطر")
                    else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenErrorCode(err) },
                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = err.code ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = err.title ?: err.code ?: "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                fontSize = 11.sp,
                                color = CodyarTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(bg, RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

data class QuadAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val label: String,
    val sub: String,
    val action: () -> Unit
)

// --- Helper Functions ---
fun formatToman(price: Double): String {
    return String.format("%,.0f", price)
}

fun convertGregorianToJalali(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    if (dateString.contains("/") && !dateString.startsWith("20")) {
        return dateString
    }
    if (!dateString.any { it.isDigit() }) {
        return dateString
    }
    try {
        val cleanDate = dateString.substringBefore("T").substringBefore(" ").trim()
        val parts = cleanDate.split("-")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull() ?: return dateString
            val month = parts[1].toIntOrNull() ?: return dateString
            val day = parts[2].toIntOrNull() ?: return dateString
            
            val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 335)
            var gy = year - 1600
            var gm = month - 1
            var gd = day - 1

            var gDayNo = 365 * gy + (gy + 4) / 4 - (gy + 100) / 100 + (gy + 400) / 400
            gDayNo += gDaysInMonth[gm]
            if (gm > 1 && ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))) {
                gDayNo++
            }
            gDayNo += gd

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461

            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            var jm = 0
            var jd = 0
            for (i in 0..11) {
                val monthLength = if (i < 6) 31 else if (i < 11) 30 else 29
                if (jDayNo < monthLength) {
                    jm = i + 1
                    jd = jDayNo + 1
                    break
                }
                jDayNo -= monthLength
            }
            
            val persianDigits = listOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
            val rawPersianDate = "$jy/${String.format("%02d", jm)}/${String.format("%02d", jd)}"
            return rawPersianDate.map { char ->
                if (char.isDigit()) persianDigits[char.toString().toInt()] else char
            }.joinToString("")
        }
    } catch (e: Exception) {
        // ignore
    }
    return dateString
}

@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

@Composable
fun FilterDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }

    LaunchedEffect(expanded) {
        if (!expanded) {
            filterText = ""
        }
    }

    val filteredOptions = remember(options, filterText) {
        if (filterText.isEmpty()) {
            options
        } else {
            val normFilter = normalizePersian(filterText)
            options.filter {
                it == "همه" || normalizePersian(it).contains(normFilter, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CodyarTextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedValue,
                    fontSize = 12.sp,
                    color = if (selectedValue == "همه") Color(0xFF64748B) else CodyarTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = CodyarTextSecondary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 280.dp)
                    .background(CodyarSurface)
            ) {
                if (options.size > 5) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("جستجو...", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (filterText.isNotEmpty()) {
                                IconButton(onClick = { filterText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CodyarNavy,
                            unfocusedBorderColor = Color(0xFFDDE1E7),
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        )
                    )
                }

                if (filteredOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "موردی یافت نشد",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        onClick = {}
                    )
                } else {
                    filteredOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )
                            },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- TAB 2: SEARCH / ERROR CODES ---
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AssistantViewModel,
    selectedErrorDetail: KodyarErrorCode?,
    onSelectError: (KodyarErrorCode) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit,
    isPremium: Boolean,
    freeErrorCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                val normalizedText = normalizeVoiceSearchText(spokenText)
                viewModel.updateSearchFilters(normalizedText, viewModel.selectedBrand.value, viewModel.selectedCategory.value)
            }
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val modelQuery by viewModel.modelQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val savedErrors by viewModel.savedErrors.collectAsState()
    val showOnlySaved by viewModel.showOnlySaved.collectAsState()

    val liveCategories by viewModel.liveCategories.collectAsState()
    val liveBrands by viewModel.liveBrands.collectAsState()

    var showFilterBar by remember { mutableStateOf(false) }

    if (selectedErrorDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchFilters(it, selectedBrand, selectedCategory, modelQuery) },
                placeholder = { Text("کد خطا، برند، دستگاه یا شرح عیب...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchFilters("", selectedBrand, selectedCategory, modelQuery) }) {
                                Icon(Icons.Default.Close, contentDescription = "پاک کردن", tint = Color(0xFF64748B))
                            }
                        }
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "لطفاً عیب یا کد خطا را بگویید...")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "سیستم صوتی روی این دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "جستجوی صوتی", tint = CodyarRed)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (showOnlySaved) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(CodyarNavy.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Text("درحال نمایش کدهای ذخیره شده شما", fontSize = 12.sp, color = CodyarTextPrimary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { viewModel.setShowOnlySaved(false) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("نمایش همه کدها", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expandable Filter Chip
            Card(
                onClick = { showFilterBar = !showFilterBar },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEAECEF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = if (showFilterBar) CodyarRed else CodyarTextPrimary
                        )
                        Text(
                            text = "فیلتر پیشرفته",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (showFilterBar) CodyarRed else CodyarTextPrimary
                        )
                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(CodyarRed, CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text("فعال", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Icon(
                        imageVector = if (showFilterBar) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = CodyarTextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = showFilterBar) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Side-by-side Dropdowns for Device Category and Brand
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "نوع دستگاه:",
                                    selectedValue = selectedCategory,
                                    options = liveCategories,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, selectedBrand, it, modelQuery) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "برند:",
                                    selectedValue = selectedBrand,
                                    options = liveBrands,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, it, selectedCategory, modelQuery) }
                                )
                            }
                        }

                        // Model Filter TextField and Dropdown
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "فیلتر مدل (دقت بیشتر):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            val availableModels = remember(selectedBrand, selectedCategory, viewModel.liveErrorCodes.collectAsState().value) {
                                viewModel.getAvailableModelsFor(selectedBrand, selectedCategory)
                            }
                            
                            var modelExpanded by remember { mutableStateOf(false) }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = modelQuery,
                                    onValueChange = { 
                                        viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, it)
                                        modelExpanded = true
                                    },
                                    placeholder = { Text("مثلاً: ۲۴۰۰، v12، دایرکت درایو...", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (modelQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, "") }) {
                                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                                Icon(
                                                    imageVector = if (modelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "نمایش لیست",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                modelExpanded = true
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CodyarNavy,
                                        unfocusedBorderColor = Color(0xFFDDE1E7),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC)
                                    )
                                )
                                
                                val filteredModels = remember(availableModels, modelQuery) {
                                    if (modelQuery.isEmpty() || modelQuery == "همه") {
                                        availableModels
                                    } else {
                                        val normQuery = viewModel.canonicalModel(modelQuery)
                                        availableModels.filter { 
                                            it == "همه" || viewModel.canonicalModel(it).contains(normQuery, ignoreCase = true)
                                        }
                                    }
                                }
                                
                                if (filteredModels.isNotEmpty() && modelExpanded) {
                                    DropdownMenu(
                                        expanded = modelExpanded,
                                        onDismissRequest = { modelExpanded = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .heightIn(max = 240.dp)
                                            .background(CodyarSurface)
                                    ) {
                                        filteredModels.forEach { modelOpt ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = modelOpt,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Right
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, if (modelOpt == "همه") "" else modelOpt)
                                                    modelExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.updateSearchFilters("", "همه", "همه", "") },
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.align(Alignment.End),
                                border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                            ) {
                                Text("× پاک کردن فیلترها", fontSize = 11.sp, color = CodyarTextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${searchResults.size} نتیجه یافت شد",
                fontSize = 11.sp,
                color = CodyarTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Results List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(searchResults) { err ->
                    val severityLevel = err.hazardLevel ?: "medium"
                    val (color, bg, label) = when (severityLevel) {
                        "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "خطرناک")
                        "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "کم‌خطر")
                        else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPremium) {
                                    onSelectError(err)
                                } else {
                                    if (freeErrorCount < 5) {
                                        viewModel.useFreeCount("error") {
                                            onSelectError(err)
                                        }
                                    } else {
                                        onShowPlans()
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = err.code ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarTextPrimary
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = err.title ?: err.code ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                    fontSize = 11.sp,
                                    color = CodyarTextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(bg, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }
                        }
                    }
                }

                if (searchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("❌", fontSize = 36.sp)
                            Text("نتیجه‌ای پیدا نشد", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("کد خطا یا برند را بررسی کنید", fontSize = 12.sp, color = CodyarTextSecondary)
                        }
                    }
                }
            }
        }
    } else {
        // ERROR DETAIL SCREEN
        val err = selectedErrorDetail
        val isBookmarked = savedErrors.any { it.code == err.code && it.brand == err.brand && it.category == err.category }

        val severityLevel = err.hazardLevel ?: "medium"
        val (themeColor, bgText, titleText) = when (severityLevel) {
            "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "بحرانی / خطرناک")
            "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "آسان / کم‌خطر")
            else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CodyarSurface,
                        contentColor = CodyarTextPrimary
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bookmark Icon
                IconButton(
                    onClick = {
                        viewModel.toggleSavedError(err.code ?: "", err.brand ?: "", err.category ?: "", isBookmarked)
                        Toast.makeText(
                            context,
                            if (isBookmarked) "از ذخیره‌شده‌ها حذف شد" else "به ذخیره‌شده‌ها اضافه شد",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "ذخیره",
                        tint = if (isBookmarked) Color.Red else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Detail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header colored based on hazard level
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeColor)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = err.code ?: "",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = err.title ?: "بررسی ارور ${err.code}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Content Padding
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Description
                        if (!err.description.isNullOrBlank()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD68910), modifier = Modifier.size(14.dp))
                                    Text("توضیح خطا", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Text(
                                    text = err.description,
                                    fontSize = 15.sp,
                                    color = CodyarTextPrimary,
                                    lineHeight = 30.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFEF9E7), RoundedCornerShape(10.dp))
                                        .border(BorderStroke(1.dp, Color(0xFFFEF9E7)))
                                        .padding(14.dp)
                                )
                            }
                        }

                        // Causes List
                        val causesList = with(viewModel) { err.causes.toListOfStrings() }
                        if (causesList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(14.dp))
                                    Text("علت‌های احتمالی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    causesList.forEachIndexed { i, cause ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFDF0EE), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "${i + 1}.",
                                                color = Color(0xFFC0392B),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cause,
                                                fontSize = 15.sp,
                                                color = CodyarTextPrimary,
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Steps list
                        val stepsList = with(viewModel) { err.steps.toListOfStrings() }
                        if (stepsList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                    Text("مراحل رفع مشکل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    stepsList.forEachIndexed { i, step ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFEAFAF1), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(Color(0xFF1E8449), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = (i + 1).toString(),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = step,
                                                fontSize = 15.sp,
                                                color = CodyarTextPrimary,
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Source Disclaimer
                        Text(
                            text = "منبع: وب‌سایت کدیار۲۴ (kodyar24.ir) و دفترچه راهنمای رسمی شرکت سازنده ${err.brand}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        // Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToTechnicians,
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("اعزام تکنسین")
                            }

                            Button(
                                onClick = onNavigateToStore,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E8449)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("قطعات یدکی")
                            }
                        }

                        // Copy Button
                        TextButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(
                                        "${err.brand} - ${err.category}\nکد خطا: ${err.code}\nشرح عیب: ${err.description ?: ""}\nمراحل حل: ${stepsList.joinToString("\n")}"
                                    )
                                )
                                Toast.makeText(context, "اطلاعات عیب‌یابی کپی شد!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("کپی متن خطایابی", fontWeight = FontWeight.Bold, color = CodyarRed)
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 3: COMMON PROBLEMS SCREEN ---
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    viewModel: AssistantViewModel,
    liveProblems: StateFlow<List<KodyarCommonProblem>>,
    selectedProblemDetail: KodyarCommonProblem?,
    onSelectProblem: (KodyarCommonProblem) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    isPremium: Boolean,
    freeProblemCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val problemsList by liveProblems.collectAsState()
    var problemsSearchQuery by remember { mutableStateOf("") }

    if (selectedProblemDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Text(
                "مشکلات رایج لوازم خانگی",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CodyarNavy,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!isPremium) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9E7)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF92400E))
                        Text(
                            text = "کاربران رایگان روزانه ۲ مشکل میتوانند مشاهده کنند",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // Search within problems
            OutlinedTextField(
                value = problemsSearchQuery,
                onValueChange = { problemsSearchQuery = it },
                placeholder = { Text("جستجو در مشکلات...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Problem Item lists
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filtered = problemsList.filter {
                    problemsSearchQuery.isEmpty() ||
                            (it.title ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                            (it.brand ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                            (it.category ?: "").contains(problemsSearchQuery, ignoreCase = true)
                }

                items(filtered) { prob ->
                    Card(
                        onClick = {
                            if (isPremium) {
                                onSelectProblem(prob)
                            } else {
                                if (freeProblemCount < 2) {
                                    viewModel.useFreeCount("problem") {
                                        onSelectProblem(prob)
                                    }
                                } else {
                                    onShowPlans()
                                }
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF0F2F5), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔧", fontSize = 22.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prob.title ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = CodyarTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${prob.brand ?: ""} ${if (!prob.category.isNullOrBlank()) "· ${prob.category}" else ""}",
                                    fontSize = 12.sp,
                                    color = CodyarTextSecondary
                                )
                            }

                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = CodyarTextSecondary)
                        }
                    }
                }
            }
        }
    } else {
        // PROBLEM DETAIL VIEW SCREEN
        val prob = selectedProblemDetail
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CodyarSurface,
                    contentColor = CodyarTextPrimary
                ),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodyarNavy)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Column {
                            Text(
                                text = prob.title ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${prob.brand ?: ""} ${if (!prob.category.isNullOrBlank()) "· ${prob.category}" else ""}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Description
                        if (!prob.description.isNullOrBlank()) {
                            Text(
                                text = prob.description,
                                fontSize = 15.sp,
                                color = CodyarTextPrimary,
                                lineHeight = 30.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                    .padding(14.dp)
                            )
                        }

                        // Causes
                        val causesList = with(viewModel) { prob.causes.toListOfStrings() }
                        if (causesList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(14.dp))
                                    Text("علت‌های احتمالی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    causesList.forEachIndexed { i, cause ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFDF0EE), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "${i + 1}.",
                                                color = Color(0xFFC0392B),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cause,
                                                fontSize = 15.sp,
                                                color = CodyarTextPrimary,
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Solutions / Steps
                        val solList = with(viewModel) { prob.steps.toListOfStrings() }
                        if (solList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                    Text("راه‌حل و مراحل رفع", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    solList.forEachIndexed { i, sol ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFEAFAF1), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(Color(0xFF1E8449), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = (i + 1).toString(),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = sol,
                                                fontSize = 15.sp,
                                                color = CodyarTextPrimary,
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Source Disclaimer
                        Text(
                            text = "منبع: وب‌سایت رسمی کدیار۲۴ (kodyar24.ir) و تجربیات تکنسین‌های مجرب لوازم خانگی",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        Button(
                            onClick = onNavigateToTechnicians,
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("اعزام تکنسین")
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 4: STORE SCREEN ---
@Composable
fun StoreScreen(
    viewModel: AssistantViewModel,
    parts: List<KodyarSparePart>,
    cartItems: List<String>,
    onAddToCart: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "فروشگاه قطعات یدکی",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = CodyarNavy,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (parts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📦", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("فروشگاه در حال تکمیل است", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("به زودی قطعات اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(parts) { part ->
                    val inCart = cartItems.contains(part.id)
                    val outOfStock = (part.stock ?: 0) <= 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Column {
                            // Product Image Block
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA))
                                    .height(130.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val finalImg = part.image ?: part.imageUrl ?: ""
                                if (finalImg.isNotEmpty()) {
                                    AsyncImage(
                                        model = if (finalImg.startsWith("http")) finalImg else "https://kodyar24.ir/${finalImg.removePrefix("/")}",
                                        contentDescription = part.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("⚙️", fontSize = 40.sp)
                                }
                            }

                            // Info block
                            Column(
                                modifier = Modifier.padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = part.name ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = part.brand ?: "",
                                    fontSize = 10.sp,
                                    color = CodyarTextSecondary
                                )

                                Text(
                                    text = if (outOfStock) "ناموجود" else "موجود (${part.stock})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (outOfStock) Color(0xFFC0392B) else Color(0xFF1E8449)
                                )

                                Text(
                                    text = "${formatToman(part.price ?: 0.0)} ت",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E8449),
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )

                                Button(
                                    onClick = { if (!outOfStock) onAddToCart(part.id ?: "") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (inCart) Color(0xFFEAFAF1) else if (outOfStock) Color(0xFFF0F2F5) else Color(0xFF1E8449),
                                        contentColor = if (inCart) Color(0xFF1E8449) else if (outOfStock) CodyarTextSecondary else Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("add_to_cart_button"),
                                    shape = RoundedCornerShape(7.dp),
                                    border = if (inCart) BorderStroke(1.dp, Color(0xFFA9DFBF)) else null,
                                    enabled = !outOfStock,
                                    contentPadding = PaddingValues(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (inCart) "✓ اضافه شد" else if (outOfStock) "ناموجود" else "+ سبد خرید",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 5: TECHNICIANS LIST SCREEN ---
@Composable
fun TechniciansScreen(
    viewModel: AssistantViewModel,
    liveTechs: StateFlow<List<KodyarTechnician>>,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit
) {
    val context = LocalContext.current
    val techsList by liveTechs.collectAsState()
    val userCity = if (!currentUser?.city.isNullOrBlank()) currentUser.city else null
    val liveCitiesRaw by viewModel.liveCities.collectAsState()
    
    // Dynamically insert user's city if it exists and is not in the list
    val liveCities = remember(liveCitiesRaw, userCity) {
        if (userCity != null && !liveCitiesRaw.any { normalizePersian(it).equals(normalizePersian(userCity), ignoreCase = true) }) {
            val list = liveCitiesRaw.toMutableList()
            if (list.contains("همه")) {
                list.add(1, userCity)
            } else {
                list.add(0, userCity)
            }
            list
        } else {
            liveCitiesRaw
        }
    }

    var selectedCityFilter by remember(userCity) { mutableStateOf(userCity ?: "همه") }
    var sortBy by remember { mutableStateOf("top_rated") }
    var selectedTechForRepair by remember { mutableStateOf<KodyarTechnician?>(null) }

    var showChangeCityDialog by remember { mutableStateOf(false) }
    var newCityInput by remember { mutableStateOf("") }
    var changeCityExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "تکنسین‌های تایید شده",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = CodyarNavy,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Persistent city-based information banner
        if (showChangeCityDialog) {
            val liveCitiesList by viewModel.liveCities.collectAsState()
            val filteredCities = remember(newCityInput, liveCitiesList) {
                val cleanQuery = newCityInput.trim().lowercase()
                if (cleanQuery.isEmpty()) {
                    liveCitiesList.filter { it != "همه" }
                } else {
                    liveCitiesList.filter { it != "همه" && it.lowercase().contains(cleanQuery) }
                }
            }

            AlertDialog(
                onDismissRequest = { showChangeCityDialog = false },
                title = { Text("تغییر شهر سکونت", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CodyarNavy) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("لطفاً نام شهر یا محله خود را از لیست انتخاب یا جستجو کنید:", fontSize = 12.sp, color = Color.Gray)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newCityInput,
                                onValueChange = { 
                                    newCityInput = it
                                    changeCityExpanded = true
                                },
                                placeholder = { Text("مثلا: اراک") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { changeCityExpanded = !changeCityExpanded }) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                                            contentDescription = "نمایش لیست"
                                        )
                                    }
                                }
                            )

                            if (changeCityExpanded && filteredCities.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 150.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = CodyarSurface)
                                ) {
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        filteredCities.forEach { cityItem ->
                                            TextButton(
                                                onClick = {
                                                    newCityInput = cityItem
                                                    changeCityExpanded = false
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = cityItem,
                                                    fontSize = 12.sp,
                                                    color = Color.Black,
                                                    textAlign = TextAlign.Right,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            androidx.compose.material3.HorizontalDivider(color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newCityInput.isNotBlank()) {
                                val updatedUser = currentUser?.copy(city = newCityInput.trim())
                                if (updatedUser != null) {
                                    viewModel.updateUserCityLocally(updatedUser)
                                }
                                showChangeCityDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                    ) {
                        Text("ثبت", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeCityDialog = false }) {
                        Text("انصراف", color = Color.Gray)
                    }
                }
            )
        }

        if (userCity != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍 نمایش تکنسین‌های فعال در شهر شما: $userCity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF1E40AF),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { 
                            newCityInput = userCity
                            showChangeCityDialog = true 
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("تغییر شهر", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍 برای مشاهده تکنسین‌های فعال در محدوده خود، شهر خود را تعیین کنید",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF92400E),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { 
                            newCityInput = ""
                            showChangeCityDialog = true 
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("تعیین شهر", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val forceUserCity = false

        // Horizontal list of city chips, visible to everyone
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            liveCities.forEach { city ->
                val active = normalizePersian(selectedCityFilter).equals(normalizePersian(city), ignoreCase = true)
                AssistChip(
                    onClick = { 
                        selectedCityFilter = city 
                        if (currentUser != null && city != "همه" && currentUser.city != city) {
                            val updatedUser = currentUser.copy(city = city)
                            viewModel.updateUserCityLocally(updatedUser)
                        }
                    },
                    label = { Text(city, fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (active) CodyarNavy else Color(0xFFF0F2F5),
                        labelColor = if (active) Color.White else CodyarTextPrimary
                    )
                )
            }
        }

        val listAvatars = remember {
            listOf(
                "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1566492031773-4f4e44671857?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=150&h=150&fit=crop"
            )
        }

        // Sorting Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مرتب‌سازی:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CodyarTextSecondary
            )
            
            val sortOptions = listOf(
                "top_rated" to "⭐ برترین‌ها (امتیاز بالا)",
                "most_orders" to "🛠️ پرکارترین‌ها",
                "all" to "👤 همه"
            )
            
            sortOptions.forEach { (optionKey, optionLabel) ->
                val isActive = sortBy == optionKey
                Box(
                    modifier = Modifier
                        .background(
                            if (isActive) CodyarNavy.copy(alpha = 0.12f) else Color(0xFFF3F4F6),
                            RoundedCornerShape(30.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) CodyarNavy else Color.Transparent,
                            shape = RoundedCornerShape(30.dp)
                        )
                        .clickable { sortBy = optionKey }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = optionLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) CodyarNavy else CodyarTextPrimary
                    )
                }
            }
        }

        // List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            val filterCity = selectedCityFilter
            val baseFiltered = techsList.filter {
                if (filterCity == "همه") {
                    true
                } else {
                    viewModel.areCitiesCompatible(it.city, filterCity)
                }
            }

            val filtered = when (sortBy) {
                "top_rated" -> baseFiltered.sortedWith(
                    compareByDescending<KodyarTechnician> { it.rating ?: 5.0 }
                        .thenByDescending { it.satisfactionRate ?: 100 }
                        .thenByDescending { it.completedOrders ?: 0 }
                )
                "most_orders" -> baseFiltered.sortedByDescending { it.completedOrders ?: 0 }
                else -> baseFiltered
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📍", fontSize = 28.sp)
                            Text(
                                "تکنسینی در شهر $filterCity ثبت نشده است",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "در حال حاضر تکنسین فعالی برای شهر مورد نظر ثبت نشده است. در صورت نیاز به هماهنگی تکنسین یا ثبت نهایی درخواست تعمیرات، لطفاً با پشتیبانی کدیار۲۴ ارتباط برقرار کنید.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                items(filtered) { tech ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Avatar on the side
                            val avatarUrl = if (!tech.image.isNullOrBlank()) tech.image else if (!tech.imageUrl.isNullOrBlank()) tech.imageUrl else null
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, CodyarNavy, CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = tech.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = tech.name,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tech.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFEAFAF1), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text("✓ تایید شده", color = Color(0xFF1E8449), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        "📍 ${tech.city ?: "نامشخص"}",
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text("•", fontSize = 11.sp, color = Color.LightGray)
                                    Text(
                                        "🛠️ ${tech.completedOrders ?: 0} سرویس",
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text("•", fontSize = 11.sp, color = Color.LightGray)
                                    
                                    // Star Rating
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB000),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            String.format(java.util.Locale.US, "%.1f", tech.rating ?: 5.0),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CodyarTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            "(${tech.satisfactionRate ?: 100}% رضایت)",
                                            fontSize = 10.sp,
                                            color = Color(0xFF1E8449),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (!tech.bio.isNullOrBlank()) {
                                    Text(
                                        tech.bio,
                                        fontSize = 11.sp,
                                        color = CodyarTextPrimary,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(bottom = 7.dp)
                                    )
                                }

                                // Categories
                                if (!tech.categories.isNullOrEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        tech.categories.forEach { cat ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFE8EAF0), RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(cat, color = CodyarTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (currentUser == null) {
                                            onShowAuth()
                                            return@Button
                                        }
                                        selectedTechForRepair = tech
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("اعزام تکنسین", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("تکنسینی ثبت نشده است", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("به زودی تکنسین‌های تایید شده اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Bottom CTA for techs
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("تکنسین هستید؟", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(
                            "با همکاران ما در وب‌سایت کدیار۲۴ تماس بگیرید و پس از تایید مدارک سفارش کار دریافت کنید.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kodyar24.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("ثبت‌نام تکنسین در سایت", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG FOR REQUESTING DISPATCH ---
    if (selectedTechForRepair != null) {
        val tech = selectedTechForRepair!!
        var deviceBrand by remember { mutableStateOf("") }
        var problemDesc by remember { mutableStateOf("") }
        var preferredDate by remember { mutableStateOf("") }
        var contactPhone by remember(currentUser) { mutableStateOf(currentUser?.phone ?: "") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) selectedTechForRepair = null },
            title = {
                Text(
                    text = "درخواست اعزام تکنسین (${tech.name ?: "کارشناس"})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CodyarNavy,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "جهت هماهنگی دقیق مراجعه تکنسین، مشخصات زیر را تکمیل کنید:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Device and Brand input
                    OutlinedTextField(
                        value = deviceBrand,
                        onValueChange = { deviceBrand = it },
                        label = { Text("نوع دستگاه و برند", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: ماشین لباسشویی سامسونگ", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // Problem description input
                    OutlinedTextField(
                        value = problemDesc,
                        onValueChange = { problemDesc = it },
                        label = { Text("شرح مختصر مشکل یا خرابی", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: روشن نمی‌شود یا صدای غیرعادی می‌دهد", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // Preferred date and time input
                    OutlinedTextField(
                        value = preferredDate,
                        onValueChange = { preferredDate = it },
                        label = { Text("تاریخ و ساعت پیشنهادی جهت مراجعه", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: فردا پنجشنبه ساعت ۱۵ الی ۱۸", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // Contact phone input
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("شماره همراه جهت هماهنگی", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deviceBrand.isBlank() || problemDesc.isBlank() || preferredDate.isBlank() || contactPhone.isBlank()) {
                            Toast.makeText(context, "لطفاً تمامی فیلدها را تکمیل کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        val repairCity = if (!currentUser?.city.isNullOrBlank()) {
                            currentUser.city!!
                        } else if (!tech.city.isNullOrBlank()) {
                            tech.city
                        } else {
                            "تهران"
                        }

                        val formattedDesc = "دستگاه و برند: $deviceBrand\n" +
                                "شرح خرابی: $problemDesc\n" +
                                "زمان پیشنهادی مراجعه کارشناس: $preferredDate\n" +
                                "تلفن تماس هماهنگی: $contactPhone"

                        viewModel.submitRepairRequest(
                            techId = tech.id ?: "",
                            description = formattedDesc,
                            city = repairCity
                        ) { success, err ->
                            isSubmitting = false
                            if (success) {
                                selectedTechForRepair = null
                                Toast.makeText(
                                    context,
                                    "✅ درخواست اعزام با موفقیت ثبت شد. تکنسین جهت هماهنگی با شما تماس می‌گیرد.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, err ?: "خطا در ثبت درخواست", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("ثبت نهایی درخواست", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedTechForRepair = null },
                    enabled = !isSubmitting
                ) {
                    Text("انصراف", color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }
}

// --- TAB 6: PROFILE SCREEN ---
@Composable
fun ProfileScreen(
    viewModel: AssistantViewModel,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit,
    onShowPlans: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToSaved: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        if (currentUser == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("👤", fontSize = 44.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("وارد حساب خود شوید", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CodyarTextPrimary)
                Text("جهت دسترسی به اشتراک ویژه و مدیریت درخواست‌های خود", fontSize = 13.sp, color = CodyarTextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onShowAuth,
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("ورود / ثبت‌نام")
                }
            }
        } else {
            val isPremium = currentUser.subscription?.is_premium == true
            val rawExpiry = currentUser.subscription?.expiry_date ?: ""
            val expiry = if (rawExpiry.startsWith("20") && rawExpiry.length >= 10) {
                convertGregorianToJalali(rawExpiry.take(10))
            } else {
                convertGregorianToJalali(rawExpiry)
            }

            // Main User details card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(CodyarNavy, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(currentUser.full_name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CodyarTextPrimary)
                            Text(currentUser.phone, fontSize = 12.sp, color = CodyarTextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    if (isPremium) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Column {
                                    Text("اشتراک ویژه فعال است", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                    if (expiry.isNotEmpty()) {
                                        Text("انقضا تا تاریخ: $expiry", fontSize = 10.sp, color = Color(0xFF1B5E20))
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                                    Text("اشتراک فعال منقضی یا ناموجود است", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                }
                                Button(
                                    onClick = onShowPlans,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("ارتقا به اشتراک ویژه")
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION: Referral & Invite Code Card ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎁", fontSize = 18.sp)
                        Text(
                            text = "سیستم معرفی و کد تخفیف همکاران",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CodyarTextPrimary
                        )
                    }

                    val isTechnician = currentUser.role == "technician"
                    if (isTechnician) {
                        val referralCode = "CODYAR-${currentUser.phone.takeLast(6)}"
                        val clipboardManager = LocalClipboardManager.current
                        
                        Text(
                            text = "شما به عنوان تکنسین می‌توانید کد دعوت خود را با دیگر همکاران و مشتریان به اشتراک بگذارید تا از ۲۵٪ تخفیف ویژه خرید اشتراک بهره‌مند شوند.",
                            fontSize = 11.sp,
                            color = CodyarTextSecondary,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CodyarBg, RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("کد دعوت اختصاصی شما:", fontSize = 10.sp, color = Color.Gray)
                                Text(referralCode, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CodyarNavy)
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(referralCode))
                                    Toast.makeText(context, "کد دعوت کپی شد: $referralCode", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("کپی کردن کد", fontSize = 11.sp)
                            }
                        }
                    } else {
                        val appliedCode by viewModel.appliedReferralCode.collectAsState()
                        var codeInput by remember { mutableStateOf("") }

                        Text(
                            text = "اگر کد معرف از همکاران تکنسین دارید، آن را وارد کنید تا ۲۵٪ تخفیف ویژه خرید تمامی اشتراک‌های کدیار۲۴ برای شما اعمال شود.",
                            fontSize = 11.sp,
                            color = CodyarTextSecondary,
                            lineHeight = 18.sp
                        )

                        if (appliedCode != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "کد اعمال شده: $appliedCode (تخفیف ۲۵٪ فعال است)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                TextButton(
                                    onClick = { viewModel.removeReferralCode() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("حذف کد", fontSize = 11.sp, color = CodyarRed)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = codeInput,
                                    onValueChange = { codeInput = it },
                                    placeholder = { Text("کد معرف را وارد کنید", fontSize = 11.sp) },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CodyarNavy,
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (codeInput.isNotBlank()) {
                                            val success = viewModel.applyReferralCode(codeInput)
                                            if (success) {
                                                Toast.makeText(context, "کد معرف با ۲۵٪ تخفیف اعمال شد", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "کد دعوت نامعتبر است (حداقل ۴ کاراکتر)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("ثبت و اعمال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Menu choices
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val menuItems = listOf(
                        QuadProfileMenu(Icons.Default.Star, Color(0xFFC9A227), "اشتراک و پلن‌ها", onShowPlans),
                        QuadProfileMenu(Icons.Default.Build, Color(0xFF1E8449), "سفارشات تعمیر من", onNavigateToOrders),
                        QuadProfileMenu(Icons.Default.Favorite, Color(0xFF2563EB), "کدهای ذخیره شده", onNavigateToSaved),
                        QuadProfileMenu(Icons.Default.ExitToApp, Color(0xFFC0392B), "خروج از حساب کاربری", { viewModel.logout() })
                    )

                    menuItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.action() }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(item.tint.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = item.label, tint = item.tint, modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = item.label,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CodyarTextPrimary
                            )
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color(0xFFD1D5DB))
                        }

                        if (index < menuItems.size - 1) {
                            HorizontalDivider(color = Color(0xFFF7F8FA))
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(16.dp))
                        Text(
                            "⚖️ مالکیت فکری و منابع محتوایی",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }
                    Text(
                        "تمامی محتوای علمی، کدهای خطا، راهکارهای رفع عیب و ایرادات فنی ارائه‌شده در این اپلیکیشن، متعلق به تیم فنی وب‌سایت رسمی کدیار۲۴ (kodyar24.ir) می‌باشد. این اطلاعات بر اساس تخصص تکنسین‌های مجرب کدیار۲۴ و استناد به دفترچه‌های راهنما و کاتالوگ‌های رسمی شرکت‌های سازنده لوازم خانگی تدوین و به صورت اختصاصی جهت استفاده همکاران یکپارچه‌سازی شده است.",
                        fontSize = 10.sp,
                        color = Color(0xFF166534),
                        textAlign = TextAlign.Right,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = Color(0xFFDCFCE7), thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔗 وب‌سایت مرجع: kodyar24.ir", 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color(0xFF15803D),
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kodyar24.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        Text(
                            text = "📧 پشتیبانی: info@kodiar24.ir", 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color(0xFF15803D),
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:info@kodiar24.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        )
                    }
                }
            }
    }
}

data class QuadProfileMenu(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val label: String,
    val action: () -> Unit
)

// --- TAB 7: REPAIR ORDERS SCREEN ---
@Composable
fun OrdersScreen(
    viewModel: AssistantViewModel,
    repairOrders: List<KodyarRepairOrder>,
    isRepairsLoading: Boolean,
    onBack: () -> Unit,
    onNavigateToTechs: () -> Unit
) {
    val partPurchases by viewModel.partPurchases.collectAsState()
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Repair Orders, 1: Part Purchases

    val statusMap = mapOf(
        "pending" to Triple("در انتظار", Color(0xFFD68910), Color(0xFFFEF9E7)),
        "accepted" to Triple("تایید شده", Color(0xFF1E8449), Color(0xFFEAFAF1)),
        "ongoing" to Triple("در حال انجام", Color(0xFF2563EB), Color(0xFFEFF6FF)),
        "completed" to Triple("تکمیل شده", CodyarTextPrimary, Color(0xFFF0F2F5)),
        "rejected" to Triple("لغو شده", Color(0xFFC0392B), Color(0xFFFDF0EE))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = CodyarSurface,
                contentColor = CodyarTextPrimary
            ),
            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = CodyarSurface,
            contentColor = CodyarNavy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("درخواست‌های تعمیر", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("سفارش‌های قطعات", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedSubTab == 0) {
            // Repair Orders
            if (isRepairsLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CodyarNavy)
                }
            } else if (repairOrders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🔧", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("سفارشی ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CodyarTextPrimary)
                    Text("جهت ثبت درخواست با تکنسین تماس حاصل فرمایید", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 20.dp))
                    Button(
                        onClick = onNavigateToTechs,
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                    ) {
                        Text("لیست تکنسین‌ها")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repairOrders.size) { i ->
                        val o = repairOrders[i]
                        val sKey = o.status ?: "pending"
                        val (label, textCol, bgCol) = statusMap[sKey] ?: Triple("در انتظار", Color(0xFFD68910), Color(0xFFFEF9E7))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "درخواست تعمیر #${i + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!o.description.isNullOrBlank()) {
                                    Text(
                                        text = o.description,
                                        fontSize = 13.sp,
                                        color = CodyarTextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .background(Color(0xFFF7F8FA), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    )
                                }

                                if (!o.city.isNullOrBlank()) {
                                    Text(
                                        text = "📍 شهر: ${o.city}",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                if (!o.technician_name.isNullOrBlank()) {
                                    Text(
                                        text = "👨‍🔧 تکنسین: ${o.technician_name}",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (!o.created_at.isNullOrBlank()) {
                                    val cleanDate = o.created_at.take(10)
                                    Text(
                                        text = "تاریخ ثبت: $cleanDate",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9AA3AF),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Part Purchases
            if (partPurchases.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📦", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("هیچ سفارش قطعه‌ای ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CodyarTextPrimary)
                    Text("با مراجعه به فروشگاه می‌توانید قطعه مورد نظر خود را سفارش دهید", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 20.dp), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(partPurchases.size) { i ->
                        val purchase = partPurchases[i]

                        // Status styling: pending -> در حال بررسی, sent -> ارسال شده, delivered -> تحویل شده
                        val (statusText, textCol, bgCol) = when (purchase.status) {
                            "sent" -> Triple("ارسال شده", Color(0xFF1E8449), Color(0xFFEAFAF1))
                            "delivered" -> Triple("تحویل داده شده", CodyarTextPrimary, Color(0xFFF0F2F5))
                            else -> Triple("در حال بررسی", Color(0xFFD68910), Color(0xFFFEF9E7))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = purchase.partName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "تعداد: ${purchase.quantity} عدد",
                                        fontSize = 13.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text(
                                        text = "مبلغ کل: ${formatToman(purchase.totalPrice)} تومان",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarRed
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = Color(0xFFEAECEF)
                                )

                                Text(
                                    text = "📅 تاریخ سفارش: ${purchase.dateStr}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF718096),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                if (!purchase.address.isNullOrBlank()) {
                                    Text(
                                        text = "📍 آدرس ارسال: ${purchase.address}",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                if (!purchase.notes.isNullOrBlank()) {
                                    Text(
                                        text = "📝 توضیحات: ${purchase.notes}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF718096)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 8: AI ASSISTANT CHAT SCREEN (SUPPLEMENTARY) ---
@Composable
fun AiChatScreen(viewModel: AssistantViewModel) {
    // Supplementary Composable if AI tab is enabled or called
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("دستیار هوشمند تعمیرات کدیار۲۴")
    }
}
