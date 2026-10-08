package me.bmax.apatch.ui.navigation

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import java.util.IdentityHashMap

/**
 * Keeps AndroidX callback ordering while withholding predictive gesture progress.
 * A plain callback receives only completed gestures, so Navigation Compose performs
 * its normal pop transition and a cancelled gesture does not change the back stack.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class CommitOnlyBackDispatcher(
    private val delegate: OnBackInvokedDispatcher,
) : OnBackInvokedDispatcher {
    private val callbacks = IdentityHashMap<OnBackInvokedCallback, OnBackInvokedCallback>()

    override fun registerOnBackInvokedCallback(priority: Int, callback: OnBackInvokedCallback) {
        val commitOnly = callbacks.getOrPut(callback) {
            OnBackInvokedCallback { callback.onBackInvoked() }
        }
        delegate.registerOnBackInvokedCallback(priority, commitOnly)
    }

    override fun unregisterOnBackInvokedCallback(callback: OnBackInvokedCallback) {
        delegate.unregisterOnBackInvokedCallback(callbacks.remove(callback) ?: callback)
    }
}

/** Prevents the system back-to-home preview when no page callback is enabled. */
internal fun ComponentActivity.installNonPredictiveBackFallback() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Preserve Activity's default root behavior (including moving the task to
            // the background), rather than replacing it with finish().
            isEnabled = false
            try {
                onBackPressedDispatcher.onBackPressed()
            } finally {
                isEnabled = true
            }
        }
    })
}
