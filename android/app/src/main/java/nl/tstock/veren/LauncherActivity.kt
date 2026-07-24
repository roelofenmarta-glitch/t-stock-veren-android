package nl.tstock.veren

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLauncher()
    }

    override fun onResume() {
        super.onResume()
        // Na terugkeer vanuit een gecrashte MainActivity wordt het rapport opnieuw ingelezen.
        if (CrashStore.report(this) != null) showLauncher()
    }

    private fun showLauncher() {
        val density = resources.displayMetrics.density
        val pad = (22 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(11, 17, 28))
        }

        root.addView(TextView(this).apply {
            text = "T-Stock Veren TEST"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "Start-veilige T-Stock Veren TEST 10.6"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (18 * density).toInt())
        })

        val report = CrashStore.report(this)
        if (report == null) {
            root.addView(TextView(this).apply {
                text = "Dit scherm gebruikt geen Compose, scanner of lokale database. Druk op Starten om de magazijnapp te openen. Als die vastloopt, open je deze app nogmaals; dan staat de exacte fout hier in beeld."
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, pad)
            })
            root.addView(actionButton("T-Stock Veren starten") { startMain() })
            root.addView(actionButton("Lokale testgegevens wissen") { clearLocalData() })
        } else {
            root.addView(TextView(this).apply {
                text = "Er is een app-crash vastgelegd. Maak hiervan een screenshot of kopieer de melding."
                textSize = 17f
                setTextColor(Color.rgb(255, 107, 107))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (12 * density).toInt())
            })
            val reportView = TextView(this).apply {
                text = report
                textSize = 12f
                setTextColor(Color.WHITE)
                setTextIsSelectable(true)
                setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
                setBackgroundColor(Color.rgb(20, 28, 41))
            }
            root.addView(reportView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(actionButton("Foutmelding kopiëren") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("T-Stock Veren crash", report))
                Toast.makeText(this, "Foutmelding gekopieerd", Toast.LENGTH_SHORT).show()
            })
            root.addView(actionButton("Opnieuw proberen") {
                CrashStore.clear(this)
                startMain()
            })
            root.addView(actionButton("Lokale testgegevens wissen en opnieuw starten") {
                clearLocalData()
                startMain()
            })
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10 * resources.displayMetrics.density).toInt()
        }
    }

    private fun startMain() {
        CrashStore.clear(this)
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun clearLocalData() {
        getSharedPreferences("tstock_settings", MODE_PRIVATE).edit().clear().commit()
        databaseList().filter { it.startsWith("tstock_veren") }.forEach(::deleteDatabase)
        CrashStore.clear(this)
        Toast.makeText(this, "Alleen lokale testgegevens zijn gewist", Toast.LENGTH_SHORT).show()
        showLauncher()
    }
}
