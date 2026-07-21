package com.example.billing

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.ConnectionState
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BazaarBillingManager(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        const val TAG = "BazaarBilling"
        const val BAZAAR_PUBLIC_KEY =
            "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwClvraihE80YiRtjgtmlctO0BO5fq1epUWH8q8L36Q30Dd9XXgMte5ijHfoC9vjBw37SGuYedlBMga1w/0KcaVC/CiAdza/+bVkIU1GjVTxZ14489JkjTka4uuJdvS1ciQMB8lKL6lKbyNrnSubpjVEHmPNwrO86ezkFCxF5Y/sd66Q1StUlKOAESqLX/RbAU2mVzJbjc9fCvggN36pX72Ma2SOEMxqKwaUZPM55UCAwEAAQ=="
    }

    private val config = PaymentConfiguration(localSecurityCheck = SecurityCheck.Enable(BAZAAR_PUBLIC_KEY))
    private val payment by lazy { Payment(context = appContext, config = config) }
    private var connection: Connection? = null

    // به‌جای تکیه بر یک وضعیت "در حال اتصال" که در کتابخانه وجود ندارد،
    // خودمان جلوی اتصال دوباره را می‌گیریم.
    @Volatile
    private var isConnecting: Boolean = false

    private var pendingSku: String? = null
    private var pendingActivity: ComponentActivity? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> get() = _connected

    private val _ownedSubscriptions = MutableStateFlow<List<String>>(emptyList())
    val ownedSubscriptions: StateFlow<List<String>> get() = _ownedSubscriptions

    // برای رفع Race Condition: هر دو نتیجه (اشتراک‌ها و خریدهای معمولی) در یک آبجکت قفل‌شده نگه‌داری می‌شوند
    private val queryLock = Any()
    private var lastSubscriptions: List<String> = emptyList()
    private var lastPurchases: List<String> = emptyList()

    fun isBazaarInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.farsitel.bazaar", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun startConnection() {
        if (!isBazaarInstalled(appContext)) return
        if (connection?.getState() == ConnectionState.Connected) {
            _connected.value = true
            queryPurchasedSubscriptions()
            return
        }
        if (isConnecting) return
        isConnecting = true

        connection = payment.connect {
            connectionSucceed {
                isConnecting = false
                _connected.value = true
                queryPurchasedSubscriptions()
                val sku = pendingSku
                val act = pendingActivity
                if (sku != null && act != null) {
                    pendingSku = null
                    pendingActivity = null
                    doPurchase(act, sku)
                }
            }
            connectionFailed {
                isConnecting = false
                _connected.value = false
            }
            disconnected {
                isConnecting = false
                _connected.value = false
            }
        }
    }

    /**
     * شروع فرآیند خرید. فقط با ComponentActivity قابل استفاده است
     * تا از کرش احتمالی cast نامعتبر جلوگیری شود.
     */
    fun launchPurchaseFlow(activity: ComponentActivity, sku: String): Boolean {
        Log.d(TAG, "launchPurchaseFlow صدا زده شد با sku = $sku")
        if (!isBazaarInstalled(activity)) {
            Toast.makeText(activity, "برنامه بازار نصب نیست.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (connection?.getState() == ConnectionState.Connected) {
            doPurchase(activity, sku)
        } else {
            pendingSku = sku
            pendingActivity = activity
            Toast.makeText(activity, "در حال اتصال به بازار...", Toast.LENGTH_SHORT).show()
            startConnection()
        }
        return true
    }

    /**
     * نمایش Toast فقط وقتی اکتیویتی هنوز زنده است، تا از کرش
     * روی اکتیویتی نابود‌شده جلوگیری شود.
     */
    private fun safeToast(activity: ComponentActivity, message: String, length: Int = Toast.LENGTH_LONG) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            Toast.makeText(activity, message, length).show()
        }
    }

    private fun doPurchase(activity: ComponentActivity, sku: String) {
        Log.d(TAG, "doPurchase شروع شد، connection state = ${connection?.getState()}")
        val request = PurchaseRequest(productId = sku, payload = "kadyar24_premium_access")
        try {
            val registry = activity.activityResultRegistry

            payment.subscribeProduct(registry = registry, request = request) {
                purchaseFlowBegan { Log.d(TAG, "درگاه خرید اشتراک باز شد.") }
                failedToBeginFlow { throwable ->
                    Log.w(TAG, "شروع خرید اشتراک ناموفق بود، تلاش برای خرید معمولی... خطا: ${throwable?.message}")
                    payment.purchaseProduct(registry = registry, request = request) {
                        purchaseFlowBegan { Log.d(TAG, "درگاه خرید محصول باز شد.") }
                        failedToBeginFlow { e ->
                            Log.e(TAG, "شروع خرید محصول ناموفق بود: ${e?.message}", e)
                            safeToast(activity, "خطا در شروع فرآیند خرید از بازار: ${e?.message}")
                        }
                        purchaseSucceed { _ ->
                            safeToast(activity, "خرید با موفقیت انجام شد!")
                            queryPurchasedSubscriptions()
                        }
                        purchaseCanceled { safeToast(activity, "خرید لغو شد.", Toast.LENGTH_SHORT) }
                        purchaseFailed { e ->
                            Log.e(TAG, "خطای خرید محصول: ${e?.message}", e)
                            safeToast(activity, "خطا در خرید محصول: ${e?.message}")
                        }
                    }
                }
                purchaseSucceed { _ ->
                    safeToast(activity, "اشتراک با موفقیت فعال شد!")
                    queryPurchasedSubscriptions()
                }
                purchaseCanceled { safeToast(activity, "خرید لغو شد.", Toast.LENGTH_SHORT) }
                purchaseFailed { throwable ->
                    Log.e(TAG, "خطای خرید اشتراک: ${throwable?.message}", throwable)
                    safeToast(activity, "خطا در خرید: ${throwable?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطای اجرای خرید: ${e.message}", e)
            safeToast(activity, "خطا: ${e.message}")
        }
    }

    fun queryPurchasedSubscriptions() {
        if (connection?.getState() != ConnectionState.Connected) return

        payment.getSubscribedProducts {
            querySucceed { products ->
                synchronized(queryLock) {
                    lastSubscriptions = products.map { it.productId }
                    _ownedSubscriptions.value = (lastSubscriptions + lastPurchases).distinct()
                }
            }
            queryFailed {
                synchronized(queryLock) {
                    lastSubscriptions = emptyList()
                    _ownedSubscriptions.value = (lastSubscriptions + lastPurchases).distinct()
                }
            }
        }

        payment.getPurchasedProducts {
            querySucceed { products ->
                synchronized(queryLock) {
                    lastPurchases = products.map { it.productId }
                    _ownedSubscriptions.value = (lastSubscriptions + lastPurchases).distinct()
                }
            }
            queryFailed {
                synchronized(queryLock) {
                    lastPurchases = emptyList()
                    _ownedSubscriptions.value = (lastSubscriptions + lastPurchases).distinct()
                }
            }
        }
    }

    fun endConnection() {
        connection?.disconnect()
        connection = null
        isConnecting = false
        _connected.value = false
    }
}