package com.bjkoop.frosttapper

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoclickerServiceTest {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.bjkoop.frosttapper", appContext.packageName)
    }

    @Test
    fun testServiceIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, OverlayService::class.java)
        assertNotNull(intent)
        assertEquals(OverlayService::class.java.name, intent.component?.className)
    }

    @Test
    fun testAccessibilityServiceIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, AutoClickService::class.java)
        assertNotNull(intent)
        assertEquals(AutoClickService::class.java.name, intent.component?.className)
    }
}
