package me.bmax.apatch.ui.navigation

import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class CommitOnlyBackDispatcherTest {
    @Test
    fun predictiveCallbackReceivesCompletionWithoutAdvertisingProgress() {
        val platform = RecordingDispatcher()
        val dispatcher = CommitOnlyBackDispatcher(platform)
        var completed = 0
        val callback = object : OnBackAnimationCallback {
            override fun onBackInvoked() { completed++ }
        }

        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)

        assertFalse(platform.registered is OnBackAnimationCallback)
        assertEquals(0, completed)
        platform.registered!!.onBackInvoked()
        assertEquals(1, completed)
    }

    @Test
    fun unregisterRemovesTheRegisteredWrapper() {
        val platform = RecordingDispatcher()
        val dispatcher = CommitOnlyBackDispatcher(platform)
        val callback = OnBackInvokedCallback {}
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        val wrapper = platform.registered

        dispatcher.unregisterOnBackInvokedCallback(callback)

        assertSame(wrapper, platform.unregistered)
    }

    @Test
    fun repeatedRegistrationKeepsCallbackIdentityAndPriority() {
        val platform = RecordingDispatcher()
        val dispatcher = CommitOnlyBackDispatcher(platform)
        val callback = OnBackInvokedCallback {}
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        val wrapper = platform.registered

        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)

        assertSame(wrapper, platform.registered)
        assertEquals(OnBackInvokedDispatcher.PRIORITY_OVERLAY, platform.priority)
    }

    @Test
    fun distinctCallbacksRemainIndependentlyRemovable() {
        val platform = RecordingDispatcher()
        val dispatcher = CommitOnlyBackDispatcher(platform)
        val first = OnBackInvokedCallback {}
        val second = OnBackInvokedCallback {}
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, first)
        val firstWrapper = platform.registered
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, second)
        val secondWrapper = platform.registered

        dispatcher.unregisterOnBackInvokedCallback(first)
        assertSame(firstWrapper, platform.unregistered)
        dispatcher.unregisterOnBackInvokedCallback(second)
        assertSame(secondWrapper, platform.unregistered)
    }

    private class RecordingDispatcher : OnBackInvokedDispatcher {
        var priority = -1
        var registered: OnBackInvokedCallback? = null
        var unregistered: OnBackInvokedCallback? = null

        override fun registerOnBackInvokedCallback(priority: Int, callback: OnBackInvokedCallback) {
            this.priority = priority
            registered = callback
        }

        override fun unregisterOnBackInvokedCallback(callback: OnBackInvokedCallback) {
            unregistered = callback
        }
    }
}
