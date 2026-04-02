package io.github.raven_wing.cuview.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.os.Bundle
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Regression test for: onUpdate() called goAsync() internally, but
// GlanceAppWidgetReceiver.onUpdate() already calls goAsync() (via Glance's
// CoroutineBroadcastReceiver.goAsync extension), consuming the PendingResult.
// The second call from our onUpdate() returned null → pendingResult.finish() threw NPE →
// process crash → WidgetConfigActivity force-finished → widget disappeared from home screen.
//
// The test sets mPendingResult via reflection so Glance's goAsync() call in super.onUpdate()
// gets a valid (finish-safe) result, leaving mPendingResult null. Before the fix, our code
// then called goAsync() again → null → NPE in the coroutine → uncaught exception.
// After the fix, our code does not call goAsync() → no crash.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CUViewWidgetReceiverOnUpdateTest {

    @Test
    fun onUpdate_doesNotCrash_whenPendingResultAlreadyConsumedByGlance() {
        var uncaughtException: Throwable? = null
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaughtException = e }

        try {
            val receiver = CUViewWidgetReceiver()
            val context = RuntimeEnvironment.getApplication()

            // Inject a PendingResult so Glance's goAsync() call in super.onUpdate() gets a
            // valid (non-null) object. TYPE_REGISTERED(1) + ordered=false makes finish() a
            // no-op (no IPC to ActivityManager needed), safe in Robolectric.
            injectPendingResult(receiver)

            receiver.onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf())

            // Allow IO coroutine (and its finally block, before fix) to execute.
            Thread.sleep(500)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }

        assertNull("CUViewWidgetReceiver.onUpdate crashed: $uncaughtException", uncaughtException)
    }

    private fun injectPendingResult(receiver: BroadcastReceiver) {
        // BroadcastReceiver$PendingResult(resultCode, resultData, resultExtras,
        //     type, ordered, sticky, token, userId, flags)
        // type=1 (TYPE_REGISTERED), ordered=false → finish() is a no-op.
        val pendingResultClass = Class.forName("android.content.BroadcastReceiver\$PendingResult")
        val ctor = pendingResultClass.getDeclaredConstructor(
            Int::class.java, String::class.java, Bundle::class.java,
            Int::class.java, Boolean::class.java, Boolean::class.java,
            android.os.IBinder::class.java, Int::class.java, Int::class.java,
        )
        ctor.isAccessible = true
        val pendingResult = ctor.newInstance(0, null, null, 1, false, false, null, 0, 0)

        val setPendingResult = BroadcastReceiver::class.java
            .getDeclaredMethod("setPendingResult", pendingResultClass)
        setPendingResult.isAccessible = true
        setPendingResult.invoke(receiver, pendingResult)
    }
}
