package nl.tstock.veren

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

private val Yellow = Color(0xFFFFD21A)
private val Orange = Color(0xFFFF9800)
private val Dark = Color(0xFF0B111C)
private val CardDark = Color(0xFF141C29)
private val Muted = Color(0xFF9CA8BA)
private val Green = Color(0xFF45D477)
private val Red = Color(0xFFFF6B6B)

class MainActivity : ComponentActivity() {
    private var vm: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startup = runCatching {
            ViewModelProvider(this)[MainViewModel::class.java]
        }
        vm = startup.getOrNull()

        val readyVm = vm
        if (readyVm == null) {
            showStartupRecovery(startup.exceptionOrNull())
            return
        }

        runCatching {
            setContent { TStockTheme { TStockApp(readyVm) } }
        }.onFailure { showStartupRecovery(it) }
    }

    override fun onResume() {
        super.onResume()
        vm?.onAppForeground()
    }

    /**
     * Native herstelscherm dat ook werkt wanneer Compose of de lokale database
     * nog niet gestart kan worden. Zo sluit de app niet zonder uitleg af.
     */
    private fun showStartupRecovery(error: Throwable?) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(AndroidColor.rgb(11, 17, 28))
        }
        layout.addView(TextView(this).apply {
            text = "T-Stock Veren TEST"
            textSize = 26f
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = "De lokale testgegevens konden niet worden geopend. Wis alleen de lokale testcache en start opnieuw. Gegevens op de Docker-server worden niet verwijderd.\n\nTechnische melding: ${error?.javaClass?.simpleName ?: "onbekend"}: ${error?.message ?: "geen details"}"
            textSize = 15f
            setTextColor(AndroidColor.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding)
        })
        layout.addView(Button(this).apply {
            text = "Lokale testcache herstellen"
            setOnClickListener {
                getSharedPreferences("tstock_settings", MODE_PRIVATE).edit().clear().commit()
                databaseList().filter { it.startsWith("tstock_veren") }.forEach(::deleteDatabase)
                recreate()
            }
        })
        setContentView(layout)
    }
}

@Composable
private fun TStockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Yellow, onPrimary = Color.Black, secondary = Orange,
            background = Dark, surface = CardDark, onSurface = Color.White,
            error = Red,
        ),
        typography = Typography(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        ), content = content
    )
}

@Composable
private fun TStockApp(vm: MainViewModel) {
    val state = vm.state
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::onScanResult)
    }
    fun launchScan(target: ScanTarget) {
        vm.requestScan(target)
        scanner.launch(
            ScanOptions().setPrompt("Scan artikel, bundel of locatie")
                .setBeepEnabled(true).setOrientationLocked(false).setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        )
    }

    val context = LocalContext.current
    state.updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text(if (update.required) "Update verplicht" else "Nieuwe app-versie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Versie ${update.versionName} staat op de T-Stock-server.")
                    update.changelog.forEach { Text("• $it", color = Muted) }
                    if (!update.apkAvailable) Text("De APK is nog niet op de server geplaatst.", color = Orange)
                }
            },
            confirmButton = {
                Button(enabled = update.apkAvailable && update.apkUrl != null, onClick = {
                    update.apkUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                }) { Text("Update downloaden") }
            },
            dismissButton = { if (!update.required) TextButton(onClick = vm::dismissUpdate) { Text("Later") } }
        )
    }

    if (state.user == null) {
        LoginScreen(vm)
    } else {
        NativeShell(vm, ::launchScan)
    }
}

