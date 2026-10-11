
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

class TideRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val today = Calendar.getInstance()
            val tomorrow = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
            }

            val code = findNatalHarborCode()
            val rows = mutableListOf<String>()

            for (date in listOf(today, tomorrow)) {
                val month = date.get(Calendar.MONTH) + 1
                val day = date.get(Calendar.DAY_OF_MONTH)

                val url =
                    "https://tabuamare.api.br/api/v2/tabua-mare/$code/$month/[$day]"

                val json = getJson(url)
                rows.addAll(parseRows(json, day, month))
            }

            if (rows.isEmpty()) {
                error("A API não retornou horários de maré.")
            }

            val updatedAt = SimpleDateFormat(
                "dd/MM HH:mm",
                Locale("pt", "BR")
            ).format(Calendar.getInstance().time)

            applicationContext
                .getSharedPreferences("tides", Context.MODE_PRIVATE)
                .edit()
                .putString("rows", rows.distinct().joinToString("\n"))
                .putString("status", "Atualizado: $updatedAt")
                .apply()

            TideWidgetProvider.refreshAll(applicationContext)
            Result.success()

        } catch (e: Exception) {
            val prefs = applicationContext.getSharedPreferences(
                "tides",
                Context.MODE_PRIVATE
            )

            val oldRows = prefs.getString("rows", null)

            prefs.edit()
                .putString(
                    "status",
                    if (oldRows.isNullOrBlank()) {
                        "Falha: ${e.message ?: "erro na consulta"}"
                    } else {
                        "Sem conexão • última consulta mantida"
                    }
                )
                .apply()

            TideWidgetProvider.refreshAll(applicationContext)
            Result.retry()
        }
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
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream.bufferedReader().use { it.readText() }

            if (statusCode !in 200..299) {
                error("API retornou HTTP $statusCode: $body")
            }

            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRows(
        root: JSONObject,
        targetDay: Int,
        targetMonth: Int
    ): List<String> {
        val output = mutableListOf<String>()
        val data = root.optJSONArray("data")
            ?: error("Resposta sem campo data")

        for (i in 0 until data.length()) {
            val months = data.getJSONObject(i).optJSONArray("months")
                ?: continue

            for (m in 0 until months.length()) {
                val month = months.getJSONObject(m)

                if (month.optInt("month", -1) != targetMonth) {
                    continue
                }

                val days = month.optJSONArray("days") ?: continue

                for (d in 0 until days.length()) {
                    val day = days.getJSONObject(d)

                    if (day.optInt("day", -1) != targetDay) {
                        continue
                    }

                    val hours = day.optJSONArray("hours") ?: continue

                    for (h in 0 until hours.length()) {
                        val tide = hours.getJSONObject(h)
                        val hour = tide.optString("hour").take(5)
                        val level = tide.optDouble("level", Double.NaN)

                        if (hour.length == 5 && !level.isNaN()) {
                            output.add(
                                "%02d/%02d  %s  %.2f m".format(
                                    Locale("pt", "BR"),
                                    targetDay,
                                    targetMonth,
                                    hour,
                                    level
                                )
                            )
                        }
                    }
                }
            }
        }

        return output
    }
}
