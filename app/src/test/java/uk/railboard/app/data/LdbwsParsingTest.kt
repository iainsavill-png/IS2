package uk.railboard.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LdbwsParsingTest {

    private val sample = """
        {
          "generatedAt": "2026-10-03T10:02:11.123+01:00",
          "locationName": "Reading",
          "crs": "RDG",
          "platformAvailable": true,
          "nrccMessages": [ { "Value": "<p>Disruption between <a href=\"x\">Didcot</a> &amp; Swindon.</p>" } ],
          "trainServices": [
            {
              "std": "10:15", "etd": "On time", "platform": "9", "operator": "Great Western Railway",
              "destination": [ { "locationName": "London Paddington", "crs": "PAD", "via": null } ],
              "isCancelled": false, "cancelReason": null, "delayReason": null, "serviceID": "a1", "length": 9
            },
            {
              "std": "10:20", "etd": "10:31", "platform": null, "operator": "CrossCountry",
              "destination": [ { "locationName": "Manchester Piccadilly", "crs": "MAN", "via": "via Birmingham" } ],
              "delayReason": "This train has been delayed by a signalling fault", "serviceID": "a2"
            },
            {
              "std": "10:25", "etd": "Cancelled", "operator": "Elizabeth line",
              "destination": [ { "locationName": "Abbey Wood", "crs": "ABW" } ],
              "isCancelled": true, "cancelReason": "This train has been cancelled because of a shortage of staff"
            }
          ],
          "busServices": null
        }
    """.trimIndent()

    @Test
    fun parsesBoard() {
        val board = parseStationBoard(sample)
        assertEquals("Reading", board.locationName)
        assertEquals(3, board.allServices.size)
        assertEquals(listOf("Disruption between Didcot & Swindon."), board.messages)

        val (first, second, third) = board.allServices
        assertEquals("London Paddington", first.destinationText)
        assertEquals(ServiceStatus.ON_TIME, first.status)
        assertEquals(9, first.length)
        assertEquals(ServiceStatus.LATE, second.status)
        assertEquals("via Birmingham", second.viaText)
        assertEquals(null, second.platform)
        assertEquals(ServiceStatus.CANCELLED, third.status)
    }

    @Test
    fun emptyBoardParses() {
        val board = parseStationBoard("""{"locationName":"Nowhere","crs":"XXX","trainServices":null}""")
        assertTrue(board.allServices.isEmpty())
        assertTrue(board.messages.isEmpty())
    }

    @Test
    fun busesMergeAcrossMidnight() {
        val board = parseStationBoard(
            """{"trainServices":[{"std":"23:50"},{"std":"00:20"}],"busServices":[{"std":"00:05"},{"std":"23:55"}]}"""
        )
        assertEquals(listOf("23:50", "23:55", "00:05", "00:20"), board.allServices.map { it.std })
        assertTrue(board.allServices[1].isBus)
    }

    @Test
    fun buildsUrlWithFilter() {
        assertEquals(
            "https://example.org/api/GetDepartureBoard/RDG?numRows=20&filterCrs=PAD&filterType=to",
            LdbwsClient.departureBoardUrl("https://example.org/api/", "rdg", "pad", 20),
        )
        assertEquals(
            "https://example.org/api/GetDepartureBoard/RDG?numRows=10",
            LdbwsClient.departureBoardUrl("https://example.org/api", "RDG", " ", 10),
        )
    }

    @Test
    fun fullStationListLoads() {
        assertTrue(Stations.all.size > 2500)
        assertEquals(Stations.all.size, Stations.all.map { it.crs }.toSet().size)
        assertEquals("Abbey Wood", Stations.nameFor("abw"))
        assertEquals("Kilmarnock", Stations.nameFor("KMK"))
    }

    @Test
    fun stationSearch() {
        assertEquals("PAD", Stations.search("pad").first().crs)
        assertEquals("Reading", Stations.search("Read").first().name)
        // Punctuation-insensitive, and matches on any word in the name.
        assertEquals("KGX", Stations.search("king's cross").first().crs)
        assertTrue(Stations.search("kings cross", limit = 10).any { it.crs == "KGX" })
        assertTrue(Stations.search("waverley", limit = 10).any { it.crs == "EDB" })
        // A 3-letter query that's a real code puts that station first, even if names also match.
        assertEquals("ELY", Stations.search("ely").first().crs)
        assertTrue(Stations.search("zzzzz").isEmpty())
    }
}
