package com.hyperscopic.motix.data

/**
 * A small QR Code encoder (byte mode, error correction M, versions 1-40), written from the QR spec so Mo-Tix can
 * show a real, scannable ticket code without a third-party library. The algorithm was checked against
 * the OpenCV decoder.
 */
internal object QrCode {
    private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28)
    private val NUM_BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49)

    /** The modules of a QR code for [text] ([y][x], true = dark), without the quiet zone. */
    fun encode(text: String): Array<BooleanArray> {
        val payload = text.toByteArray(Charsets.UTF_8)
        var ver = 1
        while (true) {
            require(ver <= 40) { "Text too long for a QR code" }
            val countBits = if (ver <= 9) 8 else 16
            if (4 + countBits + 8 * payload.size <= dataCodewords(ver) * 8) break
            ver++
        }
        val data = dataBytes(ver, payload)
        val codewords = interleave(ver, data)
        var best: Grid? = null
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0 until 8) {
            val g = Grid(ver)
            g.functionPatterns()
            g.codewords(codewords)
            g.applyMask(mask)
            g.formatBits(mask)
            val p = g.penalty()
            if (p < bestPenalty) { bestPenalty = p; best = g }
        }
        return best!!.mod
    }

    private fun rawModules(ver: Int): Int {
        var r = (16 * ver + 128) * ver + 64
        if (ver >= 2) {
            val n = ver / 7 + 2
            r -= (25 * n - 10) * n - 55
            if (ver >= 7) r -= 36
        }
        return r
    }

    private fun dataCodewords(ver: Int) = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver]

    private fun dataBytes(ver: Int, payload: ByteArray): IntArray {
        val bits = ArrayList<Int>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 }
        put(0b0100, 4)
        put(payload.size, if (ver <= 9) 8 else 16)
        for (b in payload) put(b.toInt() and 0xFF, 8)
        val cap = dataCodewords(ver) * 8
        put(0, minOf(4, cap - bits.size))
        put(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < cap) { put(pad, 8); pad = pad xor 0xEC xor 0x11 }
        return IntArray(bits.size / 8) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or bits[i * 8 + j] } }
    }

    private fun gfMul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree).also { it[degree - 1] = 1 }
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = gfMul(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMul(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: List<Int>, divisor: IntArray): List<Int> {
        val result = ArrayDeque(List(divisor.size) { 0 })
        for (b in data) {
            val factor = b xor result.removeFirst()
            result.addLast(0)
            for (i in divisor.indices) result[i] = result[i] xor gfMul(divisor[i], factor)
        }
        return result.toList()
    }

    private fun interleave(ver: Int, data: IntArray): List<Int> {
        val nBlocks = NUM_BLOCKS[ver]
        val eccLen = ECC_PER_BLOCK[ver]
        val raw = rawModules(ver) / 8
        val nShort = nBlocks - raw % nBlocks
        val shortLen = raw / nBlocks
        val div = rsDivisor(eccLen)
        val blocks = ArrayList<List<Int>>()
        var k = 0
        for (i in 0 until nBlocks) {
            val len = shortLen - eccLen + if (i < nShort) 0 else 1
            val dat = data.slice(k until k + len)
            k += len
            val ecc = rsRemainder(dat, div)
            blocks += (if (i < nShort) dat + 0 else dat) + ecc
        }
        val out = ArrayList<Int>()
        for (i in blocks[0].indices) for ((j, blk) in blocks.withIndex()) {
            if (i != shortLen - eccLen || j >= nShort) out += blk[i]
        }
        return out
    }

    private class Grid(val ver: Int) {
        val size = 17 + 4 * ver
        val mod = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }

        fun setF(x: Int, y: Int, dark: Boolean) { mod[y][x] = dark; fn[y][x] = true }

        fun alignPositions(): List<Int> {
            if (ver == 1) return emptyList()
            val n = ver / 7 + 2
            val step = (ver * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            return ((0 until n - 1).map { size - 7 - it * step } + 6).reversed()
        }

        fun functionPatterns() {
            for (i in 0 until size) { setF(6, i, i % 2 == 0); setF(i, 6, i % 2 == 0) }
            for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) setF(x, y, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) !in setOf(2, 4))
                }
            }
            val pos = alignPositions()
            val n = pos.size
            for (i in 0 until n) for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) setF(pos[i] + dx, pos[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            formatBits(0)
            versionBits()
        }

        fun formatBits(mask: Int) {
            val data = (0 shl 3) or mask // level M = 0b00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) setF(8, i, bit(i))
            setF(8, 7, bit(6)); setF(8, 8, bit(7)); setF(7, 8, bit(8))
            for (i in 9..14) setF(14 - i, 8, bit(i))
            for (i in 0..7) setF(size - 1 - i, 8, bit(i))
            for (i in 8..14) setF(8, size - 15 + i, bit(i))
            setF(8, size - 8, true)
        }

        fun versionBits() {
            if (ver < 7) return
            var rem = ver
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (ver shl 12) or rem
            for (i in 0 until 18) {
                val b = (bits ushr i) and 1 != 0
                val a = size - 11 + i % 3
                val c = i / 3
                setF(a, c, b); setF(c, a, b)
            }
        }

        fun codewords(data: List<Int>) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!fn[y][x] && i < data.size * 8) {
                        mod[y][x] = (data[i ushr 3] ushr (7 - (i and 7))) and 1 != 0
                        i++
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val m = when (mask) {
                    0 -> (x + y) % 2
                    1 -> y % 2
                    2 -> x % 3
                    3 -> (x + y) % 3
                    4 -> (x / 3 + y / 2) % 2
                    5 -> x * y % 2 + x * y % 3
                    6 -> (x * y % 2 + x * y % 3) % 2
                    else -> ((x + y) % 2 + x * y % 3) % 2
                }
                if (!fn[y][x] && m == 0) mod[y][x] = !mod[y][x]
            }
        }

        fun penalty(): Int {
            var p = 0
            val lines = (0 until size).map { y -> BooleanArray(size) { x -> mod[y][x] } } +
                (0 until size).map { x -> BooleanArray(size) { y -> mod[y][x] } }
            for (line in lines) {
                var run = 0
                var prev: Boolean? = null
                for (v in line) {
                    if (v == prev) run++ else { if (run >= 5) p += run - 2; run = 1; prev = v }
                }
                if (run >= 5) p += run - 2
                val s = line.joinToString("") { if (it) "1" else "0" }
                for (pat in listOf("10111010000", "00001011101")) p += 40 * s.windowed(pat.length).count { it == pat }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = mod[y][x]
                if (c == mod[y][x + 1] && c == mod[y + 1][x] && c == mod[y + 1][x + 1]) p += 3
            }
            val dark = mod.sumOf { row -> row.count { it } }
            p += kotlin.math.abs(dark * 20 - size * size * 10) / (size * size) * 10
            return p
        }
    }
}
