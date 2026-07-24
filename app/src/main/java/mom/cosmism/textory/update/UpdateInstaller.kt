package mom.cosmism.textory.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

internal enum class InstallLaunchResult { INSTALLER, PERMISSION_SETTINGS }

internal object UpdateInstaller {
    fun launch(context: Context, apkFile: File): InstallLaunchResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallLaunchResult.PERMISSION_SETTINGS
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        return InstallLaunchResult.INSTALLER
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
