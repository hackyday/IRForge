package io.github.hackyday.irforge

import android.app.Application
import io.github.hackyday.irforge.data.Store
import io.github.hackyday.irforge.ir.IrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ForgeApp : Application() {
    lateinit var store: Store
        private set
    lateinit var irEngine: IrEngine
        private set

    /** App-lifetime scope for fire-and-forget work (imports from intents, etc.). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        irEngine = IrEngine(this, store)
    }
}
