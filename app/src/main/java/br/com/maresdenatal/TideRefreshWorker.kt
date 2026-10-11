
private fun findNatalHarborCode(): String {
    val root = getJson(
        "https://tabuamare.api.br/api/v2/harbor_names/rn"
    )

    val arr = root.optJSONArray("data")
        ?: error("API não retornou a lista de portos do RN")

    for (i in 0 until arr.length()) {
        val item = arr.getJSONObject(i)
        val name = item.optString("harbor_name")
            .uppercase(Locale.ROOT)

        if (name.contains("PORTO DE NATAL")) {
            val id = item.optString("id")

            if (id.isNotBlank() && id != "null") {
                return id
            }
        }
    }

    error("Porto de Natal não localizado na API")
}
