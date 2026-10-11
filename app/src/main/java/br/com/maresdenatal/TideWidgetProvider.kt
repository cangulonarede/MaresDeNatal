package br.com.maresdenatal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import br.com.maresdenatal.R

class TideWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> render(context, manager, id) }
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<TideRefreshWorker>().build())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<TideRefreshWorker>().build())
        }
    }

    companion object {
        const val ACTION_REFRESH = "br.com.maresdenatal.REFRESH"
        fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("tides", Context.MODE_PRIVATE)
            val rows = prefs.getString("rows", "Consultando a tábua de marés…") ?: "Consultando a tábua de marés…"
            val status = prefs.getString("status", "Fonte: aguardando consulta") ?: "Fonte: aguardando consulta"
            val views = RemoteViews(context.packageName, R.layout.tide_widget)
            views.setTextViewText(R.id.tide_rows, rows)
            views.setTextViewText(R.id.status, status)
            val intent = Intent(context, TideWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.refresh, pending)
            views.setOnClickPendingIntent(R.id.title, pending)
            manager.updateAppWidget(id, views)
        }
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TideWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
        }
    }
}
