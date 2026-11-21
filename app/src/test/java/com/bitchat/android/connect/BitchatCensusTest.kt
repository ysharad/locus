package com.bitchat.android.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BitchatCensusTest {

    private val min = 60_000L
    private val locus = UUID.fromString("E7DE54D6-E03B-41C8-81F2-F572EC2C8A03")

    @Test
    fun `repeat sightings of one id peak at 1`() {
        val t = BitchatCensus.Tally()
        repeat(5) { t.sighting("a", it * 1000L) }
        assertEquals(1, t.flush(10_000L)!!.peak)
    }

    @Test
    fun `three ids within window peak at 3`() {
        val t = BitchatCensus.Tally()
        t.sighting("a", 0); t.sighting("b", 1 * min); t.sighting("c", 2 * min)
        assertEquals(3, t.flush(3 * min)!!.peak)
    }

    @Test
    fun `id older than 15 min is pruned`() {
        val t = BitchatCensus.Tally()
        t.sighting("a", 0); t.sighting("b", 16 * min)
        assertEquals(1, t.flush(16 * min)!!.peak)
    }

    @Test
    fun `flush with nothing returns null`() {
        assertNull(BitchatCensus.Tally().flush(0))
    }

    @Test
    fun `flush after markScanning only returns scanned with peak 0`() {
        val t = BitchatCensus.Tally()
        t.markScanning()
        t.markScanStopped()
        assertEquals(BitchatCensus.Summary(scanned = true, peak = 0), t.flush(0))
        assertNull(t.flush(1))
    }

    @Test
    fun `scan still running at flush counts the next session too`() {
        val t = BitchatCensus.Tally()
        t.markScanning()
        assertNotNull(t.flush(0))
        assertEquals(BitchatCensus.Summary(scanned = true, peak = 0), t.flush(1))
        t.markScanStopped()
        assertEquals(BitchatCensus.Summary(scanned = true, peak = 0), t.flush(2))
        assertNull(t.flush(3))
    }

    private val peer = ByteArray(8) { (it + 1).toByte() }

    @Test
    fun `sightings before beginSession are not counted`() {
        val t = BitchatCensus.Tally()
        t.sighting(null, "AA", 0)
        t.beginSession(1)
        assertNull(t.flush(2))
    }

    @Test
    fun `beginSession keeps a running scan`() {
        val t = BitchatCensus.Tally()
        t.markScanning()
        t.flush(0)
        t.beginSession(1)
        assertEquals(BitchatCensus.Summary(scanned = true, peak = 0), t.flush(2))
    }

    @Test
    fun `address then peer id for the same device counts once`() {
        val t = BitchatCensus.Tally()
        t.sighting(null, "AA", 0)
        t.sighting(peer, "AA", 1_000)
        assertEquals(1, t.flush(2_000)!!.peak)
    }

    @Test
    fun `peer id then address-only result for the same device counts once`() {
        val t = BitchatCensus.Tally()
        t.sighting(peer, "AA", 0)
        t.sighting(null, "AA", 20_000)
        assertEquals(1, t.flush(21_000)!!.peak)
    }

    @Test
    fun `two devices with peer ids count twice`() {
        val t = BitchatCensus.Tally()
        t.sighting(peer, "AA", 0)
        t.sighting(ByteArray(8) { 9 }, "BB", 0)
        assertEquals(2, t.flush(1)!!.peak)
    }

    @Test
    fun `repeat callbacks within 10s are ignored`() {
        val t = BitchatCensus.Tally()
        t.sighting(null, "AA", 0)
        t.sighting(null, "AA", 9_000) // throttled: AA's last-seen stays at 0
        t.sighting(null, "BB", 905_000) // AA is now older than 15 min
        assertEquals(1, t.flush(905_000)!!.peak)
    }

    @Test
    fun `flush resets peak`() {
        val t = BitchatCensus.Tally()
        t.sighting("a", 0); t.sighting("b", 0)
        assertEquals(2, t.flush(0)!!.peak)
        t.sighting("c", 0)
        assertEquals(3, t.flush(0)!!.peak)
    }

    @Test
    fun buckets() {
        assertNull(BitchatCensus.peakBucket(0))
        assertEquals("1", BitchatCensus.peakBucket(1))
        assertEquals("2-3", BitchatCensus.peakBucket(2))
        assertEquals("2-3", BitchatCensus.peakBucket(3))
        assertEquals("4-9", BitchatCensus.peakBucket(4))
        assertEquals("4-9", BitchatCensus.peakBucket(9))
        assertEquals("10+", BitchatCensus.peakBucket(10))
        assertEquals("10+", BitchatCensus.peakBucket(50))
    }

    @Test
    fun events() {
        assertTrue(BitchatCensus.events(null).isEmpty())
        assertEquals(
            listOf(Metrics.BITCHAT_SCAN_SESSION to emptyMap<String, String>()),
            BitchatCensus.events(BitchatCensus.Summary(scanned = true, peak = 0)),
        )
        assertEquals(
            listOf(
                Metrics.BITCHAT_SCAN_SESSION to emptyMap(),
                Metrics.BITCHAT_NEARBY to mapOf("peak" to "2-3"),
            ),
            BitchatCensus.events(BitchatCensus.Summary(scanned = true, peak = 3)),
        )
        assertEquals("bitchat_nearby", Metrics.BITCHAT_NEARBY)
        assertEquals("bitchat_scan_session", Metrics.BITCHAT_SCAN_SESSION)
    }

    @Test
    fun isBitchatOnly() {
        val b = BitchatCensus.SERVICE_UUID
        assertEquals(UUID.fromString("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C"), b)
        assertFalse(BitchatCensus.isBitchatOnly(null, locus))
        assertTrue(BitchatCensus.isBitchatOnly(listOf(b), locus))
        assertFalse(BitchatCensus.isBitchatOnly(listOf(b, locus), locus))
        assertFalse(BitchatCensus.isBitchatOnly(listOf(locus), locus))
    }

    @Test
    fun idFor() {
        val addr = "AA:BB:CC:DD:EE:FF"
        assertEquals(BitchatCensus.idFor(null, addr), BitchatCensus.idFor(byteArrayOf(1, 2, 3, 4), addr))
        val data = ByteArray(8) { it.toByte() }
        assertEquals(BitchatCensus.idFor(data, "x"), BitchatCensus.idFor(data.copyOf(), "y"))
        assertNotEquals(addr, BitchatCensus.idFor(null, addr))
        assertNotEquals(BitchatCensus.idFor(data, addr), BitchatCensus.idFor(null, addr))
    }

    @Test
    fun `concurrent sightings do not throw`() {
        val t = BitchatCensus.Tally()
        val threads = (0 until 8).map { n ->
            Thread { repeat(1000) { t.sighting("id-$n-${it % 20}", System.currentTimeMillis()) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertNotNull(t.flush(System.currentTimeMillis()))
    }
}
