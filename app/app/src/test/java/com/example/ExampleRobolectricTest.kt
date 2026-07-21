package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("کدیار24", appName)
  }

  @Test
  fun `test billing flow launch`() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
    val billingManager = activity.bazaarBillingManager
    // Call launchPurchaseFlow and verify it compiles, executes, and returns a boolean without throwing an exception
    val result = billingManager.launchPurchaseFlow(activity, "1_month")
    println("Billing launch result: $result")
  }
}
