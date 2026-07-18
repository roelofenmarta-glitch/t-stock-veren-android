package nl.tstock.veren

enum class Screen { HOME, RECEIVE, MOVE, ISSUE, STOCK, SYNC, SETTINGS }
enum class ScanTarget { ARTICLE, RECEIVE_LOCATION, FIND_BUNDLE, MOVE_LOCATION, STOCK_SEARCH }

data class UserSession(
    val id: Long,
    val username: String,
    val displayName: String,
    val roleName: String,
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
    val lastSync: String = "Nog niet gesynchroniseerd",
    val updateInfo: UpdateInfo? = null,
    val scanTarget: ScanTarget? = null,
)
