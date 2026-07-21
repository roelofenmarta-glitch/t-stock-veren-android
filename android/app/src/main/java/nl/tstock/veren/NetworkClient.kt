package nl.tstock.veren

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NetworkException(message: String, val status: Int = 0, val code: String = "NETWORK_ERROR") : Exception(message)

class NetworkClient(private val baseUrlProvider: () -> String) {
    private fun endpoint(path: String): URL {
        val base = baseUrlProvider().trim().trimEnd('/')
        if (base.isBlank()) throw NetworkException("Vul eerst het serveradres in.")
        return try {
            URL("$base$path")
        } catch (e: Exception) {
            throw NetworkException("Ongeldig serveradres: ${e.message ?: base}", code = "INVALID_SERVER_URL")
        }
    }

    suspend fun get(path: String): JSONObject = request("GET", path, null)
    suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = endpoint(path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val data = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (_: Exception) {
                JSONObject().put("error", text)
            }

            if (status !in 200..299) {
                throw NetworkException(
                    data.optString("error", "Serverfout $status"),
                    status,
                    data.optString("code", "HTTP_$status"),
                )
            }
            data
        } catch (e: NetworkException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(
                "T-Stock-server niet bereikbaar. Offline werken is actief. ${e.message.orEmpty()}".trim(),
                code = "SERVER_UNREACHABLE",
            )
        } catch (e: Exception) {
            throw NetworkException(
                "Verbinding met T-Stock-server mislukt. ${e.message.orEmpty()}".trim(),
                code = "NETWORK_ERROR",
            )
        } finally {
            connection?.disconnect()
        }
    }
}
