package com.bitchat.android.connect

import java.security.MessageDigest
import java.util.UUID

/**
 * Counts nearby devices running bitchat, the mesh app Locus is forked from. It only counts:
 * devices are seen in the existing scan, never connected to, and nothing is sent to them.
 *
 * Identifiers are hashed and held in memory for a 15-minute rolling window, per foreground session.
 * The only output is a bucketed peak count, logged as an analytics event when the app goes to the
 * background.
 */
object BitchatCensus {

    val SERVICE_UUID: UUID = UUID.fromString("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C")

    data class Summary(val scanned: Boolean, val peak: Int)

    internal class Tally(
        private val windowMs: Long = 15 * 60_000L,
        private val throttleMs: Long = 10_000L,
    ) {
        private val lastSeen = HashMap<String, Long>()
        private val peerByAddress = HashMap<String, String>()
        private val lastHandled = HashMap<String, Long>()
        private var peak = 0
        private var scanned = false
        private var scanActive = false

        @Synchronized
        fun sighting(id: String, nowMs: Long) {
            lastSeen[id] = nowMs
            prune(nowMs)
            peak = maxOf(peak, lastSeen.size)
        }

        /**
         * One advertising callback. Repeats from the same address within [throttleMs] are dropped
         * before any hashing, unless they newly carry the peer id. A device seen first by address
         * and later with its peer id (or the other way round) is counted once.
         */
        @Synchronized
        fun sighting(serviceData: ByteArray?, address: String, nowMs: Long) {
            val hasPeer = serviceData != null && serviceData.size >= 8
            val last = lastHandled[address]
            if (last != null && nowMs - last < throttleMs && (!hasPeer || address in peerByAddress)) return
            lastHandled[address] = nowMs
            val id = if (hasPeer) {
                idFor(serviceData, address).also {
                    peerByAddress[address] = it
                    lastSeen.remove(idFor(null, address))
                }
            } else {
                peerByAddress[address] ?: idFor(null, address)
            }
            sighting(id, nowMs)
        }

        /** Start a fresh foreground session: forget earlier sightings, keep the scan's running state. */
        @Synchronized
        fun beginSession(@Suppress("UNUSED_PARAMETER") nowMs: Long) {
            lastSeen.clear()
            peerByAddress.clear()
            lastHandled.clear()
            peak = 0
            scanned = false
        }

        private fun prune(nowMs: Long) {
            lastSeen.values.removeAll { nowMs - it > windowMs }
            val stale = lastHandled.filterValues { nowMs - it > windowMs }.keys
            stale.forEach { lastHandled.remove(it); peerByAddress.remove(it) }
        }

        @Synchronized
        fun markScanning() {
            scanned = true
            scanActive = true
        }

        @Synchronized
        fun markScanStopped() {
            if (scanActive) scanned = true
            scanActive = false
        }

        @Synchronized
        fun flush(nowMs: Long): Summary? {
            prune(nowMs)
            val ran = scanned || scanActive
            if (!ran && peak == 0) return null
            val summary = Summary(scanned = ran, peak = peak)
            peak = 0
            scanned = false
            return summary
        }
    }

    private val tally = Tally()

    fun isBitchatOnly(uuids: List<UUID>?, locus: UUID): Boolean =
        uuids != null && SERVICE_UUID in uuids && locus !in uuids

    /** A stable, non-reversible id: the advertised peer id when present (8+ bytes), else the address. */
    fun idFor(serviceData: ByteArray?, address: String): String {
        val source = if (serviceData != null && serviceData.size >= 8) {
            byteArrayOf(1) + serviceData
        } else {
            byteArrayOf(0) + address.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) }
    }

    fun peakBucket(peak: Int): String? = when {
        peak <= 0 -> null
        peak == 1 -> "1"
        peak <= 3 -> "2-3"
        peak <= 9 -> "4-9"
        else -> "10+"
    }

    fun events(summary: Summary?): List<Pair<String, Map<String, String>>> {
        if (summary == null) return emptyList()
        val out = mutableListOf<Pair<String, Map<String, String>>>(Metrics.BITCHAT_SCAN_SESSION to emptyMap())
        peakBucket(summary.peak)?.let { out += Metrics.BITCHAT_NEARBY to mapOf("peak" to it) }
        return out
    }

    fun sighting(serviceData: ByteArray?, address: String, nowMs: Long = System.currentTimeMillis()) =
        tally.sighting(serviceData, address, nowMs)

    fun beginSession(nowMs: Long = System.currentTimeMillis()) = tally.beginSession(nowMs)

    fun markScanning() = tally.markScanning()

    fun markScanStopped() = tally.markScanStopped()

    fun flush(nowMs: Long = System.currentTimeMillis()): Summary? = tally.flush(nowMs)
}
