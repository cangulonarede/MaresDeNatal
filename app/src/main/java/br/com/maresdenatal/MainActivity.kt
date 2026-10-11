package br.com.maresdenatal

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
        val view = TextView(this).apply {
            text = "Marés de Natal\n\nAdicione o widget à tela inicial.\n\nOs dados são consultados pela internet e a atualização automática ocorre a cada 6 horas.\n\nFonte de referência: CHM/Marinha do Brasil. A integração com a API comunitária precisa ser validada antes do primeiro uso."
            textSize = 18f
            setPadding(32, 48, 32, 32)
        }
        setContentView(view)
        val request = PeriodicWorkRequestBuilder<TideRefreshWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "tide-refresh", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}
