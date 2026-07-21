package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AssistantRepository
import com.example.data.db.ConversationEntity
import com.example.data.db.MessageEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.db.CustomErrorEntity
import com.example.data.model.*
import com.example.data.repository.ErrorCodeRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun normalizePersian(input: String?): String {
    if (input == null) return ""
    return input.trim()
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("ة", "ه")
        .replace("\\s+".toRegex(), " ")
}

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val context: Context
) : ViewModel() {

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasLoadedFromNetwork = false

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("kodyar_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .add(KodyarCityAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val databaseAdapter = moshi.adapter(KodyarDatabaseResponse::class.java)

    // --- Live Web Update Notification State ---
    private val _appUpdateNotification = MutableStateFlow<AppUpdateNotification?>(null)
    val appUpdateNotification: StateFlow<AppUpdateNotification?> = _appUpdateNotification.asStateFlow()

    fun dismissUpdateNotification() {
        _appUpdateNotification.value = null
    }

    // --- Theme Settings ---
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        sharedPrefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun areCitiesCompatible(cityA: String?, cityB: String?): Boolean {
        val normA = normalizePersian(cityA)
        val normB = normalizePersian(cityB)
        if (normA.isEmpty() || normB.isEmpty()) return false
        
        // Direct match or substring match
        if (normA == normB || normA.contains(normB, ignoreCase = true) || normB.contains(normA, ignoreCase = true)) {
            return true
        }
        
        // Support "اراک" and "فرمهین" / "فراهان" as compatible (same province)
        val isArakOrFarmahinA = normA == "اراک" || normA == "فرمهین" || normA == "فراهان" || 
                                normA.contains("اراک") || normA.contains("فرمهین") || normA.contains("فراهان")
        val isArakOrFarmahinB = normB == "اراک" || normB == "فرمهین" || normB == "فراهان" || 
                                normB.contains("اراک") || normB.contains("فرمهین") || normB.contains("فراهان")
        if (isArakOrFarmahinA && isArakOrFarmahinB) {
            return true
        }
        
        return false
    }

    // --- Database states ---
    private val _liveErrorCodes = MutableStateFlow<List<KodyarErrorCode>>(emptyList())
    val liveErrorCodes: StateFlow<List<KodyarErrorCode>> = _liveErrorCodes.asStateFlow()

    private val _liveSpareParts = MutableStateFlow<List<KodyarSparePart>>(emptyList())
    val liveSpareParts: StateFlow<List<KodyarSparePart>> = _liveSpareParts.asStateFlow()

    private val _liveTechnicians = MutableStateFlow<List<KodyarTechnician>>(emptyList())
    val liveTechnicians: StateFlow<List<KodyarTechnician>> = _liveTechnicians.asStateFlow()

    private val _liveCommonProblems = MutableStateFlow<List<KodyarCommonProblem>>(emptyList())
    val liveCommonProblems: StateFlow<List<KodyarCommonProblem>> = _liveCommonProblems.asStateFlow()

    private val _liveCategories = MutableStateFlow<List<String>>(listOf("همه"))
    val liveCategories: StateFlow<List<String>> = _liveCategories.asStateFlow()

    private val _liveBrands = MutableStateFlow<List<String>>(listOf("همه"))
    val liveBrands: StateFlow<List<String>> = _liveBrands.asStateFlow()

    private val _liveCities = MutableStateFlow<List<String>>(listOf("همه"))
    val liveCities: StateFlow<List<String>> = _liveCities.asStateFlow()

    private val _isDatabaseLoading = MutableStateFlow(false)
    val isDatabaseLoading: StateFlow<Boolean> = _isDatabaseLoading.asStateFlow()

    // --- Referral / Invite Code States ---
    private val _appliedReferralCode = MutableStateFlow<String?>(null)
    val appliedReferralCode: StateFlow<String?> = _appliedReferralCode.asStateFlow()

    private val _referralDiscountPercent = MutableStateFlow(0)
    val referralDiscountPercent: StateFlow<Int> = _referralDiscountPercent.asStateFlow()

    fun applyReferralCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.length < 4) return false
        _appliedReferralCode.value = trimmed
        _referralDiscountPercent.value = 25 // Default 25%
        return true
    }

    fun removeReferralCode() {
        _appliedReferralCode.value = null
        _referralDiscountPercent.value = 0
    }

    // --- Search states ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedBrand = MutableStateFlow("همه")
    val selectedBrand: StateFlow<String> = _selectedBrand.asStateFlow()

    private val _selectedCategory = MutableStateFlow("همه")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _modelQuery = MutableStateFlow("")
    val modelQuery: StateFlow<String> = _modelQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<KodyarErrorCode>>(emptyList())
    val searchResults: StateFlow<List<KodyarErrorCode>> = _searchResults.asStateFlow()

    private val _showOnlySaved = MutableStateFlow(false)
    val showOnlySaved: StateFlow<Boolean> = _showOnlySaved.asStateFlow()

    // --- Bookmarked / Saved Errors ---
    val savedErrors: StateFlow<List<SavedErrorEntity>> = repository.allSavedErrors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setShowOnlySaved(onlySaved: Boolean) {
        _showOnlySaved.value = onlySaved
        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
    }

    // --- Auth states ---
    private val _currentUser = MutableStateFlow<KodyarUser?>(null)
    val currentUser: StateFlow<KodyarUser?> = _currentUser
        .map { user ->
            if (user == null) {
                if (sharedPrefs.getBoolean("bazaar_premium_active", false)) {
                    val sku = sharedPrefs.getString("bazaar_premium_sku", "") ?: ""
                    KodyarUser(
                        id = "guest_premium",
                        full_name = "کاربر ویژه",
                        phone = "",
                        subscription = KodyarSubscription(
                            is_premium = true,
                            expiry_date = calculateExpiryDateForSku(sku)
                        ),
                        role = "customer"
                    )
                } else {
                    null
                }
            } else if (sharedPrefs.getBoolean("bazaar_premium_active", false)) {
                val sku = sharedPrefs.getString("bazaar_premium_sku", "") ?: ""
                user.copy(
                    subscription = KodyarSubscription(
                        is_premium = true,
                        expiry_date = calculateExpiryDateForSku(sku)
                    )
                )
            } else {
                user
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // --- Subscription Plans state ---
    private val _subscriptionPlans = MutableStateFlow<List<KodyarSubscriptionPlan>>(emptyList())
    val subscriptionPlans: StateFlow<List<KodyarSubscriptionPlan>> = _subscriptionPlans.asStateFlow()

    private val _isPlansLoading = MutableStateFlow(false)
    val isPlansLoading: StateFlow<Boolean> = _isPlansLoading.asStateFlow()

    // --- Cart states ---
    private val _cart = MutableStateFlow<List<String>>(emptyList())
    val cart: StateFlow<List<String>> = _cart.asStateFlow()

    private val _cartQty = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cartQty: StateFlow<Map<String, Int>> = _cartQty.asStateFlow()

    private val _isPurchaseLoading = MutableStateFlow(false)
    val isPurchaseLoading: StateFlow<Boolean> = _isPurchaseLoading.asStateFlow()

    private val _purchaseSuccess = MutableStateFlow(false)
    val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()

    // --- Repair state ---
    private val _repairOrders = MutableStateFlow<List<KodyarRepairOrder>>(emptyList())
    val repairOrders: StateFlow<List<KodyarRepairOrder>> = _repairOrders.asStateFlow()

    private val _isRepairsLoading = MutableStateFlow(false)
    val isRepairsLoading: StateFlow<Boolean> = _isRepairsLoading.asStateFlow()

    // --- Part Purchase state ---
    private val _partPurchases = MutableStateFlow<List<PartPurchaseOrder>>(emptyList())
    val partPurchases: StateFlow<List<PartPurchaseOrder>> = _partPurchases.asStateFlow()

    private val partPurchasesAdapter by lazy {
        moshi.adapter<List<PartPurchaseOrder>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, PartPurchaseOrder::class.java)
        )
    }

    fun loadPartPurchases() {
        val json = sharedPrefs.getString("part_purchases_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val list = partPurchasesAdapter.fromJson(json)
                if (list != null) {
                    _partPurchases.value = list
                }
            } catch (e: java.lang.Exception) {
                Log.e("AssistantViewModel", "Error parsing part purchases", e)
            }
        }
    }

    fun savePartPurchase(order: PartPurchaseOrder) {
        val currentList = _partPurchases.value.toMutableList()
        currentList.add(0, order) // Add at top
        _partPurchases.value = currentList
        try {
            val json = partPurchasesAdapter.toJson(currentList)
            sharedPrefs.edit().putString("part_purchases_json", json).apply()
        } catch (e: java.lang.Exception) {
            Log.e("AssistantViewModel", "Error saving part purchases", e)
        }
    }

    private fun getCurrentPersianDate(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val pYear = year - 621
        return "$pYear/$month/$day"
    }

    private fun calculateExpiryDateForSku(sku: String): String {
        val calendar = java.util.Calendar.getInstance()
        val daysToAdd = when (sku) {
            "ir.golden.com" -> 30
            "ir.silver.com" -> 90
            "ir.almas.com" -> 180
            "ir.12-month.com" -> 365
            else -> 30
        }
        calendar.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd)
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "$year-${String.format("%02d", month)}-${String.format("%02d", day)}"
    }

    // --- Sub / Verification state ---
    private val _isCardVerifyLoading = MutableStateFlow(false)
    val isCardVerifyLoading: StateFlow<Boolean> = _isCardVerifyLoading.asStateFlow()

    private val _cardVerifySuccess = MutableStateFlow(false)
    val cardVerifySuccess: StateFlow<Boolean> = _cardVerifySuccess.asStateFlow()

    // --- Usage counts ---
    private val _freeErrorCount = MutableStateFlow(0)
    val freeErrorCount: StateFlow<Int> = _freeErrorCount.asStateFlow()

    private val _freeProblemCount = MutableStateFlow(0)
    val freeProblemCount: StateFlow<Int> = _freeProblemCount.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadCachedDatabase()
            loadKodyarDatabase()
            checkSavedSession()
            fetchSubscriptionPlans()
            loadPartPurchases()
        }
        setupNetworkCallback()
        viewModelScope.launch {
            savedErrors.collect {
                if (_showOnlySaved.value) {
                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                }
            }
        }
    }

    fun fetchSubscriptionPlans() {
        viewModelScope.launch {
            _isPlansLoading.value = true
            try {
                val response = repository.getSubscriptionPlans()
                if ((response.status == "ok" || response.status == "success") && response.plans != null) {
                    _subscriptionPlans.value = response.plans
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to fetch subscription plans: ${e.message}")
            } finally {
                _isPlansLoading.value = false
            }
        }
    }

    private fun loadCachedDatabase() {
        try {
            val cachedJson = sharedPrefs.getString("cached_kodyar_database", null)
            if (!cachedJson.isNullOrEmpty()) {
                val response = databaseAdapter.fromJson(cachedJson)
                if (response != null) {
                    _liveErrorCodes.value = response.errorCodes ?: emptyList()
                    _liveSpareParts.value = response.spareParts ?: emptyList()
                    _liveTechnicians.value = response.technicians ?: emptyList()
                    _liveCommonProblems.value = response.commonProblems ?: emptyList()

                    _liveCategories.value = listOf("همه") + (response.categoriesList ?: emptyList())
                    _liveBrands.value = listOf("همه") + (response.brandsList ?: emptyList())
                    val parsedCities = response.citiesList?.flatMap { 
                        listOfNotNull(it.name, it.title, it.city, it.cityName, it.name_fa, it.nameFarsi, it.slug)
                    }?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
                    _liveCities.value = listOf("همه") + parsedCities

                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                    Log.d("AssistantViewModel", "Successfully loaded database from local cache.")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to load cached database.", e)
        }
        // If loading cache failed or is empty, fallback to static error codes
        loadStaticFallbackDatabase()
    }

    private fun loadStaticFallbackDatabase() {
        val fallbackList = ErrorCodeRepository.staticErrorCodes.map {
            KodyarErrorCode(
                id = it.code,
                code = it.code,
                brand = it.brand,
                category = it.category,
                title = it.code,
                description = it.description,
                causes = emptyList<String>(),
                steps = listOf(it.solution),
                hazardLevel = when(it.severity) {
                    "بحرانی" -> "high"
                    "متوسط" -> "medium"
                    else -> "low"
                },
                videoUrl = null,
                isApproved = true
            )
        }
        _liveErrorCodes.value = fallbackList
        _liveCategories.value = listOf("همه") + ErrorCodeRepository.categories.map { if (it == "همه دستگاه‌ها") "همه" else it }
        _liveBrands.value = listOf("همه") + ErrorCodeRepository.brands.map { if (it == "همه برندها") "همه" else it }
        _liveCities.value = listOf("همه", "تهران", "کرج", "اصفهان", "مشهد", "شیراز", "تبریز", "قم", "اهواز")
        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
    }

    // --- Session & Auth functions ---
    fun getSessionToken(): String? {
        return sharedPrefs.getString("session_token", null)
    }

    private fun checkSavedSession() {
        val token = getSessionToken()
        if (token != null) {
            val cached = getCachedUser()
            if (cached != null) {
                _currentUser.value = cached
            }
            loadCurrentUser(token)
        }
    }

    private fun saveUserToCache(user: KodyarUser) {
        val cached = getCachedUser()
        val persistentCity = if (!user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${user.phone}", null) else null
        val finalCity = if (!user.city.isNullOrBlank()) user.city else (cached?.city ?: persistentCity)
        val finalRole = if (!user.role.isNullOrBlank()) user.role else (cached?.role ?: "customer")
        val finalCategories = if (!user.categories.isNullOrEmpty()) user.categories else cached?.categories

        val editor = sharedPrefs.edit()
            .putString("cached_user_id", user.id)
            .putString("cached_user_name", user.full_name)
            .putString("cached_user_phone", user.phone)
            .putBoolean("cached_user_premium", user.subscription?.is_premium ?: false)
            .putString("cached_user_expiry", user.subscription?.expiry_date)
            .putString("cached_user_role", finalRole)
            .putString("cached_user_city", finalCity)
            .putString("cached_user_categories", finalCategories?.joinToString(","))

        if (!finalCity.isNullOrBlank() && !user.phone.isNullOrBlank()) {
            editor.putString("persistent_city_${user.phone}", finalCity)
        }
        editor.apply()
    }

    fun updateUserCityLocally(user: KodyarUser) {
        _currentUser.value = user
        saveUserToCache(user)
    }

    fun setPremiumUserLocally(sku: String) {
        val isPremium = sku.isNotEmpty()
        sharedPrefs.edit()
            .putBoolean("bazaar_premium_active", isPremium)
            .putString("bazaar_premium_sku", sku)
            .apply()

        val user = _currentUser.value
        if (user != null) {
            val updatedUser = user.copy(
                subscription = KodyarSubscription(
                    is_premium = isPremium,
                    expiry_date = if (isPremium) calculateExpiryDateForSku(sku) else ""
                )
            )
            _currentUser.value = updatedUser
            saveUserToCache(updatedUser)
        } else {
            // Trigger flow update
            _currentUser.value = null
        }
    }

    private fun getCachedUser(): KodyarUser? {
        val id = sharedPrefs.getString("cached_user_id", null) ?: return null
        val name = sharedPrefs.getString("cached_user_name", "") ?: ""
        val phone = sharedPrefs.getString("cached_user_phone", "") ?: ""
        val isPremium = sharedPrefs.getBoolean("cached_user_premium", false)
        val expiry = sharedPrefs.getString("cached_user_expiry", null)
        val role = sharedPrefs.getString("cached_user_role", "customer")
        val city = sharedPrefs.getString("cached_user_city", null)
        val catsString = sharedPrefs.getString("cached_user_categories", null)
        val categories = if (!catsString.isNullOrEmpty()) catsString.split(",") else null
        return KodyarUser(
            id = id,
            full_name = name,
            phone = phone,
            subscription = KodyarSubscription(is_premium = isPremium, expiry_date = expiry),
            role = role,
            city = city,
            categories = categories
        )
    }

    private fun clearUserCache() {
        sharedPrefs.edit()
            .remove("cached_user_id")
            .remove("cached_user_name")
            .remove("cached_user_phone")
            .remove("cached_user_premium")
            .remove("cached_user_expiry")
            .remove("cached_user_role")
            .remove("cached_user_city")
            .remove("cached_user_categories")
            .apply()
    }

    fun getUniqueErrorCodesViewed(): Set<String> {
        return sharedPrefs.getStringSet("viewed_error_codes", emptySet()) ?: emptySet()
    }

    fun recordErrorCodeView(codeKey: String) {
        val current = getUniqueErrorCodesViewed().toMutableSet()
        current.add(codeKey)
        sharedPrefs.edit().putStringSet("viewed_error_codes", current).apply()
    }

    fun getUniqueProblemsViewed(): Set<String> {
        return sharedPrefs.getStringSet("viewed_problems", emptySet()) ?: emptySet()
    }

    fun recordProblemView(problemKey: String) {
        val current = getUniqueProblemsViewed().toMutableSet()
        current.add(problemKey)
        sharedPrefs.edit().putStringSet("viewed_problems", current).apply()
    }

    fun loadCurrentUser(token: String) {
        viewModelScope.launch {
            try {
                val response = repository.getMe(token)
                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val cached = getCachedUser()
                    val persistentCity = if (!response.user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${response.user.phone}", null) else null
                    val mergedUser = response.user.copy(
                        city = if (!response.user.city.isNullOrBlank()) response.user.city else (cached?.city ?: persistentCity),
                        role = if (!response.user.role.isNullOrBlank()) response.user.role else (cached?.role ?: "customer"),
                        categories = if (!response.user.categories.isNullOrEmpty()) response.user.categories else cached?.categories
                    )
                    _currentUser.value = mergedUser
                    saveUserToCache(mergedUser)
                    loadFreeStatus()
                    loadRepairs()
                } else if (response.status == "error") {
                    val cached = getCachedUser()
                    if (cached != null) {
                        _currentUser.value = cached
                    } else {
                        logout()
                    }
                } else {
                    logout()
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error fetching user", e)
                val cached = getCachedUser()
                if (cached != null) {
                    _currentUser.value = cached
                }
            }
        }
    }

    fun login(phone: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val response = repository.login(phone, pass)
                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val cached = getCachedUser()
                    val persistentCity = if (!response.user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${response.user.phone}", null) else null
                    val mergedUser = response.user.copy(
                        city = if (!response.user.city.isNullOrBlank()) response.user.city else (cached?.city ?: persistentCity),
                        role = if (!response.user.role.isNullOrBlank()) response.user.role else (cached?.role ?: "customer"),
                        categories = if (!response.user.categories.isNullOrEmpty()) response.user.categories else cached?.categories
                    )
                    _currentUser.value = mergedUser
                    saveUserToCache(mergedUser)
                    sharedPrefs.edit().putString("session_token", response.user.id).apply()
                    loadFreeStatus()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    _authError.value = response.error ?: "خطا در ورود"
                    onResult(false, _authError.value)
                }
            } catch (e: Exception) {
                _authError.value = "خطای اتصال به سرور: ${e.message}"
                onResult(false, _authError.value)
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun register(
        phone: String,
        pass: String,
        name: String,
        role: String = "customer",
        city: String? = null,
        categories: List<String>? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val response = repository.register(phone, pass, name, role, city, categories)
                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val finalUser = if (role == "technician") {
                        response.user.copy(role = "technician", city = city, categories = categories)
                    } else {
                        response.user.copy(role = "customer", city = city)
                    }
                    _currentUser.value = finalUser
                    saveUserToCache(finalUser)
                    sharedPrefs.edit().putString("session_token", response.user.id).apply()
                    
                    if (role == "technician") {
                        val newTech = KodyarTechnician(
                            id = finalUser.id,
                            name = finalUser.full_name,
                            city = city ?: "تهران",
                            isVerified = true,
                            completedOrders = 0,
                            bio = "تکنسین متخصص لوازم خانگی کدیار۲۴",
                            categories = categories ?: listOf("ماشین لباسشویی", "یخچال و فریزر"),
                            rating = 5.0,
                            satisfactionRate = 100
                        )
                        _liveTechnicians.value = _liveTechnicians.value + newTech
                    }
                    
                    loadFreeStatus()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    _authError.value = response.error ?: "خطا در ثبت‌نام"
                    onResult(false, _authError.value)
                }
            } catch (e: Exception) {
                _authError.value = "خطای اتصال به سرور: ${e.message}"
                onResult(false, _authError.value)
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        sharedPrefs.edit().remove("session_token").apply()
        clearUserCache()
        _repairOrders.value = emptyList()
        _freeErrorCount.value = 0
        _freeProblemCount.value = 0
    }

    // --- Load Kodyar Database ---
    fun loadKodyarDatabase() {
        viewModelScope.launch {
            // Only trigger database loading splash screen if we don't have any cached/loaded data yet!
            _isDatabaseLoading.value = _liveErrorCodes.value.isEmpty()
            try {
                val response = repository.getKodyarDatabase()
                checkForDatabaseUpdates(response)
                _liveErrorCodes.value = response.errorCodes ?: emptyList()
                _liveSpareParts.value = response.spareParts ?: emptyList()
                _liveTechnicians.value = response.technicians ?: emptyList()
                _liveCommonProblems.value = response.commonProblems ?: emptyList()

                _liveCategories.value = listOf("همه") + (response.categoriesList ?: emptyList())
                _liveBrands.value = listOf("همه") + (response.brandsList ?: emptyList())
                val parsedCities = response.citiesList?.flatMap { 
                    listOfNotNull(it.name, it.title, it.city, it.cityName, it.name_fa, it.nameFarsi, it.slug)
                }?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
                _liveCities.value = listOf("همه") + parsedCities

                // Trigger initial search results
                updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)

                hasLoadedFromNetwork = true

                // Save successful response to local cache
                try {
                    val json = databaseAdapter.toJson(response)
                    sharedPrefs.edit().putString("cached_kodyar_database", json).apply()
                    Log.d("AssistantViewModel", "Saved database response to local cache.")
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Failed to cache database response.", e)
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error loading kodyar database from API.", e)
                // If it fails but we already have cached data, do not overwrite it with fallbacks
                if (_liveErrorCodes.value.isEmpty()) {
                    val fallbackList = ErrorCodeRepository.staticErrorCodes.map {
                        KodyarErrorCode(
                            id = it.code,
                            code = it.code,
                            brand = it.brand,
                            category = it.category,
                            title = it.code,
                            description = it.description,
                            causes = emptyList<String>(),
                            steps = listOf(it.solution),
                            hazardLevel = when(it.severity) {
                                "بحرانی" -> "high"
                                "متوسط" -> "medium"
                                else -> "low"
                            },
                            videoUrl = null,
                            isApproved = true
                        )
                    }
                    _liveErrorCodes.value = fallbackList
                    _liveCategories.value = ErrorCodeRepository.categories.map { if (it == "همه دستگاه‌ها") "همه" else it }
                    _liveBrands.value = ErrorCodeRepository.brands.map { if (it == "همه برندها") "همه" else it }
                    
                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                }
            } finally {
                _isDatabaseLoading.value = false
            }
        }
    }

    private fun matchNormalized(target: String?, filter: String?): Boolean {
        val normTarget = normalizePersian(target)
        val normFilter = normalizePersian(filter)
        if (normFilter == "همه" || normFilter.isEmpty()) return true
        return normTarget == normFilter || normTarget.contains(normFilter, ignoreCase = true) || normFilter.contains(normTarget, ignoreCase = true)
    }

    fun getAvailableModelsFor(brand: String, category: String): List<String> {
        val codes = _liveErrorCodes.value
        if (codes.isEmpty()) return listOf("همه")

        val filtered = codes.filter { error ->
            matchNormalized(error.brand, brand) && matchNormalized(error.category, category)
        }

        val models = filtered.mapNotNull { error ->
            val modelName = error.model?.trim()
            if (!modelName.isNullOrEmpty()) {
                modelName
            } else {
                val title = error.title?.trim()
                val code = error.code?.trim()
                if (!title.isNullOrEmpty() && title != code) {
                    title
                } else {
                    null
                }
            }
        }.distinct().sorted()

        return listOf("همه") + models
    }

    fun canonicalModel(input: String?): String {
        if (input == null) return ""
        var text = input.lowercase()
        
        // 1. Convert Persian/Arabic digits to English digits
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in 0..9) {
            text = text.replace(persianDigits[i].toString(), i.toString())
            text = text.replace(arabicDigits[i].toString(), i.toString())
        }
        
        // 2. Map common Persian words for English letters
        val replacements = listOf(
            "دبلیو" to "w",
            "ایکس" to "x",
            "کیو" to "q",
            "وای" to "y",
            "اچ" to "h",
            "اس" to "s",
            "ام" to "m",
            "ال" to "l",
            "اف" to "f",
            "پی" to "p",
            "جی" to "g",
            "ار" to "r",
            "زد" to "z",
            "وی" to "v",
            "تی" to "t",
            "سی" to "c",
            "دی" to "d",
            "بی" to "b",
            "کی" to "k",
            "ای" to "e",
            "ان" to "n",
            "یو" to "u",
            "ب" to "b",
            "پ" to "p",
            "ت" to "t",
            "ج" to "j",
            "د" to "d",
            "ر" to "r",
            "س" to "s",
            "ف" to "f",
            "ک" to "k",
            "ل" to "l",
            "م" to "m",
            "ن" to "n",
            "و" to "v",
            "ه" to "h",
            "ی" to "y"
        )
        
        for ((persian, english) in replacements) {
            text = text.replace(persian, english)
        }
        
        // 3. Remove all non-alphanumeric characters
        return text.replace("[^a-zA-Z0-9]".toRegex(), "")
    }

    fun matchModelCanonical(error: KodyarErrorCode, modelQuery: String): Boolean {
        if (modelQuery.isEmpty() || modelQuery == "همه") return true
        val canonicalQuery = canonicalModel(modelQuery)
        if (canonicalQuery.isEmpty()) return true
        
        // Check error.model
        val modelCanonical = canonicalModel(error.model)
        if (modelCanonical.contains(canonicalQuery) || canonicalQuery.contains(modelCanonical)) {
            return true
        }
        
        // Check error.title
        val titleCanonical = canonicalModel(error.title)
        if (titleCanonical.contains(canonicalQuery) || canonicalQuery.contains(titleCanonical)) {
            return true
        }
        
        // Check error.code
        val codeCanonical = canonicalModel(error.code)
        if (codeCanonical.contains(canonicalQuery) || canonicalQuery.contains(codeCanonical)) {
            return true
        }

        // Check error.description
        val descCanonical = canonicalModel(error.description)
        if (descCanonical.contains(canonicalQuery) || canonicalQuery.contains(descCanonical)) {
            return true
        }
        
        return false
    }

    fun updateSearchFilters(query: String, brand: String, category: String, model: String = _modelQuery.value) {
        _searchQuery.value = query
        _selectedBrand.value = brand
        _selectedCategory.value = category
        _modelQuery.value = model

        _searchResults.value = _liveErrorCodes.value.filter { error ->
            val isSavedMatch = if (_showOnlySaved.value) {
                savedErrors.value.any {
                    it.code == error.code && it.brand == error.brand && it.category == error.category
                }
            } else {
                true
            }

            val matchQuery = if (query.isEmpty()) {
                true
            } else {
                val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isEmpty()) {
                    true
                } else {
                    words.all { word ->
                        (error.code ?: "").contains(word, ignoreCase = true) ||
                        (error.brand ?: "").contains(word, ignoreCase = true) ||
                        (error.category ?: "").contains(word, ignoreCase = true) ||
                        (error.title ?: "").contains(word, ignoreCase = true) ||
                        (error.description ?: "").contains(word, ignoreCase = true)
                    }
                }
            }

            val matchBrand = matchNormalized(error.brand, brand)
            val matchCategory = matchNormalized(error.category, category)

            val matchModel = model.isEmpty() || model == "همه" || matchModelCanonical(error, model)

            isSavedMatch && matchQuery && matchBrand && matchCategory && matchModel
        }
    }

    private fun checkForDatabaseUpdates(response: KodyarDatabaseResponse) {
        val oldErrorIds = sharedPrefs.getStringSet("known_error_ids", emptySet()) ?: emptySet()
        val oldProblemIds = sharedPrefs.getStringSet("known_problem_ids", emptySet()) ?: emptySet()
        val oldPartIds = sharedPrefs.getStringSet("known_part_ids", emptySet()) ?: emptySet()

        val newErrorIds = response.errorCodes?.mapNotNull { it.id }.orEmpty().toSet()
        val newProblemIds = response.commonProblems?.mapNotNull { it.id }.orEmpty().toSet()
        val newPartIds = response.spareParts?.mapNotNull { it.id }.orEmpty().toSet()

        if (oldErrorIds.isEmpty() && oldProblemIds.isEmpty() && oldPartIds.isEmpty()) {
            // First run, silently save the list
            sharedPrefs.edit()
                .putStringSet("known_error_ids", newErrorIds)
                .putStringSet("known_problem_ids", newProblemIds)
                .putStringSet("known_part_ids", newPartIds)
                .apply()
        } else {
            // Check for added/new items
            val addedErrors = response.errorCodes?.filter { it.id != null && !oldErrorIds.contains(it.id) }.orEmpty()
            val addedProblems = response.commonProblems?.filter { it.id != null && !oldProblemIds.contains(it.id) }.orEmpty()
            val addedParts = response.spareParts?.filter { it.id != null && !oldPartIds.contains(it.id) }.orEmpty()

            if (addedErrors.isNotEmpty() || addedProblems.isNotEmpty() || addedParts.isNotEmpty()) {
                _appUpdateNotification.value = AppUpdateNotification(
                    newErrors = addedErrors,
                    newProblems = addedProblems,
                    newParts = addedParts
                )

                // Persist updated sets
                val updatedErrorIds = oldErrorIds.toMutableSet().apply { addAll(newErrorIds) }
                val updatedProblemIds = oldProblemIds.toMutableSet().apply { addAll(newProblemIds) }
                val updatedPartIds = oldPartIds.toMutableSet().apply { addAll(newPartIds) }

                sharedPrefs.edit()
                    .putStringSet("known_error_ids", updatedErrorIds)
                    .putStringSet("known_problem_ids", updatedProblemIds)
                    .putStringSet("known_part_ids", updatedPartIds)
                    .apply()
            }
        }
    }



    // --- Shorthand Helper to parse Causes / Steps ---
    fun Any?.toListOfStrings(): List<String> {
        if (this == null) return emptyList()
        if (this is List<*>) {
            return this.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        }
        val str = this.toString().trim()
        if (str.isEmpty()) return emptyList()
        return str.split(Regex("[\r\n؛•\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    // --- Bookmarked / Saved Errors ---

    fun toggleSavedError(code: String, brand: String, category: String, isCurrentlySaved: Boolean) {
        viewModelScope.launch {
            repository.toggleSavedError(code, brand, category, isCurrentlySaved)
        }
    }

    fun isErrorSaved(code: String, brand: String, category: String): Flow<Boolean> {
        return repository.isErrorSaved(code, brand, category)
    }

    // --- Custom / Notes Errors ---
    val customErrors: StateFlow<List<CustomErrorEntity>> = repository.allCustomErrors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCustomError(code: String, brand: String, category: String, description: String, solution: String, severity: String, userNote: String) {
        viewModelScope.launch {
            val custom = CustomErrorEntity(
                code = code,
                brand = brand,
                category = category,
                description = description,
                solution = solution,
                severity = severity,
                userNote = userNote
            )
            repository.addCustomError(custom)
        }
    }

    fun deleteCustomError(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomError(id)
        }
    }

    fun updateCustomErrorNote(id: Long, note: String) {
        viewModelScope.launch {
            repository.updateCustomErrorNote(id, note)
        }
    }

    // --- Cart functions ---
    fun addToCart(partId: String) {
        val currentCart = _cart.value.toMutableList()
        if (!currentCart.contains(partId)) {
            currentCart.add(partId)
            _cart.value = currentCart
        }
        val currentQty = _cartQty.value.toMutableMap()
        currentQty[partId] = currentQty[partId] ?: 1
        _cartQty.value = currentQty
    }

    fun removeFromCart(partId: String) {
        val currentCart = _cart.value.toMutableList()
        currentCart.remove(partId)
        _cart.value = currentCart

        val currentQty = _cartQty.value.toMutableMap()
        currentQty.remove(partId)
        _cartQty.value = currentQty
    }

    fun updateCartQty(partId: String, qty: Int) {
        val currentQty = _cartQty.value.toMutableMap()
        if (qty > 0) {
            currentQty[partId] = qty
            _cartQty.value = currentQty
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _cartQty.value = emptyMap()
        _purchaseSuccess.value = false
    }

    fun submitPartPurchaseOrdersToServer(onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        viewModelScope.launch {
            _isPurchaseLoading.value = true
            try {
                val user = _currentUser.value
                val city = user?.city ?: "تهران"
                val cartList = _cart.value.toList()
                val qtyMap = _cartQty.value.toMap()

                for (partId in cartList) {
                    val part = _liveSpareParts.value.find { it.id == partId } ?: continue
                    val qty = qtyMap[partId] ?: 1
                    val price = part.price ?: 0.0
                    val subtotal = price * qty

                    // Save locally for profile display
                    val order = PartPurchaseOrder(
                        id = "order_${System.currentTimeMillis()}_${partId}",
                        partId = partId,
                        partName = part.name ?: "قطعه یدکی",
                        quantity = qty,
                        unitPrice = price,
                        totalPrice = subtotal,
                        address = city,
                        notes = "ثبت شده در اپلیکیشن (ادامه پرداخت در وب‌سایت)",
                        dateStr = getCurrentPersianDate(),
                        status = "pending" // initial state
                    )
                    savePartPurchase(order)
                }
                _purchaseSuccess.value = true
                clearCart()
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, "خطا در ثبت محلی سفارش: ${e.message}")
            } finally {
                _isPurchaseLoading.value = false
            }
        }
    }

    private fun isPartPurchase(order: com.example.data.model.KodyarRepairOrder): Boolean {
        val desc = order.description?.trim() ?: ""
        val status = order.status?.trim() ?: ""
        val techId = order.technician_id?.trim() ?: ""
        
        if (status == "sent" || status == "delivered") return true
        if (desc.contains("خرید") || desc.contains("قطعه") || desc.contains("فروشگاه") || desc.contains("فیش")) {
            if (techId.isEmpty() || techId == "null") return true
        }
        return false
    }

    // --- Repair Orders ---
    fun loadRepairs() {
        val token = getSessionToken() ?: return
        viewModelScope.launch {
            _isRepairsLoading.value = true
            try {
                val response = repository.getRepairs(token)
                
                // Combine all raw orders from repairs and data fields
                val allOrders = (response.repairs ?: emptyList()) + (response.data ?: emptyList())
                val distinctOrders = allOrders.distinctBy { it.id }

                // Classify orders into Repair Requests vs Part Purchases dynamically
                val rawRepairs = distinctOrders.filter { !isPartPurchase(it) }
                val rawPurchases = distinctOrders.filter { isPartPurchase(it) }

                // 1. Repair orders (from response.repairs, or fallback to response.data)
                val user = _currentUser.value
                val filtered = if (user != null && user.role == "technician") {
                    if (normalizePersian(user.city).isEmpty()) {
                        emptyList()
                    } else {
                        rawRepairs.filter {
                            areCitiesCompatible(it.city, user.city)
                        }
                    }
                } else {
                    rawRepairs
                }
                _repairOrders.value = filtered

                // 2. Part Purchases (from response.data if present, mapped to PartPurchaseOrder)
                val mappedPurchases = rawPurchases.map { order ->
                    PartPurchaseOrder(
                        id = order.id ?: "order_${System.currentTimeMillis()}_${order.id.hashCode()}",
                        partId = "",
                        partName = order.description ?: "سفارش قطعه",
                        quantity = 1,
                        unitPrice = 0.0,
                        totalPrice = 0.0,
                        address = order.city ?: "",
                        notes = "ثبت شده در سایت",
                        dateStr = order.created_at ?: getCurrentPersianDate(),
                        status = order.status ?: "pending"
                    )
                }
                
                // Overwrite local cache with server-fetched purchases as the source of truth
                _partPurchases.value = mappedPurchases
                try {
                    val json = partPurchasesAdapter.toJson(mappedPurchases)
                    sharedPrefs.edit().putString("part_purchases_json", json).apply()
                } catch (ex: java.lang.Exception) {
                    Log.e("AssistantViewModel", "Error updating local part purchases cache", ex)
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error fetching repairs", e)
            } finally {
                _isRepairsLoading.value = false
            }
        }
    }

    fun submitRepairRequest(techId: String, description: String, city: String, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        val user = _currentUser.value
        val tech = _liveTechnicians.value.find { it.id == techId }
        if (user != null && tech != null) {
            if (!areCitiesCompatible(user.city, tech.city)) {
                onResult(false, "شما فقط می‌توانید از تکنسین‌های فعال در محدوده خود درخواست کار ثبت کنید")
                return
            }
        }

        viewModelScope.launch {
            try {
                val response = repository.createRepair(token, techId, description, city)
                if (response.status == "ok" || response.status == "success") {
                    loadRepairs()
                    onResult(true, null)
                } else {
                    onResult(false, response.error ?: "خطا در ثبت درخواست تعمیرکار")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط به سرور: ${e.message}")
            }
        }
    }

    // --- Subscription Card Verification ---
    fun submitCardVerify(cardHolder: String, trackNumber: String, productId: String, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        viewModelScope.launch {
            _isCardVerifyLoading.value = true
            try {
                val response = repository.verifyCard(token, cardHolder, trackNumber, productId)
                if (response.status == "ok" || response.status == "success") {
                    _cardVerifySuccess.value = true
                    checkSavedSession() // Refreshes premium status
                    onResult(true, null)
                } else {
                    onResult(false, response.error ?: "خطا در ثبت فیش پرداخت")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط به سرور: ${e.message}")
            } finally {
                _isCardVerifyLoading.value = false
            }
        }
    }

    fun resetCardVerifySuccess() {
        _cardVerifySuccess.value = false
    }

    // --- Free usages ---
    fun loadFreeStatus() {
        val token = getSessionToken() ?: return
        viewModelScope.launch {
            try {
                val response = repository.getFreeStatus(token)
                _freeErrorCount.value = response.error_count ?: 0
                _freeProblemCount.value = response.problem_count ?: 0
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error loading free status", e)
            }
        }
    }

    fun useFreeCount(type: String, onComplete: () -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onComplete()
            return
        }
        viewModelScope.launch {
            try {
                val response = repository.useFree(token, type)
                _freeErrorCount.value = response.error_count ?: _freeErrorCount.value
                _freeProblemCount.value = response.problem_count ?: _freeProblemCount.value
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error posting free use", e)
            } finally {
                onComplete()
            }
        }
    }

    // --- AI Smart Repair Assistant Conversation State ---
    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<MessageEntity>> = _currentConversationId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessages(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dedicated System Instruction for Home Appliance Repair Expert AI
    private val repairAssistantSystemInstruction = """
        شما یک تعمیرکار فوق‌العاده با‌تجربه، دقیق، دلسوز و حرفه‌ای لوازم خانگی به نام کدیار۲۴ (Codyar24) هستید. 
        شما باید به زبان فارسی صمیمی، روان، مودبانه و بسیار فنی، کاربران و تعمیرکاران را برای عیب‌یابی و حل مشکلات لوازم خانگی مانند ماشین لباسشویی، یخچال، فریزر، ماشین ظرفشویی، کولر گازی، اسپلیت، جاروبرقی، مایکروویو و سایر تجهیزات هدایت کنید.
        
        لطفاً هنگام پاسخ دادن به هر سوال عیب‌یابی:
        1. علت‌های احتمالی (مثلاً خرابی قطعه، اتصالات، کثیفی فیلترها و غیره) را به صورت لیست‌وار و خوانا بنویسید.
        2. مراحل گام‌به‌گام برای تست و رفع عیب ارائه دهید.
        3. نکات ایمنی (مانند کشیدن دوشاخه از برق قبل از هر تستی) را حتماً متذکر شوید.
        4. در صورت نیاز به بررسی تخصصی‌تر، بگویید که نیاز به تکنسین مجاز است.
        پاسخ‌های شما باید کاملاً متمرکز بر حل مشکل و کاربردی باشند.
    """.trimIndent()

    fun selectConversation(id: Long?) {
        _currentConversationId.value = id
        _errorMessage.value = null
    }

    fun startNewConversation() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val conversationId = repository.createConversation("گفتگوی جدید تعمیراتی", "Codyar24")
                _currentConversationId.value = conversationId
                
                // Seed custom welcoming message in Persian
                val welcome = MessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    text = "سلام! من کدیار۲۴ دستیار هوشمند و تخصصی تعمیرات لوازم خانگی شما هستم. دستگاه شما چه مشکلی دارد؟ یا به دنبال کدام کد خطا هستید؟ برند و مشکل آن را مطرح کنید تا با هم عیب‌یابی کنیم."
                )
                // Use sendMessage or a custom direct insertion
            } catch (e: Exception) {
                _errorMessage.value = "خطا در شروع گفتگو: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
            }
            repository.deleteConversation(id)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            _currentConversationId.value = null
            repository.clearAllConversations()
        }
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                var convId = _currentConversationId.value

                // If no active conversation, create one dynamically
                if (convId == null) {
                    val generatedTitle = if (trimmedText.length > 25) {
                        "${trimmedText.take(22)}..."
                    } else {
                        trimmedText
                    }
                    convId = repository.createConversation(generatedTitle, "Codyar24")
                    _currentConversationId.value = convId
                }

                if (convId != null) {
                    val result = repository.sendMessage(
                        conversationId = convId,
                        userText = trimmedText,
                        systemInstructionText = repairAssistantSystemInstruction
                    )
                    
                    result.onFailure { exception ->
                        _errorMessage.value = exception.localizedMessage ?: "خطای ارتباط با سرور هوش مصنوعی"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "خطا در ارسال پیام: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun setupNetworkCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("AssistantViewModel", "Internet connected dynamically! Refreshing database...")
                    if (!hasLoadedFromNetwork) {
                        viewModelScope.launch {
                            loadKodyarDatabase()
                            fetchSubscriptionPlans()
                        }
                    }
                }
            }
            networkCallback = callback
            connectivityManager.registerNetworkCallback(networkRequest, callback)
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to register network callback.", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to unregister network callback on clear.", e)
            }
        }
    }
}

class AssistantViewModelFactory(
    private val repository: AssistantRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AssistantViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class AppUpdateNotification(
    val newErrors: List<com.example.data.model.KodyarErrorCode>,
    val newProblems: List<com.example.data.model.KodyarCommonProblem>,
    val newParts: List<com.example.data.model.KodyarSparePart>
)

