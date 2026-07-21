package nl.tstock.veren

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tstock_settings", Context.MODE_PRIVATE)
    val store = LocalStore(application)
    private val deviceUuid = prefs.getString("device_uuid", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_uuid", it).apply() }
    private val network = NetworkClient { state.serverUrl }
    private var lastForegroundRefreshAt = 0L
    private var receiveSuggestionConfirmedByServer = false
    private var serverUnlockUntilMillis = 0L

    var state by mutableStateOf(
        AppState(
            serverUrl = prefs.getString("server_url", "") ?: "",
            user = loadUser(),
            pendingCount = store.pendingCount(),
            conflictCount = store.mutationCount("CONFLICT"),
            failedCount = store.mutationCount("FAILED"),
            cachedLocationCount = store.locationCount(),
            cachedBundleCount = store.bundleCount(),
            lastSync = store.getMeta("last_sync", "Nog niet gesynchroniseerd"),
            workAreaKey = prefs.getString("work_area_key", "hoofdlocatie") ?: "hoofdlocatie",
            workAreaName = prefs.getString("work_area_name", "Hoofdlocatie") ?: "Hoofdlocatie",
            offlineProfileKey = prefs.getString("offline_profile_key", "paganelstraat") ?: "paganelstraat",
            offlineProfileName = prefs.getString("offline_profile_name", "Paganelstraat") ?: "Paganelstraat",
        )
    ); private set

    var receiveArticle by mutableStateOf("")
    var receiveContainer by mutableStateOf("")
    var receiveBundle by mutableStateOf("")
    var receiveQuantity by mutableStateOf("")
    var receiveReason by mutableStateOf("")
    var receiveSuggestedCode by mutableStateOf("")
    var receiveSuggestedName by mutableStateOf("")
    var receiveLocationScan by mutableStateOf("")

    var findScan by mutableStateOf("")
    var selectedBundle by mutableStateOf<JSONObject?>(null)
    var moveLocationScan by mutableStateOf("")
    var moveReason by mutableStateOf("Interne verplaatsing")
    var issueQuantity by mutableStateOf("")
    var issueReason by mutableStateOf("Productie")
    var stockSearch by mutableStateOf("")
    var stockRows by mutableStateOf(store.stock())
    var locationSearch by mutableStateOf("")
    var locationRows by mutableStateOf(store.locations())
    var mutationRows by mutableStateOf(store.mutationRows())

    init {
        if (state.serverUrl.isNotBlank()) startupRefresh(force = true)
    }

    private fun loadUser(): UserSession? {
        val raw = prefs.getString("user", null) ?: return null
        return try {
            val json = JSONObject(raw)
            UserSession(
                json.getLong("id"),
                json.getString("username"),
                json.getString("displayName"),
                json.getString("roleName"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveUser(user: UserSession?) {
        if (user == null) {
            prefs.edit().remove("user").apply()
        } else {
            prefs.edit().putString(
                "user",
                JSONObject()
                    .put("id", user.id)
                    .put("username", user.username)
                    .put("displayName", user.displayName)
                    .put("roleName", user.roleName)
                    .toString(),
            ).apply()
        }
    }

    private fun refreshOfflineStats() {
        state = state.copy(
            pendingCount = store.pendingCount(),
            conflictCount = store.mutationCount("CONFLICT"),
            failedCount = store.mutationCount("FAILED"),
            cachedLocationCount = store.locationCount(),
            cachedBundleCount = store.bundleCount(),
            lastSync = store.getMeta("last_sync", state.lastSync),
        )
    }

    fun setScreen(screen: Screen) {
        state = state.copy(screen = screen, message = "", error = "")
        when (screen) {
            Screen.STOCK -> refreshStock()
            Screen.LOCATIONS -> refreshLocations()
            Screen.SYNC -> refreshQueue()
            else -> Unit
        }
    }

    fun setServerUrl(value: String) {
        val clean = value.trim().trimEnd('/')
        if (clean.isBlank()) {
            state = state.copy(error = "Vul een geldig serveradres in.", message = "")
            return
        }
        val unlockStillValid = state.serverSettingsUnlocked && System.currentTimeMillis() < serverUnlockUntilMillis
        if (state.serverUrl.isNotBlank() && !unlockStillValid) {
            serverUnlockUntilMillis = 0L
            state = state.copy(serverSettingsUnlocked = false, error = "Ontgrendel de serverinstellingen eerst met een beheerlogin.", message = "")
            return
        }
        prefs.edit().putString("server_url", clean).apply()
        serverUnlockUntilMillis = 0L
        state = state.copy(serverUrl = clean, serverSettingsUnlocked = false, message = "Serveradres opgeslagen.", error = "")
        startupRefresh(force = true)
    }

    fun unlockServerSettings(username: String, secret: String, pinMode: Boolean) = runTask {
        if (username.isBlank() || secret.isBlank()) throw IllegalArgumentException("Vul de beheerlogin en toegangscode in.")
        network.post(
            "/api/mobile/admin/unlock",
            JSONObject().put("username", username.trim()).put("secret", secret).put("mode", if (pinMode) "pin" else "password"),
        )
        val expiresAt = System.currentTimeMillis() + 5 * 60 * 1000L
        serverUnlockUntilMillis = expiresAt
        state = state.copy(serverSettingsUnlocked = true, message = "Serverinstellingen zijn 5 minuten ontgrendeld.", error = "")
        viewModelScope.launch {
            delay(5 * 60 * 1000L)
            if (serverUnlockUntilMillis == expiresAt) lockServerSettings()
        }
    }

    fun lockServerSettings() {
        serverUnlockUntilMillis = 0L
        state = state.copy(serverSettingsUnlocked = false, message = "Serverinstellingen vergrendeld.")
    }

    fun setOfflineProfile(profile: OfflineProfile) {
        prefs.edit().putString("offline_profile_key", profile.key).putString("offline_profile_name", profile.name).apply()
        state = state.copy(offlineProfileKey = profile.key, offlineProfileName = profile.name, message = "Offline opslag ingesteld op ${profile.name}.")
        if (state.online && state.user != null) rebuildOfflineCache()
    }

    fun logout() {
        saveUser(null)
        state = state.copy(user = null, screen = Screen.HOME, message = "", error = "")
    }

    fun clearMessage() {
        state = state.copy(message = "", error = "")
    }

    fun login(username: String, secret: String, pinMode: Boolean) = runTask {
        val body = JSONObject().put("username", username).put(if (pinMode) "pin" else "password", secret)
        val result = network.post(if (pinMode) "/api/auth/login-pin" else "/api/auth/login-password", body)
        val u = result.getJSONObject("user")
        val user = UserSession(
            u.getLong("id"),
            u.getString("username"),
            u.getString("display_name"),
            u.getString("role_name"),
        )
        saveUser(user)
        state = state.copy(user = user, online = true, message = "Welkom ${user.displayName}.")
        registerDevice()
        syncAllInternal(showMessage = true)
    }

    fun checkConnection() = startupRefresh(force = true)

    fun onAppForeground() {
        startupRefresh(force = false)
    }

    private fun startupRefresh(force: Boolean) {
        if (state.serverUrl.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastForegroundRefreshAt < 30_000L) return
        lastForegroundRefreshAt = now

        viewModelScope.launch {
            try {
                network.get("/api/health")
                state = state.copy(online = true)
                loadWorkAreas()
                loadOfflineProfiles()
                if (state.user != null) {
                    syncAllInternal(showMessage = false)
                }
                loadUpdateInfo(showCurrentMessage = false)
            } catch (e: Exception) {
                state = state.copy(online = false, syncState = "Offline")
                refreshOfflineStats()
                if (state.user != null && store.locationCount() == 0) {
                    state = state.copy(
                        error = "Geen offline locaties opgeslagen. Verbind met de V10.5-server en druk bij Synchronisatie op Nu synchroniseren. ${e.message.orEmpty()}",
                    )
                }
            }
        }
    }


    private suspend fun loadWorkAreas() {
        try {
            val data = network.get("/api/mobile/work-areas")
            val rows = data.optJSONArray("workAreas") ?: data.optJSONArray("work_areas") ?: JSONArray()
            val areas = (0 until rows.length()).mapNotNull { index ->
                rows.optJSONObject(index)?.let { row ->
                    WorkArea(
                        key = row.optString("key", row.optString("work_area_key")),
                        name = row.optString("name", row.optString("key")),
                        description = row.optString("description"),
                        isDefault = row.optBoolean("is_default", false),
                    )
                }
            }.filter { it.key.isNotBlank() }
            if (areas.isNotEmpty()) {
                val selected = areas.find { it.key == state.workAreaKey } ?: areas.find { it.isDefault } ?: areas.first()
                prefs.edit().putString("work_area_key", selected.key).putString("work_area_name", selected.name).apply()
                state = state.copy(availableWorkAreas = areas, workAreaKey = selected.key, workAreaName = selected.name)
            }
        } catch (_: Exception) { }
    }

    fun setWorkArea(area: WorkArea) {
        if (state.pendingCount > 0) {
            state = state.copy(error = "Synchroniseer eerst de openstaande mutaties voordat je van werkgebied wisselt.", message = "")
            return
        }
        prefs.edit().putString("work_area_key", area.key).putString("work_area_name", area.name).apply()
        state = state.copy(workAreaKey = area.key, workAreaName = area.name, message = "Werkgebied ingesteld op ${area.name}.", error = "")
        resetReceive()
        selectedBundle = null
        if (state.online && state.user != null) rebuildOfflineCache()
    }

    private suspend fun loadOfflineProfiles() {
        try {
            val data = network.get("/api/mobile/profiles")
            val rows = data.optJSONArray("profiles") ?: JSONArray()
            val profiles = (0 until rows.length()).mapNotNull { index ->
                rows.optJSONObject(index)?.let { row ->
                    OfflineProfile(
                        key = row.optString("profile_key"),
                        name = row.optString("name", row.optString("profile_key")),
                        description = row.optString("description"),
                        isDefault = row.optBoolean("is_default", false),
                    )
                }
            }.filter { it.key.isNotBlank() }
            if (profiles.isNotEmpty()) {
                val selected = profiles.find { it.key == state.offlineProfileKey }
                    ?: profiles.find { it.isDefault } ?: profiles.first()
                prefs.edit().putString("offline_profile_key", selected.key).putString("offline_profile_name", selected.name).apply()
                state = state.copy(availableProfiles = profiles, offlineProfileKey = selected.key, offlineProfileName = selected.name)
            }
        } catch (_: Exception) {
            // Een oudere server of tijdelijk netwerkprobleem mag offline gebruik niet blokkeren.
        }
    }

    private suspend fun registerDevice() {
        val user = state.user ?: return
        try {
            network.post(
                "/api/mobile/device/register",
                JSONObject()
                    .put("deviceUuid", deviceUuid)
                    .put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("appVersionCode", BuildConfig.VERSION_CODE)
                    .put("appVersionName", BuildConfig.VERSION_NAME)
                    .put("userId", user.id)
                    .put("profileKey", state.offlineProfileKey)
                    .put("workAreaKey", state.workAreaKey),
            )
        } catch (_: Exception) {
            // Registratie mag offline gebruik niet blokkeren.
        }
    }

    fun requestScan(target: ScanTarget) {
        state = state.copy(scanTarget = target)
    }

    fun scanLaunched() {
        state = state.copy(scanTarget = null)
    }

    fun onScanResult(value: String) {
        when (state.scanTarget) {
            ScanTarget.ARTICLE -> {
                receiveArticle = value
                receiveSuggestedCode = ""
                receiveSuggestedName = ""
                receiveLocationScan = ""
                receiveSuggestionConfirmedByServer = false
            }
            ScanTarget.RECEIVE_LOCATION -> receiveLocationScan = value.trim().uppercase()
            ScanTarget.FIND_BUNDLE -> {
                findScan = value
                findBundle()
            }
            ScanTarget.MOVE_LOCATION -> moveLocationScan = value.trim().uppercase()
            ScanTarget.STOCK_SEARCH -> {
                stockSearch = value
                refreshStock()
            }
            null -> Unit
        }
        state = state.copy(scanTarget = null)
    }

    fun suggestLocation() = runTask {
        if (receiveArticle.isBlank()) throw IllegalArgumentException("Scan eerst het artikel.")
        receiveSuggestionConfirmedByServer = false
        val location = if (state.online) {
            try {
                network.post(
                    "/api/mobile/suggest-location",
                    JSONObject().put("articleNumber", receiveArticle).put("profileKey", state.offlineProfileKey).put("workAreaKey", state.workAreaKey),
                ).getJSONObject("suggestedLocation").also {
                    receiveSuggestionConfirmedByServer = true
                }
            } catch (e: Exception) {
                // Een internetverbinding betekent niet automatisch dat de T-Stock-server
                // bereikbaar is. Bij iedere verbindingsfout direct terugvallen op de
                // lokale cache van het actieve werkgebied.
                state = state.copy(online = false, syncState = "Offline")
                try {
                    store.suggestLocation(receiveArticle)
                } catch (offlineError: Exception) {
                    throw IllegalStateException(
                        "Server niet bereikbaar en geen passende vrije locatie in de offline cache van ${state.workAreaName}. " +
                            "Synchroniseer dit werkgebied één keer online. ${offlineError.message.orEmpty()}",
                    )
                }
            }
        } else {
            try {
                store.suggestLocation(receiveArticle)
            } catch (offlineError: Exception) {
                throw IllegalStateException(
                    "Geen passende vrije locatie in de offline cache van ${state.workAreaName}. " +
                        "Synchroniseer dit werkgebied één keer online. ${offlineError.message.orEmpty()}",
                )
            }
        }
        receiveSuggestedCode = location.optString("code")
        receiveSuggestedName = location.optString("display_name", receiveSuggestedCode)
        state = state.copy(message = "Vrije locatie: $receiveSuggestedName")
    }

    fun receiveLocationIsCorrect(): Boolean =
        store.locationCodesMatch(receiveSuggestedCode, receiveLocationScan)

    fun receiveBundle() = runTask {
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        val qty = receiveQuantity.toIntOrNull()
        val code = store.receiveOffline(
            receiveArticle,
            receiveSuggestedCode,
            receiveLocationScan,
            receiveContainer,
            receiveBundle,
            qty,
            receiveReason,
            user.id,
            deviceUuid,
            serverConfirmedFree = receiveSuggestionConfirmedByServer && state.online,
        )
        refreshOfflineStats()
        state = state.copy(
            message = "Bundel $code lokaal opgeslagen${if (state.online) "; synchroniseren..." else "."}",
        )
        resetReceive()
        if (state.online) syncAllInternal(showMessage = true)
    }

    private fun resetReceive() {
        receiveArticle = ""
        receiveContainer = ""
        receiveBundle = ""
        receiveQuantity = ""
        receiveReason = ""
        receiveSuggestedCode = ""
        receiveSuggestedName = ""
        receiveLocationScan = ""
        receiveSuggestionConfirmedByServer = false
    }

    fun findBundle() {
        try {
            val rows = store.findBundles(findScan)
            selectedBundle = rows.firstOrNull()
            state = if (selectedBundle == null) {
                state.copy(error = "Geen actieve bundel gevonden in lokale gegevens.", message = "")
            } else {
                state.copy(
                    message = "Bundel ${selectedBundle!!.optString("bundle_code")} gevonden.",
                    error = "",
                )
            }
        } catch (e: Exception) {
            state = state.copy(error = e.message ?: "Zoeken mislukt.", message = "")
        }
    }

    fun moveBundle() = runTask {
        val bundle = selectedBundle ?: throw IllegalArgumentException("Scan en selecteer eerst een bundel.")
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        store.moveOffline(bundle, moveLocationScan, moveReason, user.id)
        refreshOfflineStats()
        state = state.copy(message = "Verplaatsing lokaal opgeslagen.")
        selectedBundle = null
        findScan = ""
        moveLocationScan = ""
        if (state.online) syncAllInternal(showMessage = true)
    }

    fun issueBundle() = runTask {
        val bundle = selectedBundle ?: throw IllegalArgumentException("Scan en selecteer eerst een bundel.")
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        store.issueOffline(bundle, issueQuantity.toIntOrNull(), issueReason, user.id)
        refreshOfflineStats()
        state = state.copy(message = "Uitboeking lokaal opgeslagen.")
        selectedBundle = null
        findScan = ""
        issueQuantity = ""
        if (state.online) syncAllInternal(showMessage = true)
    }

    fun refreshStock() {
        stockRows = store.stock(stockSearch)
        refreshOfflineStats()
    }

    fun refreshLocations() {
        locationRows = store.locations(locationSearch)
        refreshOfflineStats()
    }

    fun refreshQueue() {
        mutationRows = store.mutationRows()
        refreshOfflineStats()
    }

    fun clearCancelledMutations() {
        store.clearCancelled()
        refreshQueue()
        state = state.copy(message = "Geannuleerde mutaties uit de lokale lijst verwijderd.")
    }

    fun syncAll() = runTask {
        syncAllInternal(showMessage = true)
    }

    fun rebuildOfflineCache() = runTask {
        if (!state.online) throw IllegalStateException("Maak eerst verbinding met de server.")
        // De bestaande cache blijft staan totdat een volledige nieuwe dataset succesvol
        // is ontvangen en atomair is opgeslagen. Een netwerkfout kan offline werken dus
        // niet meer onbedoeld leegmaken.
        syncAllInternal(showMessage = true)
        state = state.copy(message = "Offline cache veilig vernieuwd: ${store.locationCount()} locaties geladen.")
    }

    private suspend fun syncAllInternal(showMessage: Boolean) {
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        state = state.copy(syncState = "Synchroniseren…")
        val pending = store.pendingJson()
        if (pending.length() > 0) {
            val response = network.post(
                "/api/mobile/sync",
                JSONObject()
                    .put("deviceUuid", deviceUuid)
                    .put("userId", user.id)
                    .put("profileKey", state.offlineProfileKey)
                    .put("workAreaKey", state.workAreaKey)
                    .put("pendingCount", pending.length())
                    .put("mutations", pending),
            )
            store.applySyncResults(response.optJSONArray("results") ?: JSONArray())
        }

        val bootstrap = try {
            network.get("/api/mobile/bootstrap?profileKey=${state.offlineProfileKey}&workAreaKey=${state.workAreaKey}")
        } catch (e: NetworkException) {
            if (e.status == 404) {
                throw IllegalStateException("De server heeft nog geen mobiele offline API. Installeer eerst T-Stock Veren Server V10.5.1 TEST of nieuwer.")
            }
            throw e
        }
        store.saveBootstrap(bootstrap)
        bootstrap.optJSONObject("profile")?.let { profile ->
            val key = profile.optString("key", state.offlineProfileKey)
            val name = profile.optString("name", state.offlineProfileName)
            prefs.edit().putString("offline_profile_key", key).putString("offline_profile_name", name).apply()
            state = state.copy(offlineProfileKey = key, offlineProfileName = name)
        }
        registerDevice()

        stockRows = store.stock(stockSearch)
        locationRows = store.locations(locationSearch)
        mutationRows = store.mutationRows()
        refreshOfflineStats()
        state = state.copy(
            online = true,
            syncState = "Gesynchroniseerd",
            lastSync = bootstrap.optString("generatedAt", Instant.now().toString()),
            message = if (showMessage) {
                "Synchronisatie voltooid: ${store.locationCount()} locaties van ${state.workAreaName} offline beschikbaar."
            } else {
                state.message
            },
            error = "",
        )
    }

    fun checkForUpdates(silent: Boolean = false) = runTask(silent) {
        loadUpdateInfo(showCurrentMessage = !silent)
    }

    private suspend fun loadUpdateInfo(showCurrentMessage: Boolean) {
        val channel = BuildConfig.UPDATE_CHANNEL
        val data = network.get("/api/mobile/version?channel=$channel")
        val changes = data.optJSONArray("changelog") ?: JSONArray()
        val info = UpdateInfo(
            data.optInt("versionCode"),
            data.optString("versionName"),
            data.optInt("minimumVersionCode"),
            data.optBoolean("required"),
            data.optBoolean("apkAvailable"),
            data.optString("apkUrl").takeIf { it.isNotBlank() },
            (0 until changes.length()).map { changes.optString(it) },
        )
        state = state.copy(
            online = true,
            updateInfo = if (info.versionCode > BuildConfig.VERSION_CODE) info else null,
            message = if (showCurrentMessage && info.versionCode <= BuildConfig.VERSION_CODE) {
                "De app is bijgewerkt."
            } else {
                state.message
            },
        )
    }

    fun dismissUpdate() {
        if (state.updateInfo?.required != true) state = state.copy(updateInfo = null)
    }

    private fun runTask(silent: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!silent) state = state.copy(busy = true, error = "", message = "")
            try {
                block()
            } catch (e: Exception) {
                if (e is NetworkException) state = state.copy(online = false, syncState = "Synchronisatiefout")
                refreshOfflineStats()
                if (!silent) state = state.copy(error = e.message ?: "Onbekende fout.", message = "")
            } finally {
                if (!silent) state = state.copy(busy = false)
                refreshOfflineStats()
            }
        }
    }
}
