package com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy

import java.io.IOException

/**
 * Reader for Microsoft's Compound File Binary format (the "OLE2" container of .doc, .xls and .ppt).
 * Reads the whole file from memory; every chain walk is bounded so a corrupt or malicious file
 * cannot loop forever or index outside the data.
 */
class Cfb(private val data: ByteArray) {

    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Long
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries: List<Entry>
    private val miniStream: ByteArray

    class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    init {
        if (data.size < HEADER || u32(0) != SIGNATURE_LO || u32(4) != SIGNATURE_HI) throw IOException("Not a compound file")
        val shift = u16(0x1E)
        if (shift != 9 && shift != 12) throw IOException("Bad sector size")
        sectorSize = 1 shl shift
        miniSectorSize = 1 shl u16(0x20).coerceIn(6, 12)
        miniCutoff = u32(0x38).toLong() and 0xFFFFFFFFL
        fat = readFat()
        entries = readDirectory()
        val root = entries.firstOrNull { it.type == ROOT } ?: throw IOException("No root entry")
        miniStream = readChain(root.start, root.size, fat, sectorSize, ::sectorOffset, data)
        miniFat = readMiniFat()
    }

    /** A stream by name (first match, case-insensitive), or null. */
    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type == STREAM && it.name.equals(name, ignoreCase = true) } ?: return null
        return if (entry.size < miniCutoff) {
            readChain(entry.start, entry.size, miniFat, miniSectorSize, { it.toLong() * miniSectorSize }, miniStream)
        } else {
            readChain(entry.start, entry.size, fat, sectorSize, ::sectorOffset, data)
        }
    }

    fun names(): List<String> = entries.filter { it.type == STREAM }.map { it.name }

    private fun sectorOffset(sector: Int): Long = (sector.toLong() + 1) * sectorSize

    private fun readFat(): IntArray {
        val fatSectors = mutableListOf<Int>()
        for (i in 0 until DIFAT_IN_HEADER) {
            val s = u32(0x4C + i * 4)
            if (s >= 0) fatSectors += s
        }
        var difat = u32(0x44)
        var guard = 0
        val perSector = sectorSize / 4 - 1
        while (difat >= 0 && guard++ < MAX_CHAIN) {
            val base = sectorOffset(difat)
            if (base + sectorSize > data.size) break
            for (i in 0 until perSector) {
                val s = u32(base.toInt() + i * 4)
                if (s >= 0) fatSectors += s
            }
            difat = u32(base.toInt() + perSector * 4)
        }
        val perFatSector = sectorSize / 4
        val table = IntArray(fatSectors.size * perFatSector) { FREE }
        fatSectors.forEachIndexed { n, sector ->
            val base = sectorOffset(sector)
            if (base + sectorSize > data.size) return@forEachIndexed
            for (i in 0 until perFatSector) table[n * perFatSector + i] = u32(base.toInt() + i * 4)
        }
        return table
    }

    private fun readDirectory(): List<Entry> {
        val bytes = readChain(u32(0x30), Long.MAX_VALUE, fat, sectorSize, ::sectorOffset, data)
        val list = mutableListOf<Entry>()
        var offset = 0
        while (offset + DIR_ENTRY <= bytes.size) {
            val nameLength = le16(bytes, offset + 64).coerceIn(0, 64)
            val name = if (nameLength >= 2) String(bytes, offset, nameLength - 2, Charsets.UTF_16LE) else ""
            val type = bytes[offset + 66].toInt() and 0xFF
            val start = le32(bytes, offset + 116)
            val size = le32(bytes, offset + 120).toLong() and 0xFFFFFFFFL
            if (type != 0) list += Entry(name, type, start, size)
            offset += DIR_ENTRY
        }
        return list
    }

    private fun readMiniFat(): IntArray {
        val start = u32(0x3C)
        if (start < 0) return IntArray(0)
        val bytes = readChain(start, Long.MAX_VALUE, fat, sectorSize, ::sectorOffset, data)
        return IntArray(bytes.size / 4) { le32(bytes, it * 4) }
    }

    private fun u16(offset: Int) = le16(data, offset)
    private fun u32(offset: Int) = le32(data, offset)

    companion object {
        private const val HEADER = 512
        private const val SIGNATURE_LO = 0xE011CFD0.toInt()
        private const val SIGNATURE_HI = 0xE11AB1A1.toInt()
        private const val DIFAT_IN_HEADER = 109
        private const val DIR_ENTRY = 128
        private const val MAX_CHAIN = 1_000_000
        private const val FREE = -1
        const val STREAM = 2
        const val ROOT = 5

        fun isCompoundFile(bytes: ByteArray): Boolean =
            bytes.size >= 8 && le32(bytes, 0) == SIGNATURE_LO && le32(bytes, 4) == SIGNATURE_HI

        /** Follows [start] through [table], copying [unit]-sized blocks, up to [size] bytes. */
        private fun readChain(
            start: Int,
            size: Long,
            table: IntArray,
            unit: Int,
            offsetOf: (Int) -> Long,
            source: ByteArray,
        ): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var sector = start
            var guard = 0
            val seen = HashSet<Int>()
            while (sector >= 0 && guard++ < MAX_CHAIN && out.size() < size && seen.add(sector)) {
                val offset = offsetOf(sector)
                if (offset < 0 || offset >= source.size) break
                val length = minOf(unit.toLong(), source.size - offset, size - out.size()).toInt()
                out.write(source, offset.toInt(), length)
                sector = if (sector < table.size) table[sector] else -1
            }
            return out.toByteArray()
        }

        fun le16(bytes: ByteArray, offset: Int): Int =
            if (offset + 1 >= bytes.size || offset < 0) 0
            else (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

        fun le32(bytes: ByteArray, offset: Int): Int =
            if (offset + 3 >= bytes.size || offset < 0) -1
            else (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        fun le64(bytes: ByteArray, offset: Int): Long =
            (le32(bytes, offset).toLong() and 0xFFFFFFFFL) or (le32(bytes, offset + 4).toLong() shl 32)
    }
}
