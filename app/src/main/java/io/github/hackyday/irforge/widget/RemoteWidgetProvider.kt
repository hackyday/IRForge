package io.github.hackyday.irforge.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.RemoteViews
import android.widget.Toast
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 4×2 home-screen widget: four favorite buttons that transmit without opening the app. */
class RemoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as ForgeApp
        for (id in appWidgetIds) updateOne(context, app, manager, id)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as ForgeApp
        appWidgetIds.forEach { app.store.deleteWidgetCfg(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_BTN) return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val idx = intent.getIntExtra(EXTRA_INDEX, -1)
        val app = context.applicationContext as ForgeApp
        val cfg = app.store.widgetCfg(appWidgetId) ?: return
        val codeId = cfg.buttonIds.getOrNull(idx) ?: return
        val code = app.store.codeById(codeId) ?: return

        val pending = goAsync()
        (context.applicationContext as? android.app.Application)?.let { application ->
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.NonCancellable).launch {
                try {
                    // Ready-sinks only: a widget press must never sit waiting for a permission dialog.
                    val err = app.irEngine.sendCodeReadyNow(code)
                    if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    else vibrate(context)
                } catch (e: Exception) {
                    Toast.makeText(context, "IR error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    pending.finish()
                }
            }
        } ?: pending.finish()
    }

    private fun vibrate(context: Context) {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(20, 140))
        else @Suppress("DEPRECATION") v.vibrate(20)
    }

    companion object {
        const val ACTION_BTN = "io.github.hackyday.irforge.widget.BTN"
        const val EXTRA_INDEX = "idx"

        fun updateOne(context: Context, app: ForgeApp, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_remote)
            val cfg = app.store.widgetCfg(appWidgetId)
            val remote = cfg?.remoteId?.let { rid -> app.store.remoteById(rid) }
            val labels = MutableList(4) { "" }

            if (remote != null) {
                views.setTextViewText(R.id.widget_title, remote.name)
                val buttonsById = remote.pages.flatMap { it.buttons }.associateBy { it.id }
                cfg.buttonIds.take(4).forEachIndexed { i, bid ->
                    labels[i] = buttonsById[bid]?.label ?: ""
                }
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            }

            val openApp = android.app.PendingIntent.getActivity(
                context, 0,
                Intent(context, io.github.hackyday.irforge.MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            for (i in 0 until 4) {
                val viewId = when (i) { 0 -> R.id.widget_b0; 1 -> R.id.widget_b1; 2 -> R.id.widget_b2; else -> R.id.widget_b3 }
                if (labels[i].isEmpty()) {
                    views.setTextViewText(viewId, context.getString(R.string.widget_empty))
                    views.setOnClickPendingIntent(viewId, openApp)
                } else {
                    views.setTextViewText(viewId, labels[i])
                    val pi = android.app.PendingIntent.getBroadcast(
                        context, (appWidgetId * 10 + i),
                        Intent(context, RemoteWidgetProvider::class.java)
                            .setAction(ACTION_BTN)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            .putExtra(EXTRA_INDEX, i),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                    views.setOnClickPendingIntent(viewId, pi)
                }
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        fun updateAll(context: Context) {
            val app = context.applicationContext as ForgeApp
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RemoteWidgetProvider::class.java))
            for (id in ids) updateOne(context, app, manager, id)
        }
    }
}
