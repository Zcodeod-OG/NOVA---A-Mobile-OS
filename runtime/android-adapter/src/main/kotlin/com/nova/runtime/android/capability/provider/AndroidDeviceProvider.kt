package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.capability.CapabilityOperations
import com.nova.runtime.android.intentAdapter.IntentOperations
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityValidationResult
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Device capabilities backed by the Intent adapter: real app launching by name and
 * in-app search via deep links with a default-browser fallback.
 */
class AndroidDeviceProvider(
    context: Context,
    logger: NovaLogger,
    private val adapters: AndroidAdapterLayer,
    private val appCatalog: InstalledAppCatalog = PackageManagerAppCatalog(context),
) : AdapterDelegatingCapabilityProvider(context, logger) {

    override val providerId: String = "android-device"
    override val capabilityType: String = "device"
    override val version: String = "1.0.0"

    private val appNameResolver = AppNameResolver(appCatalog)

    // Both qualified and short names: pipeline requests arrive with the short form.
    override fun supportedOperations(): Set<String> =
        setOf(
            CapabilityOperations.DEVICE_OPEN_APP,
            OPEN_APP_SHORT,
            CapabilityOperations.DEVICE_APP_SEARCH,
            APP_SEARCH_SHORT,
        )

    override fun requiredPermissions(): Set<String> = emptySet()

    override suspend fun validateOperation(
        request: CapabilityExecutionRequest,
    ): CapabilityValidationResult =
        when (request.operation) {
            CapabilityOperations.DEVICE_OPEN_APP, OPEN_APP_SHORT -> {
                if (request.parameters["appName"].isNullOrBlank() &&
                    request.parameters["packageName"].isNullOrBlank()
                ) {
                    invalidParameters("appName or packageName is required to open an app")
                } else {
                    CapabilityValidationResult.Valid
                }
            }
            CapabilityOperations.DEVICE_APP_SEARCH, APP_SEARCH_SHORT -> {
                if (request.parameters["searchQuery"].isNullOrBlank() &&
                    request.parameters["query"].isNullOrBlank()
                ) {
                    invalidParameters("searchQuery is required for in-app search")
                } else {
                    CapabilityValidationResult.Valid
                }
            }
            else -> CapabilityValidationResult.Valid
        }

    override suspend fun dispatch(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        when (operation) {
            CapabilityOperations.DEVICE_OPEN_APP, OPEN_APP_SHORT -> openApp(parameters, traceId)
            CapabilityOperations.DEVICE_APP_SEARCH, APP_SEARCH_SHORT -> appSearch(parameters, traceId)
            else -> error("unsupported operation: $operation")
        }

    private suspend fun openApp(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        val appName = parameters["appName"]?.trim().orEmpty()

        if (appName.lowercase() in SETTINGS_NAMES) {
            val result = adapters.intents.execute(
                operation = IntentOperations.LAUNCH_SETTINGS,
                parameters = emptyMap(),
                traceId = traceId,
            )
            return result.withOutput("appName" to appName)
        }

        val packageName = parameters["packageName"]?.takeIf { it.isNotBlank() }
            ?: appNameResolver.resolvePackage(appName)

        if (packageName != null) {
            val result = adapters.intents.execute(
                operation = IntentOperations.OPEN_APP,
                parameters = mapOf("packageName" to packageName),
                traceId = traceId,
            )
            return result.withOutput(
                "appName" to appName.ifBlank { packageName },
                "resolvedPackage" to packageName,
                "target" to "app",
            )
        }

        // Missing app → open the web equivalent in the default browser.
        return openAppInBrowser(appName, traceId)
    }

    private suspend fun openAppInBrowser(
        appName: String,
        traceId: UUID,
    ): CapabilityResult {
        val lower = appName.lowercase()
        val url = when {
            lower in YOUTUBE_NAMES -> "https://www.youtube.com"
            lower in SPOTIFY_NAMES -> "https://open.spotify.com"
            lower in MAPS_NAMES -> "https://www.google.com/maps"
            else ->
                "https://www.google.com/search?q=" +
                    URLEncoder.encode(appName, StandardCharsets.UTF_8.name())
        }
        val result = adapters.intents.execute(
            operation = IntentOperations.OPEN_URL,
            parameters = mapOf("url" to url),
            traceId = traceId,
        )
        return result.withOutput(
            "appName" to appName,
            "target" to "browser",
        )
    }

    private suspend fun appSearch(
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        val query = parameters["searchQuery"]?.takeIf { it.isNotBlank() }
            ?: parameters["query"].orEmpty()
        val appName = parameters["appName"]?.trim()?.lowercase().orEmpty()
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())

        val target = resolveSearchTarget(appName, query, encodedQuery)

        val result = adapters.intents.execute(
            operation = IntentOperations.OPEN_URL,
            parameters = buildMap {
                put("url", target.url)
                target.packageName?.let { put("packageName", it) }
            },
            traceId = traceId,
        )
        return result.withOutput(
            "searchQuery" to query,
            "appName" to appName,
            "target" to if (target.packageName != null) "app" else "browser",
        )
    }

    private data class SearchTarget(val url: String, val packageName: String?)

    private fun resolveSearchTarget(
        appName: String,
        query: String,
        encodedQuery: String,
    ): SearchTarget = when {
        appName in YOUTUBE_NAMES ->
            SearchTarget(
                url = "https://www.youtube.com/results?search_query=$encodedQuery",
                packageName = YOUTUBE_PACKAGE.takeIf { appCatalog.isInstalled(it) },
            )
        appName in SPOTIFY_NAMES ->
            if (appCatalog.isInstalled(SPOTIFY_PACKAGE)) {
                SearchTarget(url = "spotify:search:$encodedQuery", packageName = SPOTIFY_PACKAGE)
            } else {
                SearchTarget(
                    url = "https://open.spotify.com/search/$encodedQuery",
                    packageName = null,
                )
            }
        appName in MAPS_NAMES ->
            if (appCatalog.isInstalled(MAPS_PACKAGE)) {
                SearchTarget(url = "geo:0,0?q=$encodedQuery", packageName = MAPS_PACKAGE)
            } else {
                SearchTarget(url = "https://www.google.com/maps/search/$encodedQuery", packageName = null)
            }
        appName in BROWSER_NAMES || appName.isBlank() ->
            SearchTarget(url = "https://www.google.com/search?q=$encodedQuery", packageName = null)
        else ->
            // No known in-app search deep link — fall back to a web search in the
            // default browser, scoped with the app name for relevance.
            SearchTarget(
                url = "https://www.google.com/search?q=" +
                    URLEncoder.encode("$query $appName", StandardCharsets.UTF_8.name()),
                packageName = null,
            )
    }

    private fun CapabilityResult.withOutput(vararg extras: Pair<String, String>): CapabilityResult =
        when (this) {
            is CapabilityResult.Success -> CapabilityResult.Success(output + extras.toMap())
            is CapabilityResult.Failure -> this
        }

    private fun invalidParameters(detail: String): CapabilityValidationResult.Invalid =
        CapabilityValidationResult.Invalid(
            com.nova.runtime.models.RuntimeError(
                code = "CAPABILITY_INVALID_PARAMETERS",
                category = com.nova.runtime.models.ErrorCategory.VALIDATION,
                severity = com.nova.runtime.models.ErrorSeverity.LOW,
                recoverable = false,
                userVisibleMessage = detail,
                diagnostics = mapOf("providerId" to providerId),
            ),
        )

    private companion object {
        const val OPEN_APP_SHORT = "open_app"
        const val APP_SEARCH_SHORT = "app_search"

        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val MAPS_PACKAGE = "com.google.android.apps.maps"

        val SETTINGS_NAMES = setOf("settings", "setting", "system settings")
        val YOUTUBE_NAMES = setOf("youtube", "yt")
        val SPOTIFY_NAMES = setOf("spotify")
        val MAPS_NAMES = setOf("maps", "google maps", "map")
        val BROWSER_NAMES = setOf("browser", "chrome", "google", "the web", "web", "internet")
    }
}
