package io.github.rex25415.haiyu.schedule.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class LegacyInstallInfo(
    val packageName: String,
    val versionName: String
)

/**
 * 零权限旧包名检测器（仅通过 Android 11+ 合规的 <queries> 单包声明）：
 * 当检测到设备仍保留 v2.7.0 及以前旧包名（cn.gdou.haiyu.schedule）时，
 * 温和引导用户完成数据迁移并一键前往系统应用信息页卸载旧图标，避免桌面双图标混淆。
 */
object LegacyPackageDetector {

    const val LEGACY_PACKAGE_NAME = "cn.gdou.haiyu.schedule"

    fun detectLegacyInstall(context: Context): LegacyInstallInfo? {
        if (context.packageName == LEGACY_PACKAGE_NAME) return null
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(LEGACY_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(LEGACY_PACKAGE_NAME, 0)
            }
            LegacyInstallInfo(
                packageName = LEGACY_PACKAGE_NAME,
                versionName = info.versionName?.takeIf { it.isNotBlank() } ?: "旧版"
            )
        } catch (_: Exception) {
            null
        }
    }

    fun launchLegacyApp(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(LEGACY_PACKAGE_NAME)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: return false
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 打开旧版包的系统「应用信息」页，由用户点击系统原生「卸载」按钮（零新增权限）
     */
    fun openLegacyAppUninstallSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$LEGACY_PACKAGE_NAME")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
