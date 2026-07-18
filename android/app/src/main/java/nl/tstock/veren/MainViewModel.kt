package nl.tstock.veren

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tstock_settings", Context.MODE_PRIVATE)
    val store = LocalStore(application)
    private val deviceUuid = prefs.getString("device_uuid", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_uuid", it).apply() }
    private val network = NetworkClient { state.serverUrl }

    var state by mutableStateOf(
        AppState(
            serverUrl = prefs.getString("server_url", "") ?: "",
            user = loadUser(),
            pendingCount = store.pendingCount(),
            lastSync = store.getMeta("last_sync", "Nog niet gesynchroniseerd"),
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
    var mutationRows by mutableStateOf(store.mutationRows())

    init {
        if (state.serverUrl.isNotBlank()) {
            checkConnection()
            checkForUpdates(silent = true)
        }
    }

    private fun loadUser(): UserSession? {
        val raw = prefs.getString("user", null) ?: return null
        return try {
            val json = JSONObject(raw)
            UserSession(json.getLong("id"), json.getString("username"), json.getString("displayName"), json.getString("roleName"))
        } catch (_: Exception) { null }
    }
    private fun saveUser(user: UserSession?) {
        if (user == null) prefs.edit().remove("user").apply()
        else prefs.edit().putString("user", JSONObject().put("id", user.id).put("username", user.username).put("displayName", user.displayName).put("roleName", user.roleName).toString()).apply()
    }

    fun setScreen(screen: Screen) {
        state = state.copy(screen = screen, message = "", error = "")
        if (screen == Screen.STOCK) refreshStock()
        if (screen == Screen.SYNC) refreshQueue()
    }
    fun setServerUrl(value: String) {
        val clean = value.trim().trimEnd('/')
        prefs.edit().putString("server_url", clean).apply()
        state = state.copy(serverUrl = clean, message = "Serveradres opgeslagen.", error = "")
        checkConnection()
    }
    fun logout() { saveUser(null); state = state.copy(user = null, screen = Screen.HOME, message = "", error = "") }
    fun clearMessage() { state = state.copy(message = "", error = "") }

    fun login(username: String, secret: String, pinMode: Boolean) = runTask {
        val body = JSONObject().put("username", username).put(if (pinMode) "pin" else "password", secret)
        val result = network.post(if (pinMode) "/api/auth/login-pin" else "/api/auth/login-password", body)
        val u = result.getJSONObject("user")
        val user = UserSession(u.getLong("id"), u.getString("username"), u.getString("display_name"), u.getString("role_name"))
        saveUser(user)
        state = state.copy(user = user, online = true, message = "Welkom ${user.displayName}.")
        registerDevice()
        syncAll()
    }

    fun checkConnection() = runTask(silent = true) {
        network.get("/api/health")
        state = state.copy(online = true)
    }

    private suspend fun registerDevice() {
        val user = state.user ?: return
        try {
            network.post("/api/mobile/device/register", JSONObject()
                .put("deviceUuid", deviceUuid).put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("appVersionCode", BuildConfig.VERSION_CODE).put("appVersionName", BuildConfig.VERSION_NAME).put("userId", user.id))
        } catch (_: Exception) {}
    }

    fun requestScan(target: ScanTarget) { state = state.copy(scanTarget = target) }
    fun scanLaunched() { state = state.copy(scanTarget = null) }
    fun onScanResult(value: String) {
        when (state.scanTarget) {
            ScanTarget.ARTICLE -> { receiveArticle = value; receiveSuggestedCode = ""; receiveSuggestedName = ""; receiveLocationScan = "" }
            ScanTarget.RECEIVE_LOCATION -> receiveLocationScan = value.trim().uppercase()
            ScanTarget.FIND_BUNDLE -> { findScan = value; findBundle() }
            ScanTarget.MOVE_LOCATION -> moveLocationScan = value.trim().uppercase()
            ScanTarget.STOCK_SEARCH -> { stockSearch = value; refreshStock() }
            null -> Unit
        }
        state = state.copy(scanTarget = null)
    }

    fun suggestLocation() = runTask {
        if (receiveArticle.isBlank()) throw IllegalArgumentException("Scan eerst het artikel.")
        val local = try {
            if (state.online) {
                val data = network.post("/api/mobile/suggest-location", JSONObject().put("articleNumber", receiveArticle))
                data.getJSONObject("suggestedLocation")
            } else store.suggestLocation(receiveArticle)
        } catch (e: Exception) {
            state = state.copy(online = false)
            store.suggestLocation(receiveArticle)
        }
        receiveSuggestedCode = local.optString("code")
        receiveSuggestedName = local.optString("display_name", receiveSuggestedCode)
        state = state.copy(message = "Vrije locatie: $receiveSuggestedName")
    }

    fun receiveBundle() = runTask {
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        val qty = receiveQuantity.toIntOrNull()
        val code = store.receiveOffline(receiveArticle, receiveSuggestedCode, receiveLocationScan, receiveContainer, receiveBundle, qty, receiveReason, user.id, deviceUuid)
        state = state.copy(message = "Bundel $code lokaal opgeslagen${if (state.online) "; synchroniseren..." else "."}", pendingCount = store.pendingCount())
        resetReceive()
        if (state.online) syncAllInternal()
    }
    private fun resetReceive() {
        receiveArticle = ""; receiveContainer = ""; receiveBundle = ""; receiveQuantity = ""; receiveReason = ""
        receiveSuggestedCode = ""; receiveSuggestedName = ""; receiveLocationScan = ""
    }

    fun findBundle() {
        try {
            val rows = store.findBundles(findScan)
            selectedBundle = rows.firstOrNull()
            state = if (selectedBundle == null) state.copy(error = "Geen actieve bundel gevonden in lokale gegevens.", message = "")
            else state.copy(message = "Bundel ${selectedBundle!!.optString("bundle_code")} gevonden.", error = "")
        } catch (e: Exception) { state = state.copy(error = e.message ?: "Zoeken mislukt.", message = "") }
    }

    fun moveBundle() = runTask {
        val bundle = selectedBundle ?: throw IllegalArgumentException("Scan en selecteer eerst een bundel.")
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        store.moveOffline(bundle, moveLocationScan, moveReason, user.id)
        state = state.copy(message = "Verplaatsing lokaal opgeslagen.", pendingCount = store.pendingCount())
        selectedBundle = null; findScan = ""; moveLocationScan = ""
        if (state.online) syncAllInternal()
    }

    fun issueBundle() = runTask {
        val bundle = selectedBundle ?: throw IllegalArgumentException("Scan en selecteer eerst een bundel.")
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        store.issueOffline(bundle, issueQuantity.toIntOrNull(), issueReason, user.id)
        state = state.copy(message = "Uitboeking lokaal opgeslagen.", pendingCount = store.pendingCount())
        selectedBundle = null; findScan = ""; issueQuantity = ""
        if (state.online) syncAllInternal()
    }

    fun refreshStock() { stockRows = store.stock(stockSearch) }
    fun refreshQueue() { mutationRows = store.mutationRows(); state = state.copy(pendingCount = store.pendingCount(), lastSync = store.getMeta("last_sync", state.lastSync)) }
    fun clearFailedMutations() { store.clearFailed(); refreshQueue(); state = state.copy(message = "Mislukte mutaties verwijderd.") }

    fun syncAll() = runTask { syncAllInternal() }
    private suspend fun syncAllInternal() {
        val user = state.user ?: throw IllegalStateException("Log opnieuw in.")
        val pending = store.pendingJson()
        if (pending.length() > 0) {
            val response = network.post("/api/mobile/sync", JSONObject().put("deviceUuid", deviceUuid).put("userId", user.id).put("mutations", pending))
            store.applySyncResults(response.optJSONArray("results") ?: JSONArray())
        }
        val bootstrap = network.get("/api/mobile/bootstrap")
        store.saveBootstrap(bootstrap)
        registerDevice()
        stockRows = store.stock(stockSearch)
        mutationRows = store.mutationRows()
        state = state.copy(online = true, pendingCount = store.pendingCount(), lastSync = bootstrap.optString("generatedAt", Instant.now().toString()), message = "Synchronisatie voltooid.")
    }

    fun checkForUpdates(silent: Boolean = false) = runTask(silent) {
        val data = network.get("/api/mobile/version")
        val changes = data.optJSONArray("changelog") ?: JSONArray()
        val info = UpdateInfo(
            data.optInt("versionCode"), data.optString("versionName"), data.optInt("minimumVersionCode"),
            data.optBoolean("required"), data.optBoolean("apkAvailable"), data.optString("apkUrl").takeIf { it.isNotBlank() },
            (0 until changes.length()).map { changes.optString(it) }
        )
        state = state.copy(online = true, updateInfo = if (info.versionCode > BuildConfig.VERSION_CODE) info else null,
            message = if (!silent && info.versionCode <= BuildConfig.VERSION_CODE) "De app is bijgewerkt." else state.message)
    }
    fun dismissUpdate() { if (state.updateInfo?.required != true) state = state.copy(updateInfo = null) }

    private fun runTask(silent: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!silent) state = state.copy(busy = true, error = "", message = "")
            try { block() }
            catch (e: Exception) {
                if (e is NetworkException) state = state.copy(online = false)
                if (!silent) state = state.copy(error = e.message ?: "Onbekende fout.", message = "")
            } finally { if (!silent) state = state.copy(busy = false, pendingCount = store.pendingCount()) }
        }
    }
}
