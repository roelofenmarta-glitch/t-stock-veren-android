package nl.tstock.veren

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import nl.tstock.veren.databinding.ActivityMainBinding
import org.json.JSONObject
import java.net.URI

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val value = result.contents
        if (value.isNullOrBlank()) {
            Toast.makeText(this, R.string.scan_cancelled, Toast.LENGTH_SHORT).show()
        } else {
            injectScannedValue(value.trim())
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback ?: return@registerForActivityResult
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileChooserCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        configureToolbar()
        configureWebView()
        configureBackNavigation()

        val storedUrl = preferences.getString(KEY_SERVER_URL, BuildConfig.DEFAULT_SERVER_URL).orEmpty()
        if (storedUrl.isBlank()) {
            showServerDialog(force = true)
        } else {
            loadServer(storedUrl)
        }
    }

    private fun configureToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_scan -> {
                    launchScanner()
                    true
                }
                R.id.action_refresh -> {
                    binding.webView.reload()
                    true
                }
                R.id.action_server -> {
                    showServerDialog(force = false)
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configureWebView() = with(binding.webView) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = true
        settings.userAgentString = "${settings.userAgentString} TStockVerenAndroid/10.1"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

        addJavascriptInterface(NativeBridge(), "TStockAndroid")

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = try {
                    fileChooserParams?.createIntent()
                } catch (_: Exception) {
                    null
                } ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                fileChooserLauncher.launch(intent)
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return if (isAllowedServerUri(uri)) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectFocusTracking()
                updateToolbarSubtitle()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Snackbar.make(binding.root, R.string.page_unreachable, Snackbar.LENGTH_LONG)
                        .setAction(R.string.server_settings) { showServerDialog(force = false) }
                        .show()
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                // Nooit ongeldige certificaten stilzwijgend accepteren.
                handler?.cancel()
                Snackbar.make(binding.root, "Ongeldig HTTPS-certificaat op de T-Stock server.", Snackbar.LENGTH_LONG).show()
            }
        }

        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                )
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this@MainActivity, "Download gestart.", Toast.LENGTH_SHORT).show()
        })
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })
    }

    private fun showServerDialog(force: Boolean) {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding)
        }
        val input = EditText(this).apply {
            hint = getString(R.string.server_url_hint)
            setSingleLine(true)
            setText(preferences.getString(KEY_SERVER_URL, BuildConfig.DEFAULT_SERVER_URL).orEmpty())
            selectAll()
        }
        val autoEnter = CheckBox(this).apply {
            text = getString(R.string.auto_enter)
            isChecked = preferences.getBoolean(KEY_AUTO_ENTER, true)
        }
        container.addView(input)
        container.addView(autoEnter)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.server_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .apply { if (!force) setNegativeButton(R.string.cancel, null) }
            .setCancelable(!force)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val normalized = normalizeServerUrl(input.text.toString())
                if (normalized == null) {
                    input.error = getString(R.string.invalid_server)
                    return@setOnClickListener
                }
                preferences.edit()
                    .putString(KEY_SERVER_URL, normalized)
                    .putBoolean(KEY_AUTO_ENTER, autoEnter.isChecked)
                    .apply()
                dialog.dismiss()
                loadServer(normalized)
            }
        }
        dialog.show()
    }

    private fun normalizeServerUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        return try {
            val uri = URI(trimmed)
            if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) trimmed else null
        } catch (_: Exception) {
            null
        }
    }

    private fun loadServer(url: String) {
        binding.webView.loadUrl(url)
        updateToolbarSubtitle()
    }

    private fun updateToolbarSubtitle() {
        val url = preferences.getString(KEY_SERVER_URL, "").orEmpty()
        binding.toolbar.subtitle = try {
            val uri = URI(url)
            buildString {
                append(uri.host ?: url)
                if (uri.port > 0) append(":${uri.port}")
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            setPrompt("Scan artikel-, bundel- of locatiecode")
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    private fun injectFocusTracking() {
        val script = """
            (function() {
              if (window.__tstockAndroidFocusInstalled) return;
              window.__tstockAndroidFocusInstalled = true;
              document.addEventListener('focusin', function(event) {
                var el = event.target;
                if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) {
                  window.__tstockLastInput = el;
                }
              }, true);
              window.print = function() { TStockAndroid.printPage(); };
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(script, null)
    }

    private fun injectScannedValue(value: String) {
        val autoEnter = preferences.getBoolean(KEY_AUTO_ENTER, true)
        val jsonValue = JSONObject.quote(value.uppercase())
        val script = """
            (function(value, autoEnter) {
              function isVisible(el) {
                if (!el || el.disabled || el.readOnly) return false;
                var r = el.getBoundingClientRect();
                var s = window.getComputedStyle(el);
                return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden';
              }
              var target = window.__tstockLastInput;
              if (!isVisible(target)) target = document.activeElement;
              if (!isVisible(target) || !/^(INPUT|TEXTAREA)$/.test(target.tagName)) {
                var inputs = Array.from(document.querySelectorAll('input, textarea')).filter(isVisible);
                target = inputs.find(function(el) {
                  var text = [el.placeholder, el.name, el.getAttribute('aria-label')].filter(Boolean).join(' ');
                  return /(scan|artikelnummer|locatiecode|bundelcode|containercode|picklijstcode)/i.test(text);
                }) || inputs[0];
              }
              if (!target) return 'NO_INPUT';
              target.focus();
              var descriptor = Object.getOwnPropertyDescriptor(
                target.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype,
                'value'
              );
              if (descriptor && descriptor.set) descriptor.set.call(target, value); else target.value = value;
              target.dispatchEvent(new Event('input', { bubbles: true }));
              target.dispatchEvent(new Event('change', { bubbles: true }));
              window.__tstockLastInput = target;
              if (autoEnter) {
                setTimeout(function() {
                  ['keydown', 'keypress', 'keyup'].forEach(function(type) {
                    target.dispatchEvent(new KeyboardEvent(type, {
                      key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true
                    }));
                  });
                }, 150);
              }
              return target.placeholder || target.name || target.tagName;
            })($jsonValue, ${if (autoEnter) "true" else "false"});
        """.trimIndent()

        binding.webView.evaluateJavascript(script) { result ->
            if (result == null || result == "\"NO_INPUT\"") {
                Snackbar.make(binding.root, R.string.scan_no_input, Snackbar.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Scan ingevuld: $value", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isAllowedServerUri(uri: Uri): Boolean {
        val configured = preferences.getString(KEY_SERVER_URL, "").orEmpty()
        return try {
            val server = URI(configured)
            uri.scheme in listOf("http", "https") &&
                uri.host.equals(server.host, ignoreCase = true) &&
                effectivePort(uri.scheme, uri.port) == effectivePort(server.scheme, server.port)
        } catch (_: Exception) {
            false
        }
    }

    private fun effectivePort(scheme: String?, port: Int): Int = when {
        port > 0 -> port
        scheme == "https" -> 443
        else -> 80
    }

    private inner class NativeBridge {
        @JavascriptInterface
        fun printPage() {
            runOnUiThread {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "T-Stock Veren"
                val adapter = binding.webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, adapter, PrintAttributes.Builder().build())
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "tstock_veren_android"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_AUTO_ENTER = "auto_enter"
    }
}
