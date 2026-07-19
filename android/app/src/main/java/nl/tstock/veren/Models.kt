package nl.tstock.veren

enum class Screen { HOME, RECEIVE, MOVE, ISSUE, STOCK, LOCATIONS, SYNC, SETTINGS }
enum class ScanTarget { ARTICLE, RECEIVE_LOCATION, FIND_BUNDLE, MOVE_LOCATION, STOCK_SEARCH }

data class UserSession(
    val id: Long,
    val username: String,
    val displayName: String,
    val roleName: String,
)

data class WorkArea(
    val key: String,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
)

data class OfflineProfile(
    val key: String,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minimumVersionCode: Int,
    val required: Boolean,
    val apkAvailable: Boolean,
    val apkUrl: String?,
    val changelog: List<String>,
)

data class AppState(
    val screen: Screen = Screen.HOME,
    val user: UserSession? = null,
    val serverUrl: String = "",
    val online: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val failedCount: Int = 0,
    val syncState: String = "Gereed",
    val cachedLocationCount: Int = 0,
    val cachedBundleCount: Int = 0,
    val lastSync: String = "Nog niet gesynchroniseerd",
    val updateInfo: UpdateInfo? = null,
    val scanTarget: ScanTarget? = null,
    val workAreaKey: String = "hoofdlocatie",
    val workAreaName: String = "Hoofdlocatie",
    val availableWorkAreas: List<WorkArea> = listOf(WorkArea("hoofdlocatie", "Hoofdlocatie", isDefault = true), WorkArea("paganelstraat", "Paganelstraat")),
    val offlineProfileKey: String = "paganelstraat",
    val offlineProfileName: String = "Paganelstraat",
    val availableProfiles: List<OfflineProfile> = listOf(OfflineProfile("paganelstraat", "Paganelstraat", isDefault = true)),
    val serverSettingsUnlocked: Boolean = false,
)