@Composable
private fun LoginScreen(vm: MainViewModel) {
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var server by remember(vm.state.serverUrl) { mutableStateOf(vm.state.serverUrl) }
    Box(Modifier.fillMaxSize().background(Dark).statusBarsPadding().navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(20.dp).fillMaxWidth().widthIn(max = 500.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = "T-Stock Veren",
                    modifier = Modifier.size(82.dp),
                )
                Text(BuildConfig.APP_TITLE, style = MaterialTheme.typography.headlineMedium)
                Text("Voorraadbeheer", color = Muted)
                if (vm.state.serverUrl.isBlank()) {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Dark.copy(alpha=.55f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Eerste installatie", fontWeight = FontWeight.Bold)
                            Text("Stel eenmalig het serveradres in. Daarna is wijzigen alleen mogelijk via een beheerlogin.", color = Muted, fontSize = 12.sp)
                            OutlinedTextField(server, { server = it }, label = { Text("Serveradres") }, placeholder = { Text("http://192.168.2.126:8080") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(onClick = { vm.setServerUrl(server) }, modifier = Modifier.fillMaxWidth()) { Text("Server instellen") }
                        }
                    }
                }
                OutlinedTextField(username, { username = it }, label = { Text("Gebruikersnaam") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(secret, { secret = it }, label = { Text("Toegangscode") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                vm.state.error.takeIf { it.isNotBlank() }?.let { StatusBox(it, false) }
                vm.state.message.takeIf { it.isNotBlank() }?.let { StatusBox(it, true) }
                Button(
                    onClick = { vm.login(username, secret, secret.all { it.isDigit() }) },
                    enabled = !vm.state.busy && vm.state.serverUrl.isNotBlank() && username.isNotBlank() && secret.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (vm.state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Inloggen", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeShell(vm: MainViewModel, launchScan: (ScanTarget) -> Unit) {
    val state = vm.state
    BackHandler(enabled = state.screen != Screen.HOME) { vm.setScreen(Screen.HOME) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Dark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(BuildConfig.APP_TITLE, fontWeight = FontWeight.Black)
                        Text(
                            "${state.workAreaName} · " + if (state.online) "Online" else "Offline · ${state.pendingCount} in wachtrij",
                            fontSize = 12.sp,
                            color = if (state.online) Green else Orange,
                        )
                    }
                },
                navigationIcon = { if (state.screen != Screen.HOME) IconButton(onClick = { vm.setScreen(Screen.HOME) }) { Icon(Icons.Default.ArrowBack, "Terug") } },
                actions = {
                    IconButton(onClick = { vm.checkForUpdates() }) { Icon(Icons.Default.SystemUpdate, "Updates") }
                    IconButton(onClick = { vm.setScreen(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, "Instellingen") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Dark)
            )
        },
        bottomBar = {
            NavigationBar(Modifier.navigationBarsPadding(), containerColor = CardDark) {
                val destinations = listOf(
                    Triple(Screen.HOME, Icons.Default.Home, "Home"),
                    Triple(Screen.RECEIVE, Icons.Default.Inventory, "Inboeken"),
                    Triple(Screen.MOVE, Icons.Default.SwapHoriz, "Verplaatsen"),
                    Triple(Screen.ISSUE, Icons.Default.Output, "Uitboeken"),
                    Triple(Screen.SYNC, Icons.Default.Sync, "Sync"),
                )
                destinations.forEach { (screen, icon, label) ->
                    NavigationBarItem(selected = state.screen == screen, onClick = { vm.setScreen(screen) }, icon = { BadgedBox(badge = { if (screen == Screen.SYNC && state.pendingCount > 0) Badge { Text(state.pendingCount.toString()) } }) { Icon(icon, label) } }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.error.isNotBlank()) StatusBox(state.error, false, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            if (state.message.isNotBlank()) StatusBox(state.message, true, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            Box(Modifier.fillMaxSize()) {
                when (state.screen) {
                    Screen.HOME -> HomeScreen(vm)
                    Screen.RECEIVE -> ReceiveScreen(vm, launchScan)
                    Screen.MOVE -> MoveScreen(vm, launchScan)
                    Screen.ISSUE -> IssueScreen(vm, launchScan)
                    Screen.STOCK -> StockScreen(vm, launchScan)
                    Screen.LOCATIONS -> LocationsScreen(vm)
                    Screen.SYNC -> SyncScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
                if (state.busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.35f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Yellow) }
            }
        }
    }
}

@Composable
private fun WorkAreaSelector(vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.state.availableWorkAreas.forEach { area ->
            FilterChip(
                selected = vm.state.workAreaKey == area.key,
                onClick = { vm.setWorkArea(area) },
                label = { Column { Text(area.name); if (area.description.isNotBlank()) Text(area.description, fontSize = 11.sp, color = Muted) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val user = vm.state.user
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Goedendag, ${user?.displayName}", style = MaterialTheme.typography.headlineMedium)
        Text("Kies je werkgebied en daarna een magazijnactie. De app blijft bruikbaar zonder netwerk.", color = Muted)
        WorkAreaSelector(vm)
        HomeAction("Bundel inboeken", "Artikel → locatieadvies → locatiecontrole", Icons.Default.Inventory, Screen.RECEIVE, vm)
        HomeAction("Bundel verplaatsen", "Bundel en nieuwe locatie scannen", Icons.Default.SwapHoriz, Screen.MOVE, vm)
        HomeAction("Bundel uitboeken", "Voor productie of verbruik", Icons.Default.Output, Screen.ISSUE, vm)
        HomeAction("Voorraad zoeken", "Zoek lokaal op artikel, bundel of locatie", Icons.Default.Search, Screen.STOCK, vm)
        HomeAction("Locaties", "${vm.state.cachedLocationCount} locaties offline opgeslagen", Icons.Default.LocationOn, Screen.LOCATIONS, vm)
        HomeAction("Synchronisatie", "${vm.state.pendingCount} wachtend · ${vm.state.conflictCount + vm.state.failedCount} aandacht", Icons.Default.Sync, Screen.SYNC, vm)
        if (vm.state.cachedLocationCount == 0) {
            StatusBox("Nog geen locaties offline opgeslagen. Verbind met de server en voer één volledige synchronisatie uit.", false)
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (vm.state.online) Icons.Default.CloudDone else Icons.Default.CloudOff, null, tint = if (vm.state.online) Green else Orange)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (vm.state.online) "Server bereikbaar" else "Offline werken actief", fontWeight = FontWeight.Bold)
                    Text("${vm.state.cachedLocationCount} locaties · ${vm.state.cachedBundleCount} bundels offline", color = Muted, fontSize = 12.sp)
                    Text("Laatste sync: ${vm.state.lastSync}", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, screen: Screen, vm: MainViewModel) {
    ElevatedCard(Modifier.fillMaxWidth().clickable { vm.setScreen(screen) }, colors = CardDefaults.elevatedCardColors(containerColor = CardDark), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(Yellow.copy(alpha=.14f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Yellow) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(subtitle, color = Muted, fontSize = 13.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun ReceiveScreen(vm: MainViewModel, launchScan: (ScanTarget) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle("Bundel inboeken", "De originele V10-locatiecontrole in drie duidelijke stappen.")
            StepCard(1, "Scan artikel") {
                ScanField("Artikelnummer", vm.receiveArticle, { vm.receiveArticle = it }, { launchScan(ScanTarget.ARTICLE) }, "SG1234R-2500")
                OutlinedTextField(vm.receiveContainer, { vm.receiveContainer = it }, label = { Text("Containercode (optioneel)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vm.receiveBundle, { vm.receiveBundle = it }, label = { Text("Bundelcode (optioneel)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(vm.receiveQuantity, { vm.receiveQuantity = it.filter(Char::isDigit) }, label = { Text("Aantal (leeg = standaard)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(vm.receiveReason, { vm.receiveReason = it }, label = { Text("Reden correctie") }, modifier = Modifier.weight(1f))
                }
                Button(onClick = vm::suggestLocation, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.LocationSearching, null); Spacer(Modifier.width(8.dp)); Text("Zoek vrije locatie") }
            }
            StepCard(2, "Locatieadvies") {
                if (vm.receiveSuggestedCode.isBlank()) Text("Nog geen locatieadvies.", color = Muted)
                else { Text(vm.receiveSuggestedName, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Yellow); Text(vm.receiveSuggestedCode, color = Muted) }
            }
            StepCard(3, "Scan locatie ter controle") {
                ScanField("Locatiecode", vm.receiveLocationScan, { vm.receiveLocationScan = it.uppercase() }, { launchScan(ScanTarget.RECEIVE_LOCATION) }, "Scan stellinglabel")
                if (vm.receiveLocationScan.isNotBlank() && vm.receiveSuggestedCode.isNotBlank()) {
                    val correct = vm.receiveLocationIsCorrect()
                    StatusBox(if (correct) "Locatie is correct." else "Verkeerde locatie. Verwacht ${vm.receiveSuggestedCode}.", correct)
                }
            }
        }
        Surface(shadowElevation = 10.dp, color = CardDark) { Button(onClick = vm::receiveBundle, enabled = vm.receiveArticle.isNotBlank() && vm.receiveSuggestedCode.isNotBlank() && vm.receiveLocationScan.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(16.dp).height(58.dp)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(if (vm.state.online) "Bundel inboeken" else "Offline opslaan", fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun MoveScreen(vm: MainViewModel, launchScan: (ScanTarget) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle("Bundel verplaatsen", "Scan de bundel of huidige locatie en daarna de nieuwe vrije locatie.")
            StepCard(1, "Zoek bundel") { ScanField("Bundel / artikel / locatie", vm.findScan, { vm.findScan = it }, { launchScan(ScanTarget.FIND_BUNDLE) }, "Scan code"); Button(onClick = vm::findBundle, modifier = Modifier.fillMaxWidth()) { Text("Zoeken") } }
            vm.selectedBundle?.let { BundleCard(it) }
            StepCard(2, "Nieuwe locatie") { ScanField("Nieuwe locatie", vm.moveLocationScan, { vm.moveLocationScan = it.uppercase() }, { launchScan(ScanTarget.MOVE_LOCATION) }, "Scan locatiecode"); OutlinedTextField(vm.moveReason, { vm.moveReason = it }, label = { Text("Reden") }, modifier = Modifier.fillMaxWidth()) }
        }
        Surface(shadowElevation = 10.dp, color = CardDark) { Button(onClick = vm::moveBundle, enabled = vm.selectedBundle != null && vm.moveLocationScan.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(16.dp).height(58.dp)) { Text(if (vm.state.online) "Verplaatsen" else "Offline verplaatsing opslaan", fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun IssueScreen(vm: MainViewModel, launchScan: (ScanTarget) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle("Bundel uitboeken", "Standaard wordt de complete bundel uitgeboekt, gelijk aan V10.")
            StepCard(1, "Scan bundel") { ScanField("Bundel / artikel / locatie", vm.findScan, { vm.findScan = it }, { launchScan(ScanTarget.FIND_BUNDLE) }, "Scan code"); Button(onClick = vm::findBundle, modifier = Modifier.fillMaxWidth()) { Text("Zoeken") } }
            vm.selectedBundle?.let { BundleCard(it) }
            StepCard(2, "Uitgifte") {
                OutlinedTextField(vm.issueQuantity, { vm.issueQuantity = it.filter(Char::isDigit) }, label = { Text("Aantal (leeg = complete bundel)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vm.issueReason, { vm.issueReason = it }, label = { Text("Reden / doel") }, modifier = Modifier.fillMaxWidth())
            }
        }
        Surface(shadowElevation = 10.dp, color = CardDark) { Button(onClick = vm::issueBundle, enabled = vm.selectedBundle != null, modifier = Modifier.fillMaxWidth().padding(16.dp).height(58.dp)) { Text(if (vm.state.online) "Uitboeken" else "Offline uitboeking opslaan", fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun StockScreen(vm: MainViewModel, launchScan: (ScanTarget) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("Voorraad zoeken", "De laatst gesynchroniseerde voorraad is ook offline beschikbaar.")
        ScanField("Zoeken", vm.stockSearch, { vm.stockSearch = it; vm.refreshStock() }, { launchScan(ScanTarget.STOCK_SEARCH) }, "Artikel, bundel of locatie")
        Spacer(Modifier.height(10.dp))
        Text("${vm.stockRows.size} bundels", color = Muted)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(vm.stockRows, key = { it.optString("bundle_code") }) { BundleCard(it) }
        }
    }
}

@Composable
private fun LocationsScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("Locaties", "Deze locaties zijn lokaal opgeslagen en blijven zonder netwerk beschikbaar.")
        OutlinedTextField(
            value = vm.locationSearch,
            onValueChange = { vm.locationSearch = it; vm.refreshLocations() },
            label = { Text("Zoek locatie") },
            placeholder = { Text("Code, naam, bundel of artikel") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        Text("${vm.locationRows.size} van ${vm.state.cachedLocationCount} locaties", color = Muted)
        if (vm.state.cachedLocationCount == 0) {
            StatusBox("Geen offline locaties aanwezig. Open Synchronisatie terwijl de server bereikbaar is.", false, Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                items(vm.locationRows, key = { it.optString("code") }) { location ->
                    LocationCard(location)
                }
            }
        }
    }
}

@Composable
private fun LocationCard(location: JSONObject) {
    val occupied = !location.isNull("occupied_bundle_id") || location.optString("occupied_bundle_code").isNotBlank()
    val blocked = location.optBoolean("blocked", false) || !location.optBoolean("enabled", true)
    val statusText = when {
        blocked -> "Geblokkeerd"
        occupied -> "Bezet"
        else -> "Vrij"
    }
    val statusColor = when {
        blocked -> Red
        occupied -> Orange
        else -> Green
    }
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = CardDark)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(location.optString("display_name", location.optString("code")), fontWeight = FontWeight.Black, color = Yellow)
                Text(statusText, color = statusColor, fontWeight = FontWeight.Bold)
            }
            Text("Code: ${location.optString("code")}", color = Muted)
            location.optString("occupied_bundle_code").takeIf { it.isNotBlank() }?.let { Text("Bundel: $it") }
            location.optString("occupied_article_number").takeIf { it.isNotBlank() }?.let { Text("Artikel: $it", color = Muted) }
            val min = if (location.isNull("min_length_mm")) "-" else location.optInt("min_length_mm").toString()
            val max = if (location.isNull("max_length_mm")) "-" else location.optInt("max_length_mm").toString()
            Text("Lengte $min–$max mm · Kant ${location.optString("allowed_side", "BOTH")}", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SyncScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            ScreenTitle("Synchronisatie", "Mutaties blijven lokaal bewaard totdat de server ze heeft verwerkt.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::syncAll, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Nu synchroniseren") }
                OutlinedButton(onClick = vm::refreshQueue) { Icon(Icons.Default.Refresh, null) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Status: ${vm.state.syncState}", color = if (vm.state.online) Green else Orange, fontWeight = FontWeight.Bold)
            Text("${vm.state.pendingCount} openstaande mutaties · ${vm.state.conflictCount} conflicten · ${vm.state.failedCount} mislukt", fontWeight = FontWeight.Bold)
            Text("${vm.state.workAreaName}: ${vm.state.cachedLocationCount} locaties en ${vm.state.cachedBundleCount} bundels offline", color = Muted, fontSize = 12.sp)
            Text("Laatste synchronisatie: ${vm.state.lastSync}", color = Muted, fontSize = 12.sp)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(vm.mutationRows, key = { it.optString("uuid") }) { row ->
                    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(row.optString("type"), fontWeight = FontWeight.Bold); Text(row.optString("status"), color = when(row.optString("status")) { "CONFLICT","FAILED" -> Red; else -> Orange }) }; Text(row.optJSONObject("payload")?.optString("articleNumber", row.optJSONObject("payload")?.optString("bundleCode", "")) ?: "", color = Muted); row.optString("error").takeIf { it.isNotBlank() }?.let { Text(it, color = Red, fontSize = 12.sp) } } }
                }
            }
        }
        if (vm.mutationRows.any { it.optString("status") == "CANCELLED" }) TextButton(onClick = vm::clearCancelledMutations, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Geannuleerde regels opruimen", color = Muted) }
        if (vm.state.conflictCount + vm.state.failedCount > 0) Text("Conflicten blijven bewaard. Laat een Admin ze beoordelen in de webapp onder Offline mutaties.", color = Orange, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    var server by remember(vm.state.serverUrl) { mutableStateOf(vm.state.serverUrl) }
    var adminUser by remember { mutableStateOf("") }
    var adminSecret by remember { mutableStateOf("") }
    var adminPinMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Instellingen", "Werkgebied, offline opslag, synchronisatie en beveiligd beheer.")

        ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Werkgebied", fontWeight = FontWeight.Bold)
            WorkAreaSelector(vm)
            Text("Alle locaties, voorraad en locatieadviezen worden strikt op dit werkgebied gefilterd.", color = Muted, fontSize = 12.sp)
        } }

        ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Offline opslag", fontWeight = FontWeight.Bold)
            Text("Actief profiel: ${vm.state.offlineProfileName}", color = Yellow, fontWeight = FontWeight.Bold)
            vm.state.availableProfiles.forEach { profile ->
                FilterChip(
                    selected = vm.state.offlineProfileKey == profile.key,
                    onClick = { vm.setOfflineProfile(profile) },
                    label = { Column { Text(profile.name); if (profile.description.isNotBlank()) Text(profile.description, fontSize = 11.sp, color = Muted) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("${vm.state.cachedLocationCount} locaties, ${vm.state.cachedBundleCount} bundels en ${vm.state.pendingCount} openstaande mutaties", color = Muted)
            Text("Laatste synchronisatie: ${vm.state.lastSync}", color = Muted, fontSize = 12.sp)
            if (vm.state.cachedLocationCount == 0) Text("Synchroniseer dit profiel minimaal één keer online voordat je offline gaat werken.", color = Orange)
            OutlinedButton(onClick = vm::rebuildOfflineCache, enabled = vm.state.online && !vm.state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Offline cache veilig vernieuwen")
            }
        } }

        ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Serverinstellingen", fontWeight = FontWeight.Bold)
            if (!vm.state.serverSettingsUnlocked) {
                Text("Het serveradres is beveiligd. Alleen een gebruiker met serverbeheerrechten kan dit wijzigen.", color = Muted)
                OutlinedTextField(adminUser, { adminUser = it }, label = { Text("Beheerder") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = adminPinMode, onCheckedChange = { adminPinMode = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (adminPinMode) "Inloggen met pincode" else "Inloggen met wachtwoord", color = Muted)
                }
                OutlinedTextField(adminSecret, { adminSecret = it }, label = { Text(if (adminPinMode) "Pincode" else "Wachtwoord") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { vm.unlockServerSettings(adminUser, adminSecret, adminPinMode) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("Ontgrendelen")
                }
            } else {
                StatusBox("Serverinstellingen tijdelijk ontgrendeld.", true)
                OutlinedTextField(server, { server = it }, label = { Text("Serveradres") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { vm.setServerUrl(server) }, modifier = Modifier.fillMaxWidth()) { Text("Server opslaan en testen") }
                TextButton(onClick = vm::lockServerSettings, modifier = Modifier.fillMaxWidth()) { Text("Nu vergrendelen") }
                HorizontalDivider()
                Text("Systeeminformatie", fontWeight = FontWeight.Bold)
                Text("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Muted)
            }
        } }

        ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Updates", fontWeight = FontWeight.Bold)
            Text("Updatekanaal: ${BuildConfig.UPDATE_CHANNEL}", color = Muted)
            Button(onClick = { vm.checkForUpdates() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("Controleer op updates") }
        } }
        OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Uitloggen") }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) { Column(Modifier.padding(bottom = 4.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium); Text(subtitle, color = Muted) } }

@Composable
private fun StepCard(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = CardDark), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(32.dp).background(Yellow, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(number.toString(), color = Color.Black, fontWeight = FontWeight.Black) }; Spacer(Modifier.width(10.dp)); Text(title, style = MaterialTheme.typography.titleLarge) }; content() }
    }
}

@Composable
private fun ScanField(label: String, value: String, onValue: (String) -> Unit, onScan: () -> Unit, placeholder: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onValue, label = { Text(label) }, placeholder = { Text(placeholder) }, modifier = Modifier.weight(1f), singleLine = true)
        FilledIconButton(onClick = onScan, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.QrCodeScanner, "Scannen") }
    }
}

@Composable
private fun BundleCard(bundle: JSONObject) {
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = CardDark)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(bundle.optString("article_number"), fontWeight = FontWeight.Black, color = Yellow); Text("${bundle.optInt("quantity_current")} st.", fontWeight = FontWeight.Bold) }
            Text("Bundel: ${bundle.optString("bundle_code")}", color = Muted)
            Text("Locatie: ${bundle.optString("location_display_name", bundle.optString("location_code","-"))}")
            Text("Type ${bundle.optString("spring_type_code")} · ${bundle.optInt("length_mm")} mm · ${bundle.optString("side")}", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusBox(text: String, success: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = (if (success) Green else Red).copy(alpha = .15f), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (success) Green.copy(alpha=.5f) else Red.copy(alpha=.5f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (success) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (success) Green else Red); Spacer(Modifier.width(8.dp)); Text(text, color = if (success) Green else Red, fontWeight = FontWeight.SemiBold) }
    }
}
