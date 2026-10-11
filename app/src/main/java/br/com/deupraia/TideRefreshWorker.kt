
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

class TideRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private data class TideEvent(
        val time: Calendar,
        val level: Double
    )

    override suspend fun doWork(): Result {
        return try {
            val today = Calendar.getInstance()
            val tomorrow = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
            }

            val harborCode = findNatalHarborCode()

            // Busca dias vizinhos para classificar corretamente
            // os eventos próximos à meia-noite.
            val start = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, -1)
            }

            val end = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 2)
            }

            val events = mutableListOf<TideEvent>()
            val cursor = start.clone() as Calendar

            while (cursor.timeInMillis <= end.timeInMillis) {
                events.addAll(fetchDayEvents(harborCode, cursor))
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }

            val ordered = events
                .distinctBy { it.time.timeInMillis }
                .sortedBy { it.time.timeInMillis }

            val todayRows = formatDay(ordered, today)
            val tomorrowRows = formatDay(ordered, tomorrow)

            if (todayRows.isBlank() && tomorrowRows.isBlank()) {
                error("A API não retornou eventos de maré válidos")
            }

            val updatedAt = SimpleDateFormat(
                "dd/MM HH:mm",
                Locale("pt", "BR")
            ).format(Calendar.getInstance().time)

            applicationContext
                .getSharedPreferences("tides", Context.MODE_PRIVATE)
                .edit()
                .putString(
                    "today_rows",
                    todayRows.ifBlank { "Sem dados para hoje" }
                )
                .putString(
                    "tomorrow_rows",
                    tomorrowRows.ifBlank { "Sem dados para amanhã" }
                )
                .putString(
                    "status",
                    "Natal/RN • atualizado $updatedAt"
                )
                .apply()

            TideWidgetProvider.refreshAll(applicationContext)
            Result.success()

        } catch (e: Exception) {
            val prefs = applicationContext.getSharedPreferences(
                "tides",
                Context.MODE_PRIVATE
            )

            val hasCache = !prefs
                .getString("today_rows", null)
                .isNullOrBlank()

            prefs.edit()
                .putString(
                    "status",
                    if (hasCache) {
                        "Sem conexão • última consulta mantida"
                    } else {
                        "Falha na consulta • toque em ⟳"
                    }
                )
                .apply()

            TideWidgetProvider.refreshAll(applicationContext)
            Result.retry()
        }
    }

    private fun fetchDayEvents(
        code: String,
        date: Calendar
    ): List<TideEvent> {

        val month = date.get(Calendar.MONTH) + 1
        val day = date.get(Calendar.DAY_OF_MONTH)

        val url =
            "https://tabuamare.api.br/api/v2/tabua-mare/" +
            "$code/$month/[$day]"

        val root = getJson(url)
        val data = root.optJSONArray("data")
            ?: error("Resposta da API sem campo data")

        val result = mutableListOf<TideEvent>()

        for (i in 0 until data.length()) {
            val port = data.getJSONObject(i)
            val months = port.optJSONArray("months") ?: continue

            for (m in 0 until months.length()) {
                val monthObject = months.getJSONObject(m)

                if (monthObject.optInt("month", -1) != month) {
                    continue
                }

                val days = monthObject.optJSONArray("days") ?: continue

                for (d in 0 until days.length()) {
                    val dayObject = days.getJSONObject(d)

                    if (dayObject.optInt("day", -1) != day) {
                        continue
                    }

                    val hours = dayObject.optJSONArray("hours") ?: continue

                    for (h in 0 until hours.length()) {
                        val tide = hours.getJSONObject(h)
                        val hourText = tide.optString("hour")
                        val level = tide.optDouble(
                            "level",
                            Double.NaN
                        )

                        if (hourText.length < 5 || !level.isFinite()) {
                            continue
                        }

                        val parts = hourText.split(":")
                        if (parts.size < 2) continue

                        val hour = parts[0].toIntOrNull() ?: continue
                        val minute = parts[1].toIntOrNull() ?: continue

                        if (hour !in 0..23 || minute !in 0..59) {
                            continue
                        }

                        val eventTime = date.clone() as Calendar
                        eventTime.set(Calendar.HOUR_OF_DAY, hour)
                        eventTime.set(Calendar.MINUTE, minute)
                        eventTime.set(Calendar.SECOND, 0)
                        eventTime.set(Calendar.MILLISECOND, 0)

                        result.add(
                            TideEvent(eventTime, level)
                        )
                    }
                }
            }
        }

        return result
    }

    private fun formatDay(
        events: List<TideEvent>,
        date: Calendar
    ): String {

        val output = mutableListOf<String>()

        for (i in events.indices) {
            val event = events[i]

            val eventDate = event.time
            if (
                eventDate.get(Calendar.YEAR) != date.get(Calendar.YEAR) ||
                eventDate.get(Calendar.DAY_OF_YEAR) !=
                    date.get(Calendar.DAY_OF_YEAR)
            ) {
                continue
            }

            // Sem dois vizinhos não há como confirmar um máximo
            // ou mínimo. Não inventamos uma classificação.
            if (i == 0 || i == events.lastIndex) continue

            val previous = events[i - 1].level
            val next = events[i + 1].level

            val isHigh =
                event.level > previous && event.level > next

            val isLow =
                event.level < previous && event.level < next

            val marker: String
            val label: String

    when {
    isHigh -> {
        marker = "⬆️"
        label = "Alta"
    }

    isLow -> {
        marker = "⬇️"
        label = "Baixa"
    }

    else -> continue
}
                else -> continue
            }

            val hour = SimpleDateFormat(
                "HH:mm",
                Locale("pt", "BR")
            ).format(event.time.time)

            output.add("$marker $hour  $label")
        }

        return output.joinToString("\n")
    }

    private fun findNatalHarborCode(): String {
        val root = getJson(
            "https://tabuamare.api.br/api/v2/harbor_names/rn"
        )

        val ports = root.optJSONArray("data")
            ?: error("Lista de portos não encontrada")

        for (i in 0 until ports.length()) {
            val item = ports.getJSONObject(i)
            val name = item.optString("harbor_name")
                .uppercase(Locale.ROOT)

            if (name.contains("PORTO DE NATAL")) {
                val id = item.optString("id")

                if (id.isNotBlank() && id != "null") {
                    return id
                }
            }
        }

        error("Porto de Natal não encontrado")
    }

    private fun getJson(address: String): JSONObject {
        val connection =
            (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }

        try {
            val code = connection.responseCode

            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream.bufferedReader().use {
                it.readText()
            }

            if (code !in 200..299) {
                error("API HTTP $code")
            }

            return JSONObject(body)

        } finally {
            connection.disconnect()
        }
    }
}
