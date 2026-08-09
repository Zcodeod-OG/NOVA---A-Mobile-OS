package com.nova.runtime.android.capability.provider

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A launchable app installed on the device. */
data class InstalledApp(
    val label: String,
    val packageName: String,
)

/** Abstraction over PackageManager so app-name matching stays unit-testable. */
interface InstalledAppCatalog {
    fun installedApps(): List<InstalledApp>
    fun isInstalled(packageName: String): Boolean
}

/** Real catalog backed by PackageManager launcher queries. */
class PackageManagerAppCatalog(private val context: Context) : InstalledAppCatalog {
    override fun installedApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { resolveInfo ->
                InstalledApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName }
    }

    override fun isInstalled(packageName: String): Boolean {
        val packageManager = context.packageManager
        if (packageManager.getLaunchIntentForPackage(packageName) != null) return true

        // Fallback for OEMs / Android 16 where getLaunchIntentForPackage is empty
        // even though the launcher activity is visible via <queries>.
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        if (packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL).isNotEmpty()) {
            return true
        }

        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

/**
 * Resolves a spoken/typed app name ("youtube", "google maps") to a package name.
 * Uses a curated alias map first, then fuzzy PackageManager label matching so any
 * installed app can be opened by name.
 */
class AppNameResolver(private val catalog: InstalledAppCatalog) {

    fun resolvePackage(rawName: String): String? {
        val query = normalize(rawName)
        if (query.isBlank()) return null

        KNOWN_APP_PACKAGES[query]
            ?.firstOrNull { catalog.isInstalled(it) }
            ?.let { return it }

        val apps = catalog.installedApps()
        val labelled = apps.map { normalize(it.label) to it }

        labelled.firstOrNull { (label, _) -> label == query }?.let { return it.second.packageName }
        labelled.firstOrNull { (label, _) -> label.startsWith(query) }?.let { return it.second.packageName }
        labelled.firstOrNull { (label, _) -> query in label || label in query }
            ?.let { return it.second.packageName }

        val compactQuery = query.replace(" ", "")
        return apps.firstOrNull { compactQuery.isNotBlank() && compactQuery in it.packageName.lowercase() }
            ?.packageName
    }

    private fun normalize(name: String): String =
        name.lowercase()
            .trim()
            .removePrefix("the ")
            .removeSuffix(" app")
            .removeSuffix(" application")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        /** Common alias → candidate packages (first installed wins). */
        val KNOWN_APP_PACKAGES: Map<String, List<String>> = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "yt" to listOf("com.google.android.youtube"),
            "chrome" to listOf("com.android.chrome"),
            "browser" to listOf("com.android.chrome"),
            "whatsapp" to listOf("com.whatsapp"),
            "spotify" to listOf("com.spotify.music"),
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "gmail" to listOf("com.google.android.gm"),
            "mail" to listOf("com.google.android.gm"),
            "photos" to listOf("com.google.android.apps.photos"),
            "gallery" to listOf("com.google.android.apps.photos", "com.oneplus.gallery"),
            "camera" to listOf("com.oneplus.camera", "com.oplus.camera", "com.google.android.GoogleCamera"),
            "calendar" to listOf("com.google.android.calendar"),
            "clock" to listOf("com.google.android.deskclock", "com.oneplus.deskclock", "com.coloros.alarmclock"),
            "calculator" to listOf("com.google.android.calculator", "com.coloros.calculator"),
            "play store" to listOf("com.android.vending"),
            "playstore" to listOf("com.android.vending"),
            "settings" to listOf("com.android.settings"),
            "instagram" to listOf("com.instagram.android"),
            "facebook" to listOf("com.facebook.katana"),
            "telegram" to listOf("org.telegram.messenger"),
            "twitter" to listOf("com.twitter.android"),
            "x" to listOf("com.twitter.android"),
            "messages" to listOf("com.google.android.apps.messaging"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer"),
            "dialer" to listOf("com.google.android.dialer", "com.android.dialer"),
            "files" to listOf("com.google.android.apps.nbu.files", "com.coloros.filemanager"),
            "drive" to listOf("com.google.android.apps.docs"),
            "netflix" to listOf("com.netflix.mediaclient"),
        )
    }
}
