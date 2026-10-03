package uk.railboard.app.data

data class Station(val name: String, val crs: String) {
    internal val key: String = normalise(name)
}

/**
 * All ~2,600 National Rail stations in Great Britain, loaded on first use from the bundled
 * `stations.csv` resource (lines of `CRS,Name`).
 */
object Stations {
    val all: List<Station> by lazy {
        val stream = Stations::class.java.getResourceAsStream("stations.csv")
            ?: error("stations.csv missing from resources")
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val comma = line.indexOf(',')
                    Station(name = line.substring(comma + 1).trim(), crs = line.substring(0, comma).trim())
                }
                .toList()
        }
    }

    private val byCrs: Map<String, Station> by lazy { all.associateBy { it.crs } }

    fun nameFor(crs: String): String? = byCrs[crs.uppercase()]?.name

    /**
     * Ranked search: exact CRS code, then exact name, then names starting with the query,
     * then names with a word starting with it (so "cross" finds "London Kings Cross"),
     * then names containing it anywhere. Punctuation is ignored, so "kings" matches "King's".
     */
    fun search(query: String, limit: Int = 6): List<Station> {
        val q = normalise(query)
        if (q.isEmpty()) return emptyList()
        val crsHit = byCrs[query.trim().uppercase()]

        val ranked = all.mapNotNull { st ->
            val rank = when {
                st.key == q -> 1
                st.key.startsWith(q) -> 2
                st.key.contains(" $q") -> 3
                st.key.contains(q) -> 4
                else -> return@mapNotNull null
            }
            rank to st
        }.sortedWith(compareBy({ it.first }, { it.second.name.length }))
            .map { it.second }

        return (listOfNotNull(crsHit) + ranked).distinct().take(limit)
    }

    fun isCrs(s: String): Boolean = s.trim().matches(Regex("[A-Za-z]{3}"))
}

private val NON_ALNUM = Regex("[^a-z0-9 ]")
private val SPACES = Regex("\\s+")

internal fun normalise(s: String): String =
    s.lowercase().replace('&', ' ').replace('-', ' ').replace(NON_ALNUM, "").replace(SPACES, " ").trim()
