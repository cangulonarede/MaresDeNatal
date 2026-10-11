package br.com.deupraia

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
            val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val harborCode = findNatalHarborCode()
            val todayRows = fetchDay(harborCode, today)
            val tomorrowRows = fetchDay(harborCode, tomorrow)

            if (todayRows.isEmpty() && tomorrowRows.isEmpty()) {
                error("A API não retornou horários para hoje e amanhã")
            }

            val updatedAt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
                .format(Calendar.getInstance().time)
            applicationContext.getSharedPreferences("tides", Context.MODE_PRIVATE).edit()
                .putString("today_rows", todayRows.ifEmpty { "Sem dados para hoje" })
                .putString("tomorrow_rows", tomorrowRows.ifEmpty { "Sem dados para amanhã" })
                .putString("status", "Natal/RN • atualizado $updatedAt")
                .apply()

            TideWidgetProvider.refreshAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            val prefs = applicationContext.getSharedPreferences("tides", Context.MODE_PRIVATE)
            val hasCache = !prefs.getString("today_rows", null).isNullOrBlank()
            prefs.edit().putString(
                "status",
                if (hasCache) "Sem conexão • última consulta mantida"
                else "Falha na consulta • toque em ⟳"
            ).apply()
            TideWidgetProvider.refreshAll(applicationContext)
            Result.retry()
        }
    }

    private fun fetchDay(code: String, date: Calendar): String {
        val month = date.get(Calendar.MONTH) + 1
        val day = date.get(Calendar.DAY_OF_MONTH)
        val url = "https://tabuamare.api.br/api/v2/tabua-mare/$code/$month/[$day]"
        val root = getJson(url)
        val data = root.optJSONArray("data") ?: error("Resposta sem campo data")
        val lines = mutableListOf<String>()

        for (i in 0 until data.length()) {
            val months = data.getJSONObject(i).optJSONArray("months") ?: continue
            for (m in 0 until months.length()) {
                val mo = months.getJSONObject(m)
                if (mo.optInt("month", -1) != month) continue
                val days = mo.optJSONArray("days") ?: continue
                for (d in 0 until days.length()) {
                    val dayObject = days.getJSONObject(d)
                    if (dayObject.optInt("day", -1) != day) continue
                    val hours = dayObject.optJSONArray("hours") ?: continue
                    val values = mutableListOf<Pair<String, Double>>()
                    for (h in 0 until hours.length()) {
                        val tide = hours.getJSONObject(h)
                        val hour = tide.optString("hour").take(5)
                        val level = tide.optDouble("level", Double.NaN)
                        if (hour.length == 5 && !level.isNaN()) values.add(hour to level)
                    }
                    for (iHour in values.indices) {
                        val (hour, level) = values[iHour]
                        val previous = values.getOrNull(iHour - 1)?.second
                        val next = values.getOrNull(iHour + 1)?.second
                        val high = previous != null && next != null &&
                            level >= previous && level >= next
                        val low = previous != null && next != null &&
                            level <= previous && level <= next
                        val arrow = when {
                            high -> "↑"
                            low -> "↓"
                            else -> if (next != null && next > level) "↑" else "↓"
                        }
                        val label = if (arrow == "↑") "Alta" else "Baixa"
                        val colorMarker = if (arrow == "↑") "🔵" else "🔻"
                        lines.add("$colorMarker $hour  $label")
                    }
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun findNatalHarborCode(): String {
        val root = getJson("https://tabuamare.api.br/api/v2/harbor_names/rn")
        val ports = root.optJSONArray("data") ?: error("Lista de portos não encontrada")
        for (i in 0 until ports.length()) {
            val item = ports.getJSONObject(i)
            val name = item.optString("harbor_name").uppercase(Locale.ROOT)
            if (name.contains("PORTO DE NATAL")) {
                val id = item.optString("id")
                if (id.isNotBlank() && id != "null") return id
            }
        }
        error("Porto de Natal não encontrado")
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("API HTTP $code")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
