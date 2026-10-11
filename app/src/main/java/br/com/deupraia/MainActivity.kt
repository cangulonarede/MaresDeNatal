package br.com.deupraia

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "DeuPraia!\\n\\nAdicione o widget à tela inicial para consultar as marés de Natal/RN."
            textSize = 18f
            setPadding(32, 48, 32, 32)
        })
        val request = PeriodicWorkRequestBuilder<TideRefreshWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "tide-refresh", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}
