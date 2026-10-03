package uk.railboard.app.data

data class Station(val name: String, val crs: String)

/**
 * A short list of major GB stations for name search. Any valid 3-letter CRS code
 * can still be typed directly, so stations not listed here work too.
 */
object Stations {
    val all: List<Station> = listOf(
        "Aberdeen" to "ABD", "Ashford International" to "AFK", "Ayr" to "AYR",
        "Bangor (Gwynedd)" to "BNG", "Basingstoke" to "BSK", "Bath Spa" to "BTH",
        "Bedford" to "BDM", "Berwick-upon-Tweed" to "BWK", "Birmingham Moor Street" to "BMO",
        "Birmingham New Street" to "BHM", "Blackpool North" to "BPN", "Bournemouth" to "BMH",
        "Bradford Interchange" to "BDI", "Brighton" to "BTN", "Bristol Parkway" to "BPW",
        "Bristol Temple Meads" to "BRI", "Cambridge" to "CBG", "Canterbury West" to "CBW",
        "Cardiff Central" to "CDF", "Cardiff Queen Street" to "CDQ", "Carlisle" to "CAR",
        "Cheltenham Spa" to "CNM", "Chester" to "CTR", "Clapham Junction" to "CLJ",
        "Colchester" to "COL", "Coventry" to "COV", "Crewe" to "CRE", "Darlington" to "DAR",
        "Derby" to "DBY", "Doncaster" to "DON", "Dundee" to "DEE", "Durham" to "DHM",
        "Ebbsfleet International" to "EBD", "Edinburgh Waverley" to "EDB", "Edinburgh Haymarket" to "HYM",
        "Exeter St Davids" to "EXD", "Fort William" to "FTW", "Gatwick Airport" to "GTW",
        "Glasgow Central" to "GLC", "Glasgow Queen Street" to "GLQ", "Gloucester" to "GCR",
        "Guildford" to "GLD", "Harrogate" to "HGT", "Heathrow Terminals 2 & 3" to "HXX",
        "Holyhead" to "HHD", "Huddersfield" to "HUD", "Hull" to "HUL", "Inverness" to "INV",
        "Ipswich" to "IPS", "Kettering" to "KET", "Lancaster" to "LAN", "Leeds" to "LDS",
        "Leicester" to "LEI", "Lincoln" to "LCN", "Liverpool Lime Street" to "LIV",
        "London Bridge" to "LBG", "London Cannon Street" to "CST", "London Charing Cross" to "CHX",
        "London Euston" to "EUS", "London Fenchurch Street" to "FST", "London King's Cross" to "KGX",
        "London Liverpool Street" to "LST", "London Marylebone" to "MYB", "London Paddington" to "PAD",
        "London St Pancras International" to "STP", "London Victoria" to "VIC", "London Waterloo" to "WAT",
        "Luton" to "LUT", "Luton Airport Parkway" to "LTN", "Manchester Airport" to "MIA",
        "Manchester Oxford Road" to "MCO", "Manchester Piccadilly" to "MAN", "Manchester Victoria" to "MCV",
        "Milton Keynes Central" to "MKC", "Motherwell" to "MTH", "Newcastle" to "NCL",
        "Newport (South Wales)" to "NWP", "Northampton" to "NMP", "Norwich" to "NRW",
        "Nottingham" to "NOT", "Oxenholme Lake District" to "OXN", "Oxford" to "OXF",
        "Paisley Gilmour Street" to "PYG", "Penzance" to "PNZ", "Perth" to "PTH",
        "Peterborough" to "PBO", "Plymouth" to "PLY", "Portsmouth Harbour" to "PMH",
        "Preston" to "PRE", "Reading" to "RDG", "Rugby" to "RUG", "Sheffield" to "SHF",
        "Shrewsbury" to "SHR", "Southampton Central" to "SOU", "St Albans City" to "SAC",
        "Stafford" to "STA", "Stansted Airport" to "SSD", "Stevenage" to "SVG", "Stirling" to "STG",
        "Stoke-on-Trent" to "SOT", "Stratford (London)" to "SRA", "Sunderland" to "SUN",
        "Swansea" to "SWA", "Swindon" to "SWI", "Tamworth" to "TAM", "Wakefield Westgate" to "WKF",
        "Warrington Bank Quay" to "WBQ", "Watford Junction" to "WFJ", "Wigan North Western" to "WGN",
        "Winchester" to "WIN", "Woking" to "WOK", "Wolverhampton" to "WVH",
        "Worcester Foregate Street" to "WOF", "York" to "YRK",
    ).map { (name, crs) -> Station(name, crs) }

    private val byCrs = all.associateBy { it.crs }

    fun nameFor(crs: String): String? = byCrs[crs.uppercase()]?.name

    /** Exact CRS match first, then names starting with the query, then names containing it. */
    fun search(query: String, limit: Int = 6): List<Station> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val exact = byCrs[q.uppercase()]?.let(::listOf).orEmpty()
        val prefix = all.filter { it.name.startsWith(q, ignoreCase = true) || it.name.removePrefix("London ").startsWith(q, ignoreCase = true) }
        val contains = all.filter { it.name.contains(q, ignoreCase = true) }
        return (exact + prefix + contains).distinct().take(limit)
    }

    fun isCrs(s: String): Boolean = s.trim().matches(Regex("[A-Za-z]{3}"))
}
