package nl.tstock.veren

import android.app.Application

class TStockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashStore.install(this)
    }
}
