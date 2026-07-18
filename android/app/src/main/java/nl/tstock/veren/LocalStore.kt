package nl.tstock.veren

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class LocalStore(context: Context) : SQLiteOpenHelper(
    context,
    if (BuildConfig.IS_TEST_BUILD) "tstock_veren_v102_test.db" else "tstock_veren_v102.db",
    null,
    1,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE spring_types(code TEXT PRIMARY KEY,json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE standard_lengths(id INTEGER PRIMARY KEY AUTOINCREMENT,type_code TEXT,length_mm INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE locations(code TEXT PRIMARY KEY,json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bundles(bundle_code TEXT PRIMARY KEY,json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE mutations(uuid TEXT PRIMARY KEY,type TEXT NOT NULL,payload TEXT NOT NULL,status TEXT NOT NULL,created_at TEXT NOT NULL,error TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun setMeta(key: String, value: String) {
        writableDatabase.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun getMeta(key: String, fallback: String = ""): String {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use { return if (it.moveToFirst()) it.getString(0) else fallback }
    }

    private fun countInDb(db: SQLiteDatabase, table: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun locationCount(): Int = countInDb(readableDatabase, "locations")
    fun bundleCount(): Int = countInDb(readableDatabase, "bundles")
    fun hasOfflineLocations(): Boolean = locationCount() > 0

    /**
     * Slaat de complete offline dataset atomair op.
     *
     * Belangrijk: een lege of ongeldige serverrespons mag de bestaande locatiecache
     * nooit wissen. Daardoor blijven eerder gesynchroniseerde locaties bruikbaar
     * wanneer de server tijdelijk niet goed reageert of nog niet naar V10.2 is bijgewerkt.
     */
    fun saveBootstrap(data: JSONObject) {
        if (!data.has("locations")) {
            throw IllegalStateException("De server leverde geen locaties. Werk de T-Stock-server bij en synchroniseer opnieuw.")
        }
        val locations = data.optJSONArray("locations")
            ?: throw IllegalStateException("De locatiegegevens van de server zijn ongeldig.")
        if (locations.length() == 0) {
            throw IllegalStateException("De server leverde 0 locaties. De bestaande offline locatiecache is behouden.")
        }

        val springTypes = data.optJSONArray("springTypes")
            ?: throw IllegalStateException("De server leverde geen veertypen voor offline gebruik.")
        val standardLengths = data.optJSONArray("standardLengths") ?: JSONArray()
        val bundles = data.optJSONArray("bundles") ?: JSONArray()

        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("spring_types", null, null)
            db.delete("standard_lengths", null, null)
            db.delete("locations", null, null)
            db.delete("bundles", null, null)

            putArray(db, "spring_types", "code", "spring_type_code", springTypes)
            for (i in 0 until standardLengths.length()) {
                val row = standardLengths.getJSONObject(i)
                db.insert("standard_lengths", null, ContentValues().apply {
                    put("type_code", if (row.isNull("spring_type_code")) null as String? else row.optString("spring_type_code"))
                    put("length_mm", row.optInt("length_mm"))
                })
            }
            putArray(db, "locations", "code", "code", locations)
            putArray(db, "bundles", "bundle_code", "bundle_code", bundles)

            val insertedLocations = countInDb(db, "locations")
            if (insertedLocations == 0) {
                throw IllegalStateException("Er konden geen locaties lokaal worden opgeslagen.")
            }

            setMetaInDb(db, "last_sync", data.optString("generatedAt", Instant.now().toString()))
            setMetaInDb(db, "settings", (data.optJSONObject("settings") ?: JSONObject()).toString())
            setMetaInDb(db, "cached_location_count", insertedLocations.toString())
            setMetaInDb(db, "cached_bundle_count", countInDb(db, "bundles").toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun putArray(db: SQLiteDatabase, table: String, keyColumn: String, jsonKey: String, rows: JSONArray) {
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val key = row.optString(jsonKey)
            if (key.isBlank()) continue
            db.insertWithOnConflict(table, null, ContentValues().apply { put(keyColumn, key); put("json", row.toString()) }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
    private fun setMetaInDb(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    data class Article(val articleNumber: String, val springType: String, val side: String, val lengthMm: Int, val raw: String)
    fun parseArticle(scan: String): Article {
        val normalized = scan.trim().uppercase().replace(Regex("\\s+"), " ")
        val match = Regex("\\b(SG([0-9]{4})([RLX])-([0-9]{4}))\\b", RegexOption.IGNORE_CASE).find(normalized)
            ?: throw IllegalArgumentException("Ongeldige artikelcode. Verwacht SGxxxxR-xxxx, SGxxxxL-xxxx of SGxxxxX-xxxx.")
        return Article(match.groupValues[1].uppercase(), match.groupValues[2], match.groupValues[3].uppercase(), match.groupValues[4].toInt(), normalized)
    }

    fun suggestLocation(articleScan: String): JSONObject {
        if (!hasOfflineLocations()) {
            throw IllegalStateException("Er zijn nog geen locaties offline opgeslagen. Maak verbinding met de server en kies Nu synchroniseren.")
        }
        val article = parseArticle(articleScan)
        validateStandardLength(article)
        val candidates = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT json FROM locations", null).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject(cursor.getString(0))
                if (!row.optBoolean("enabled", true) || row.optBoolean("blocked", false)) continue
                if (!row.isNull("occupied_bundle_id") || row.optString("occupied_bundle_code").isNotBlank()) continue
                if (!row.isNull("min_length_mm") && article.lengthMm < row.optInt("min_length_mm")) continue
                if (!row.isNull("max_length_mm") && article.lengthMm > row.optInt("max_length_mm")) continue
                val side = row.optString("allowed_side", "BOTH")
                if (side != "BOTH" && side != article.side) continue
                val allowed = row.optJSONArray("allowed_spring_types") ?: JSONArray()
                if (allowed.length() > 0 && (0 until allowed.length()).none { allowed.optString(it) == article.springType }) continue
                candidates += row
            }
        }
        if (candidates.isEmpty()) throw IllegalStateException("Geen geschikte vrije locatie in de lokale gegevens.")
        return candidates.sortedWith(compareBy<JSONObject>(
            { if (it.optBoolean("fast_mover_preferred", false)) 0 else 1 },
            { when (it.optString("distance_to_infeed", "NORMAL")) { "DICHTBIJ" -> 0; "VER" -> 2; else -> 1 } },
            { it.optInt("priority", 100) }, { it.optString("zone") }, { it.optInt("rack") },
            { it.optInt("column_nr") }, { it.optInt("row_nr") }, { it.optInt("position_nr") }
        )).first()
    }

    private fun validateStandardLength(article: Article) {
        val configured = mutableListOf<Int>()
        readableDatabase.rawQuery("SELECT length_mm FROM standard_lengths WHERE type_code IS NULL OR type_code=?", arrayOf(article.springType)).use {
            while (it.moveToNext()) configured += it.getInt(0)
        }
        if (configured.isNotEmpty() && article.lengthMm !in configured) throw IllegalArgumentException("Lengte ${article.lengthMm} mm is niet toegestaan. Toegestaan: ${configured.sorted().joinToString()}.")
    }

    fun getLocation(code: String): JSONObject? {
        val scan = code.trim().uppercase()
        readableDatabase.rawQuery("SELECT json FROM locations", null).use { c ->
            while (c.moveToNext()) {
                val row = JSONObject(c.getString(0))
                if (row.optString("code").uppercase() == scan || row.optString("display_name").uppercase() == scan || row.optString("old_location_code").uppercase() == scan) return row
            }
        }
        return null
    }

    private fun validateLocation(
        location: JSONObject,
        article: Article,
        requireFree: Boolean = true,
        serverConfirmedFree: Boolean = false,
    ) {
        if (!location.optBoolean("enabled", true)) throw IllegalStateException("Locatie is uitgeschakeld.")
        if (location.optBoolean("blocked", false)) throw IllegalStateException("Locatie is geblokkeerd.")
        if (requireFree && !serverConfirmedFree && (!location.isNull("occupied_bundle_id") || location.optString("occupied_bundle_code").isNotBlank())) {
            throw IllegalStateException("Locatie is al bezet in de lokale cache. Synchroniseer opnieuw of gebruik de testcorrectie met servercontrole.")
        }
        if (!location.isNull("min_length_mm") && article.lengthMm < location.optInt("min_length_mm")) throw IllegalStateException("Veer is te kort voor deze locatie.")
        if (!location.isNull("max_length_mm") && article.lengthMm > location.optInt("max_length_mm")) throw IllegalStateException("Veer is te lang voor deze locatie.")
        val side = location.optString("allowed_side", "BOTH")
        if (side != "BOTH" && side != article.side) throw IllegalStateException("Kant ${article.side} is niet toegestaan op deze locatie.")
        val allowed = location.optJSONArray("allowed_spring_types") ?: JSONArray()
        if (allowed.length() > 0 && (0 until allowed.length()).none { allowed.optString(it) == article.springType }) throw IllegalStateException("Veertype ${article.springType} is niet toegestaan op deze locatie.")
    }

    private fun bundleSize(type: String): Int {
        readableDatabase.rawQuery("SELECT json FROM spring_types WHERE code=?", arrayOf(type)).use {
            if (it.moveToFirst()) { val row = JSONObject(it.getString(0)); return row.optInt("bundle_size", row.optInt("quantity_per_bundle", 1)) }
        }
        throw IllegalStateException("Veertype $type staat niet in de lokale gegevens.")
    }

    fun receiveOffline(
        articleScan: String,
        suggestedCode: String,
        scannedCode: String,
        containerCode: String,
        bundleCodeInput: String,
        quantityOverride: Int?,
        correctionReason: String,
        userId: Long,
        deviceUuid: String,
        serverConfirmedFree: Boolean = false,
    ): String {
        val article = parseArticle(articleScan)
        val suggested = suggestedCode.trim().uppercase()
        val scanned = scannedCode.trim().uppercase()
        if (scanned.isBlank()) throw IllegalArgumentException("Scan de locatie.")
        if (suggested.isNotBlank() && scanned != suggested) throw IllegalArgumentException("Verkeerde locatie. Verwacht $suggested, gescand $scanned.")
        val location = getLocation(scanned) ?: throw IllegalArgumentException("Locatie niet gevonden in de lokale gegevens.")
        validateLocation(location, article, true, serverConfirmedFree)
        val defaultQuantity = bundleSize(article.springType)
        val quantity = quantityOverride ?: defaultQuantity
        if (quantity <= 0) throw IllegalArgumentException("Aantal moet groter zijn dan 0.")
        if (quantity != defaultQuantity && correctionReason.isBlank()) throw IllegalArgumentException("Reden is verplicht bij afwijkend aantal.")
        val bundleCode = bundleCodeInput.trim().uppercase().ifBlank { "MOB-${deviceUuid.takeLast(6)}-${System.currentTimeMillis()}" }
        val payload = JSONObject()
            .put("articleNumber", article.raw).put("suggestedLocationCode", suggested).put("scannedLocationCode", scanned)
            .put("containerCode", containerCode.trim().uppercase()).put("bundleCode", bundleCode)
            .put("quantityOverride", quantity).put("quantityCorrectionReason", correctionReason).put("userId", userId)
        val bundle = JSONObject().put("id", -System.currentTimeMillis()).put("bundle_code", bundleCode).put("article_number", article.articleNumber)
            .put("spring_type_code", article.springType).put("side", article.side).put("length_mm", article.lengthMm)
            .put("quantity_current", quantity).put("quantity_original", quantity).put("status", "IN_STOCK")
            .put("location_id", location.optLong("id")).put("location_code", location.optString("code"))
            .put("location_display_name", location.optString("display_name")).put("container_code", containerCode.trim().uppercase())
        val db = writableDatabase; db.beginTransaction()
        try {
            queueMutation(db, "RECEIVE", payload)
            db.insertWithOnConflict("bundles", null, ContentValues().apply { put("bundle_code", bundleCode); put("json", bundle.toString()) }, SQLiteDatabase.CONFLICT_REPLACE)
            location.put("occupied_bundle_id", bundle.optLong("id")).put("occupied_bundle_code", bundleCode).put("occupied_article_number", article.articleNumber)
            saveLocation(db, location)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return bundleCode
    }

    fun findBundles(scanInput: String): List<JSONObject> {
        val scan = scanInput.trim().uppercase(); var article = scan
        try { article = parseArticle(scan).articleNumber } catch (_: Exception) {}
        val results = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT json FROM bundles", null).use { c ->
            while (c.moveToNext()) {
                val row = JSONObject(c.getString(0))
                if (row.optString("bundle_code").uppercase() == scan || row.optString("article_number").uppercase() == scan || row.optString("article_number").uppercase() == article || row.optString("location_code").uppercase() == scan) results += row
            }
        }
        return results.sortedByDescending { it.optString("updated_at", it.optString("created_at")) }
    }

    fun moveOffline(bundle: JSONObject, toLocationCode: String, reason: String, userId: Long) {
        val article = parseArticle(bundle.optString("article_number"))
        val location = getLocation(toLocationCode) ?: throw IllegalArgumentException("Nieuwe locatie niet gevonden.")
        validateLocation(location, article, true)
        if (bundle.optString("location_code").uppercase() == location.optString("code").uppercase()) throw IllegalArgumentException("Bundel staat al op deze locatie.")
        val payload = JSONObject().put("bundleId", bundle.optLong("id")).put("bundleCode", bundle.optString("bundle_code"))
            .put("toLocationCode", location.optString("code")).put("reason", reason).put("userId", userId)
        val db = writableDatabase; db.beginTransaction()
        try {
            queueMutation(db, "MOVE", payload)
            getLocation(bundle.optString("location_code"))?.let { old -> old.put("occupied_bundle_id", JSONObject.NULL).put("occupied_bundle_code", "").put("occupied_article_number", ""); saveLocation(db, old) }
            location.put("occupied_bundle_id", bundle.optLong("id")).put("occupied_bundle_code", bundle.optString("bundle_code")).put("occupied_article_number", bundle.optString("article_number")); saveLocation(db, location)
            bundle.put("location_id", location.optLong("id")).put("location_code", location.optString("code")).put("location_display_name", location.optString("display_name")).put("status", "MOVED")
            saveBundle(db, bundle)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun issueOffline(bundle: JSONObject, quantity: Int?, reason: String, userId: Long) {
        val current = bundle.optInt("quantity_current")
        val issue = quantity ?: current
        if (issue <= 0 || issue > current) throw IllegalArgumentException("Ongeldig uitboekaantal.")
        val payload = JSONObject().put("bundleId", bundle.optLong("id")).put("bundleCode", bundle.optString("bundle_code"))
            .put("quantity", issue).put("issueMode", if (issue == current) "bundle" else "quantity").put("reason", reason).put("userId", userId)
        val db = writableDatabase; db.beginTransaction()
        try {
            queueMutation(db, "ISSUE", payload)
            val left = current - issue
            if (left == 0) {
                db.delete("bundles", "bundle_code=?", arrayOf(bundle.optString("bundle_code")))
                getLocation(bundle.optString("location_code"))?.let { loc -> loc.put("occupied_bundle_id", JSONObject.NULL).put("occupied_bundle_code", "").put("occupied_article_number", ""); saveLocation(db, loc) }
            } else {
                bundle.put("quantity_current", left).put("status", "PARTLY_USED"); saveBundle(db, bundle)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun queueMutation(db: SQLiteDatabase, type: String, payload: JSONObject): String {
        val uuid = UUID.randomUUID().toString(); val created = Instant.now().toString()
        db.insertOrThrow("mutations", null, ContentValues().apply { put("uuid", uuid); put("type", type); put("payload", payload.toString()); put("status", "PENDING"); put("created_at", created) })
        return uuid
    }
    private fun saveLocation(db: SQLiteDatabase, row: JSONObject) { db.insertWithOnConflict("locations", null, ContentValues().apply { put("code", row.optString("code")); put("json", row.toString()) }, SQLiteDatabase.CONFLICT_REPLACE) }
    private fun saveBundle(db: SQLiteDatabase, row: JSONObject) { db.insertWithOnConflict("bundles", null, ContentValues().apply { put("bundle_code", row.optString("bundle_code")); put("json", row.toString()) }, SQLiteDatabase.CONFLICT_REPLACE) }

    fun pendingJson(): JSONArray {
        val result = JSONArray()
        readableDatabase.rawQuery("SELECT uuid,type,payload,created_at FROM mutations WHERE status<>'PROCESSED' ORDER BY created_at", null).use { c ->
            while (c.moveToNext()) result.put(JSONObject().put("uuid", c.getString(0)).put("type", c.getString(1)).put("payload", JSONObject(c.getString(2))).put("createdAt", c.getString(3)))
        }
        return result
    }
    fun pendingCount(): Int { readableDatabase.rawQuery("SELECT COUNT(*) FROM mutations WHERE status<>'PROCESSED'", null).use { return if (it.moveToFirst()) it.getInt(0) else 0 } }
    fun mutationRows(): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT uuid,type,payload,status,created_at,error FROM mutations ORDER BY created_at DESC LIMIT 100", null).use { c ->
            while (c.moveToNext()) rows += JSONObject().put("uuid",c.getString(0)).put("type",c.getString(1)).put("payload",JSONObject(c.getString(2))).put("status",c.getString(3)).put("created_at",c.getString(4)).put("error",c.getString(5) ?: "")
        }
        return rows
    }
    fun applySyncResults(results: JSONArray) {
        val db = writableDatabase; db.beginTransaction()
        try {
            for (i in 0 until results.length()) {
                val row = results.getJSONObject(i); val uuid = row.optString("uuid"); val status = row.optString("status")
                if (status == "PROCESSED") db.delete("mutations", "uuid=?", arrayOf(uuid))
                else db.update("mutations", ContentValues().apply { put("status", status); put("error", row.optString("error")) }, "uuid=?", arrayOf(uuid))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun clearFailed() { writableDatabase.delete("mutations", "status IN ('FAILED','CONFLICT')", null) }

    /** Verwijdert alleen de servercache; openstaande mutaties blijven veilig bewaard. */
    fun clearCachedServerData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("spring_types", null, null)
            db.delete("standard_lengths", null, null)
            db.delete("locations", null, null)
            db.delete("bundles", null, null)
            db.delete("meta", "key IN ('last_sync','settings','cached_location_count','cached_bundle_count')", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun locations(searchInput: String = ""): List<JSONObject> {
        val search = searchInput.trim().uppercase()
        val rows = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT json FROM locations", null).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject(cursor.getString(0))
                val matches = search.isBlank() ||
                    row.optString("code").uppercase().contains(search) ||
                    row.optString("display_name").uppercase().contains(search) ||
                    row.optString("old_location_code").uppercase().contains(search) ||
                    row.optString("occupied_bundle_code").uppercase().contains(search) ||
                    row.optString("occupied_article_number").uppercase().contains(search)
                if (matches) rows += row
            }
        }
        return rows.sortedWith(compareBy<JSONObject>(
            { it.optString("zone") },
            { it.optInt("rack") },
            { it.optInt("column_nr") },
            { it.optInt("row_nr") },
            { it.optInt("position_nr") },
            { it.optString("code") },
        ))
    }

    fun stock(searchInput: String = ""): List<JSONObject> {
        val search = searchInput.trim().uppercase(); val rows = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT json FROM bundles", null).use { c ->
            while (c.moveToNext()) {
                val row = JSONObject(c.getString(0));
                if (search.isBlank() || row.optString("article_number").uppercase().contains(search) || row.optString("bundle_code").uppercase().contains(search) || row.optString("location_code").uppercase().contains(search)) rows += row
            }
        }
        return rows.sortedBy { it.optString("article_number") }
    }
}
