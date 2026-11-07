package io.github.hackyday.irforge

import android.app.Application
import io.github.hackyday.irforge.data.Store
import io.github.hackyday.irforge.ir.IrEngine

class ForgeApp : Application() {
    lateinit var store: Store
        private set
    lateinit var irEngine: IrEngine
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        irEngine = IrEngine(this, store)
    }
}
