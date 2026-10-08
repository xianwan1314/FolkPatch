package me.bmax.apatch.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import me.bmax.apatch.util.SafeUriResolver
import java.io.File

/**
 * Launches the platform crop UI for a picked image and hands back the cropped
 * image's URI.
 *
 * This is the flow the appearance settings already used for wallpapers, lifted
 * out so other features (the profile avatar) reuse it instead of growing a
 * second cropper.
 *
 * @param cacheName file inside the cache dir used as crop input and output
 * @param aspectX aspect numerator, or 0 to use the screen aspect
 * @param aspectY aspect denominator, or 0 to use the screen aspect
 * @param outputSize forced square edge in px, or 0 to use the screen size
 */
@Composable
fun rememberSystemCropLauncher(
    cacheName: String,
    aspectX: Int = 0,
    aspectY: Int = 0,
    outputSize: Int = 0,
    onCropped: (Uri) -> Unit,
): ManagedActivityResultLauncher<Uri, Uri?> {
    val contract = remember(cacheName, aspectX, aspectY, outputSize) {
        object : ActivityResultContract<Uri, Uri?>() {
            override fun createIntent(context: Context, input: Uri): Intent {
                val tempFile = File(context.cacheDir, cacheName).apply {
                    parentFile?.mkdirs()
                    delete()
                    createNewFile()
                    deleteOnExit()
                }

                SafeUriResolver.openInputStream(context, input).use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val tempUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                val displayMetrics = context.resources.displayMetrics
                val ax = if (aspectX > 0) aspectX else displayMetrics.widthPixels
                val ay = if (aspectY > 0) aspectY else displayMetrics.heightPixels
                val ox = if (outputSize > 0) outputSize else displayMetrics.widthPixels
                val oy = if (outputSize > 0) outputSize else displayMetrics.heightPixels

                return Intent("com.android.camera.action.CROP").apply {
                    setDataAndType(tempUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    putExtra("crop", "true")
                    putExtra("aspectX", ax)
                    putExtra("aspectY", ay)
                    putExtra("outputX", ox)
                    putExtra("outputY", oy)
                    putExtra("return-data", false)
                    putExtra(MediaStore.EXTRA_OUTPUT, tempUri)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
                return if (resultCode == Activity.RESULT_OK) intent?.data else null
            }
        }
    }

    return rememberLauncherForActivityResult(contract) { uri ->
        uri?.let(onCropped)
    }
}
