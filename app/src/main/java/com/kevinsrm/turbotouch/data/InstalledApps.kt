package com.kevinsrm.turbotouch.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

/** Apps instalados com icone de launcher. */
object InstalledApps {

    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = try {
            pm.queryIntentActivities(launcherIntent, 0)
        } catch (t: Throwable) {
            emptyList()
        }
        val launcherPackages = resolved.map { it.activityInfo.packageName }.toSet()

        val installed = try {
            pm.getInstalledApplications(0)
        } catch (t: Throwable) {
            emptyList()
        }

        return installed
            .map { info ->
                val pkg = info.packageName
                val label = try {
                    pm.getApplicationLabel(info).toString()
                } catch (t: Throwable) {
                    pkg
                }
                val system = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                InstalledApp(pkg, label, system)
            }
            .filter { it.packageName in launcherPackages || !it.isSystem }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
