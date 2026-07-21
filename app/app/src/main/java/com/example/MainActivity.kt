package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.billing.BazaarBillingManager
import com.example.data.AssistantRepository
import com.example.data.api.RetrofitClient
import com.example.data.db.AppDatabase
import com.example.ui.AssistantViewModel
import com.example.ui.AssistantViewModelFactory
import com.example.ui.screens.AssistantScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var bazaarBillingManager: BazaarBillingManager
    lateinit var viewModel: AssistantViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AssistantRepository(database.assistantDao(), RetrofitClient.service)

        viewModel = ViewModelProvider(
            this,
            AssistantViewModelFactory(repository, applicationContext)
        )[AssistantViewModel::class.java]

     bazaarBillingManager = BazaarBillingManager(applicationContext)
        bazaarBillingManager.startConnection()

        lifecycleScope.launch {
            bazaarBillingManager.ownedSubscriptions.collect { ownedSkus ->
                // بررسی دقیق تمام شناسههای اشتراک بازار برای کدیار ۲۴
                val activeSku = ownedSkus.firstOrNull {
                    it in listOf("ir.golden.com", "ir.silver.com", "ir.almas.com", "ir.12-month.com")
                } ?: ""
                // فعال کردن وضعیت ویژه در صورت وجود اشتراک معتبر
                viewModel.setPremiumUserLocally(activeSku)
            }
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AssistantScreen(
                        viewModel = viewModel,
                        onPurchasePlan = { sku ->
                            // ارجاع مستقیم رویداد کلیک لایه کامپوز به متد اجرای خرید اکتیویتی اصلی
                            bazaarBillingManager.launchPurchaseFlow(this@MainActivity, sku)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bazaarBillingManager.startConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        bazaarBillingManager.endConnection()
    }
}
