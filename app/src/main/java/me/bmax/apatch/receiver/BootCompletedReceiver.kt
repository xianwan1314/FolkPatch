package me.bmax.apatch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread
import me.bmax.apatch.util.ApdExecResult
import me.bmax.apatch.util.ShizukuServiceManager
import me.bmax.apatch.util.execApdBootFallback

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val pendingResult = goAsync()

        thread(name = "fp-boot-fallback") {
            try {
                val retryDelaysMs = longArrayOf(0L, 15_000L, 30_000L)
                var fallbackSucceeded = false
                for ((index, delayMs) in retryDelaysMs.withIndex()) {
                    if (delayMs > 0) {
                        Thread.sleep(delayMs)
                    }

                    Log.i(
                        TAG,
                        "Boot fallback attempt ${index + 1}/${retryDelaysMs.size}: triggering manager-boot-completed"
                    )
                    val result = try {
                        execApdBootFallback("manager-boot-completed")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Boot fallback attempt ${index + 1} crashed before completion", t)
                        null
                    }

                    if (result != null && result.success) {
                        Log.i(
                            TAG,
                            "Boot fallback succeeded on attempt ${index + 1}: ${formatResult(result)}"
                        )
                        fallbackSucceeded = true
                        break
                    }

                    if (result != null) {
                        Log.w(
                            TAG,
                            "Boot fallback attempt ${index + 1} failed: ${formatResult(result)}"
                        )
                    }
                }

                if (!fallbackSucceeded) {
                    Log.e(TAG, "Boot fallback failed after all retry attempts")
                }

                startShizukuIfEnabled(context)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Boot fallback interrupted", e)
            } catch (t: Throwable) {
                Log.e(TAG, "Boot fallback crashed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "FPBootReceiver"

        private fun startShizukuIfEnabled(context: Context) {
            if (!ShizukuServiceManager.isEnabled()) {
                return
            }

            Log.i(TAG, "Shizuku auto-start begin")
            for (attempt in 1..2) {
                if (attempt > 1) {
                    Thread.sleep(10_000L)
                }
                try {
                    if (ShizukuServiceManager.isServerRunning() || ShizukuServiceManager.start(context)) {
                        Log.i(TAG, "Shizuku server auto-start succeeded on attempt $attempt")
                        return
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Shizuku auto-start attempt $attempt failed", t)
                }
            }
            Log.w(TAG, "Shizuku server auto-start failed after all attempts")
        }

        private fun formatResult(result: ApdExecResult): String {
            val parts = mutableListOf("command=${result.commandLabel}")
            result.exitCode?.let { parts += "exit=$it" }
            result.errorMessage?.takeIf { it.isNotBlank() }?.let { parts += "error=$it" }
            result.output
                .takeIf { it.isNotBlank() }
                ?.let { output ->
                    val compact = output.replace('\n', ' ').take(400)
                    parts += "output=$compact"
                }
            return parts.joinToString(", ")
        }
    }
}
