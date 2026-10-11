package br.com.maresdenatal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TideRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val today = Calendar.getInstance()
            val month = today.get(Calendar.MONTH) + 1
            val day1 = today.get(Calendar.DAY_OF_MONTH)
            val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val day2 = tomorrow.get(Calendar.DAY_OF_MONTH)
            val days = if (month == tomorrow.get(Calendar.MONTH) + 1) "[$day1,$day2]" else "[$day1]"
            val harborCode = findNatalHarborCode()
            val json = getJson("https://tabuamare.api.br/api/v2/tabua-mare/$harborCode/$month/$days")
            val rows = parseRows(json, day1, day2, month, tomorrow.get(Calendar.MONTH) + 1)
            val prefs = applicationContext.getSharedPreferences("tides", Context.MODE_PRIVATE)
            prefs.edit().putString("rows", rows)
                .putString("status", "Atualizado: " + SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Calendar.getInstance().time) + " • API")
                .apply()
            TideWidgetProvider.refreshAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            val prefs = applicationContext.getSharedPreferences("tides", Context.MODE_PRIVATE)
            val old = prefs.getString("rows", null)
            prefs.edit().putString("status", if (old == null) "Sem dados • verifique a conexão" else "Sem conexão • exibindo última consulta").apply()
            TideWidgetProvider.refreshAll(applicationContext)
            Result.retry()
        }
    }

    private fun findNatalHarborCode(): String {
        val root = getJson("https://tabuamare.api.br/api/v2/harbor_names/rn")
        val arr = root.optJSONArray("data") ?: error("Resposta sem lista de portos")
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val name = item.optString("harbor_name").uppercase(Locale.ROOT)
            if (name.contains("PORTO DE NATAL")) {
                // A API documenta o ID numérico na listagem e códigos de porto no endpoint.
                // Tentamos os formatos conhecidos; se o serviço exigir outro código, a mensagem de erro fica visível.
                val id = item.optString("code", item.optString("harbor_code", ""))
                if (id.isNotBlank() && id != "null") return id
                val stateId = item.optInt("id", -1)
                if (stateId > 0) return stateId.toString()
            }
        }
        error("Porto de Natal não localizado na API")
    }

    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("API HTTP $code: $body")
            return JSONObject(body)
        } finally { conn.disconnect() }
    }

    private fun parseRows(root: JSONObject, d1: Int, d2: Int, m1: Int, m2: Int): String {
        val output = mutableListOf<String>()
        val data = root.optJSONArray("data") ?: error("Resposta da API sem dados")
        for (i in 0 until data.length()) {
            val months = data.getJSONObject(i).optJSONArray("months") ?: continue
            for (m in 0 until months.length()) {
                val mo = months.getJSONObject(m)
                val monthNum = mo.optInt("month", -1)
                if (monthNum != m1 && monthNum != m2) continue
                val days = mo.optJSONArray("days") ?: continue
                for (d in 0 until days.length()) {
                    val day = days.getJSONObject(d)
                    val dayNum = day.optInt("day", -1)
                    if (!((monthNum == m1 && dayNum == d1) || (monthNum == m2 && dayNum == d2))) continue
                    val date = "%02d/%02d".format(dayNum, monthNum)
                    val hours = day.optJSONArray("hours") ?: continue
                    for (h in 0 until hours.length()) {
                        val tide = hours.getJSONObject(h)
                        val hour = tide.optString("hour").take(5)
                        val level = tide.optDouble("level", Double.NaN)
                        if (hour.length == 5 && !level.isNaN()) {
                            output.add("$date  $hour   %.2f m".format(Locale("pt", "BR"), level))
                        }
                    }
                }
            }
        }
        if (output.isEmpty()) error("A API não retornou horários para hoje e amanhã")
        return output.joinToString("\n")
    }
}
