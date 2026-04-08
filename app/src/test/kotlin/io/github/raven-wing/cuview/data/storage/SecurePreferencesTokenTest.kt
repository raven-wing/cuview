package io.github.raven_wing.cuview.data.storage

import android.content.Context
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Regression test: tapping "Disconnect" in WidgetConfigActivity was not clearing the stored
// token from SecurePreferences. On next open, LaunchedEffect(Unit) would read the old token
// and set isConnected=true, making the app appear still connected. The fix calls
// SecurePreferences.apiToken = null inside the onDisconnect lambda.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurePreferencesTokenTest {

    // Use the internal test constructor to avoid EncryptedSharedPreferences / Keystore
    // which Robolectric doesn't support.
    private val prefs = SecurePreferences(
        RuntimeEnvironment.getApplication().getSharedPreferences("test_token", Context.MODE_PRIVATE)
    )

    @Test
    fun settingApiTokenToNull_clearsPersistedToken() {
        prefs.apiToken = "pk_sometoken"

        prefs.apiToken = null

        assertNull(prefs.apiToken)
    }
}
