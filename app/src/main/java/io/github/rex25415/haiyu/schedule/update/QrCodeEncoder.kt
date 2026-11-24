package io.github.rex25415.haiyu.schedule.update

/**
 * 轻量级纯 Kotlin ISO/IEC 18004（纠错等级 M · Byte 模式，支持 Version 1–6）离线二维码编码器。
 * 零第三方库依赖、极小体积，用于在分享弹窗内离线生成高清可扫描的下载页二维码。
 */
data class QrMatrix(
    val size: Int,
    val modules: BooleanArray
) {
    fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return size == other.size && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}

object QrCodeEncoder {

    private data class VersionSpec(
        val version: Int,
        val totalDataBytes: Int,
        val ecBytesPerBlock: Int,
        val blockCount: Int,
        val alignmentCoords: IntArray,
        val remainderBits: Int
    )

    // ISO/IEC 18004 Level M (纠错码字占比 ~15%)，Versions 1..6 均为等长单组块
    private val LEVEL_M_SPECS = listOf(
        VersionSpec(1, 16, 10, 1, intArrayOf(), 0),
        VersionSpec(2, 28, 16, 1, intArrayOf(6, 18), 7),
        VersionSpec(3, 44, 26, 1, intArrayOf(6, 22), 7),
        VersionSpec(4, 64, 18, 2, intArrayOf(6, 26), 7),
        VersionSpec(5, 86, 24, 2, intArrayOf(6, 30), 7),
        VersionSpec(6, 108, 16, 4, intArrayOf(6, 34), 7)
    )

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    fun encodeByteModeM(text: String): QrMatrix? {
        val payload = text.toByteArray(Charsets.UTF_8)
        // Byte mode: 4-bit mode + 8-bit length + payload + 4-bit terminator <= totalDataBytes * 8
        val spec = LEVEL_M_SPECS.firstOrNull { 4 + 8 + payload.size * 8 + 4 <= it.totalDataBytes * 8 }
            ?: return null

        val dataBytes = buildPaddedDataBytes(payload, spec.totalDataBytes)
        val finalStream = buildInterleavedBitStream(dataBytes, spec)

        val size = 17 + 4 * spec.version
        val isFunction = BooleanArray(size * size)
        val baseModules = BooleanArray(size * size)
        placeFunctionPatterns(spec, size, baseModules, isFunction)
        placeDataBits(size, baseModules, isFunction, finalStream)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules = baseModules
        for (mask in 0 until 8) {
            val candidate = applyMaskAndFormat(size, baseModules, isFunction, mask)
            val penalty = evaluatePenalty(size, candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestModules = candidate
            }
        }
        return QrMatrix(size = size, modules = bestModules)
    }

    private fun buildPaddedDataBytes(payload: ByteArray, totalDataBytes: Int): IntArray {
        val bits = ArrayList<Int>(totalDataBytes * 8)
        appendBits(bits, 0b0100, 4) // Byte mode indicator
        appendBits(bits, payload.size, 8)
        for (b in payload) appendBits(bits, b.toInt() and 0xFF, 8)

        val maxBits = totalDataBytes * 8
        val termLen = minOf(4, maxBits - bits.size)
        repeat(termLen) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val result = IntArray(totalDataBytes)
        var byteCount = bits.size / 8
        for (i in 0 until byteCount) {
            var v = 0
            for (bit in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + bit]
            }
            result[i] = v
        }
        var padIndex = 0
        while (byteCount < totalDataBytes) {
            result[byteCount++] = if (padIndex++ % 2 == 0) 0xEC else 0x11
        }
        return result
    }

    private fun buildInterleavedBitStream(dataBytes: IntArray, spec: VersionSpec): BooleanArray {
        val blockDataLen = spec.totalDataBytes / spec.blockCount
        val ecLen = spec.ecBytesPerBlock
        val dataBlocks = Array(spec.blockCount) { i ->
            dataBytes.copyOfRange(i * blockDataLen, (i + 1) * blockDataLen)
        }
        val ecBlocks = Array(spec.blockCount) { i ->
            computeReedSolomonEc(dataBlocks[i], ecLen)
        }

        val totalCodewords = spec.totalDataBytes + spec.blockCount * ecLen
        val totalBits = totalCodewords * 8 + spec.remainderBits
        val out = BooleanArray(totalBits)
        var bitPos = 0

        for (col in 0 until blockDataLen) {
            for (b in 0 until spec.blockCount) {
                val cw = dataBlocks[b][col]
                for (shift in 7 downTo 0) out[bitPos++] = ((cw shr shift) and 1) == 1
            }
        }
        for (col in 0 until ecLen) {
            for (b in 0 until spec.blockCount) {
                val cw = ecBlocks[b][col]
                for (shift in 7 downTo 0) out[bitPos++] = ((cw shr shift) and 1) == 1
            }
        }
        return out
    }

    private fun computeReedSolomonEc(data: IntArray, ecCount: Int): IntArray {
        // Generator polynomial (x - alpha^0)...(x - alpha^(ecCount-1))
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val next = IntArray(gen.size + 1)
            val root = GF_EXP[i]
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], root)
            }
            gen = next
        }

        val rem = IntArray(ecCount)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecCount - 1)
            rem[ecCount - 1] = 0
            if (factor != 0) {
                for (j in 0 until ecCount) {
                    rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
                }
            }
        }
        return rem
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun placeFunctionPatterns(
        spec: VersionSpec,
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray
    ) {
        fun setFn(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y * size + x] = dark
                isFunction[y * size + x] = true
            }
        }

        // Three finder patterns + 1-module white separator
        val finderOrigins = listOf(0 to 0, size - 7 to 0, 0 to size - 7)
        for ((ox, oy) in finderOrigins) {
            for (dy in -1..7) {
                for (dx in -1..7) {
                    val dark = if (dx in 0..6 && dy in 0..6) {
                        val ring = maxOf(kotlin.math.abs(dx - 3), kotlin.math.abs(dy - 3))
                        ring != 2
                    } else {
                        false
                    }
                    setFn(ox + dx, oy + dy, dark)
                }
            }
        }

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            setFn(i, 6, dark)
            setFn(6, i, dark)
        }

        // Alignment patterns (skip corners overlapping finder patterns)
        val coords = spec.alignmentCoords
        for (ay in coords) {
            for (ax in coords) {
                val overlapsTopLeft = ax <= 8 && ay <= 8
                val overlapsTopRight = ax >= size - 9 && ay <= 8
                val overlapsBottomLeft = ax <= 8 && ay >= size - 9
                if (overlapsTopLeft || overlapsTopRight || overlapsBottomLeft) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val ring = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        setFn(ax + dx, ay + dy, ring != 1)
                    }
                }
            }
        }

        // Dark module
        setFn(8, 4 * spec.version + 9, true)

        // Reserve format info strips
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8 * size + i] = true
                isFunction[i * size + 8] = true
            }
        }
        for (i in 0 until 7) isFunction[(size - 1 - i) * size + 8] = true
        for (i in 0 until 8) isFunction[8 * size + (size - 8 + i)] = true
    }

    private fun placeDataBits(
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray,
        stream: BooleanArray
    ) {
        var idx = 0
        var upward = true
        var rightCol = size - 1
        while (rightCol >= 1) {
            if (rightCol == 6) rightCol--
            for (step in 0 until size) {
                val y = if (upward) size - 1 - step else step
                for (dx in 0..1) {
                    val x = rightCol - dx
                    if (!isFunction[y * size + x]) {
                        modules[y * size + x] = idx < stream.size && stream[idx++]
                    }
                }
            }
            rightCol -= 2
            upward = !upward
        }
    }

    private fun applyMaskAndFormat(
        size: Int,
        baseModules: BooleanArray,
        isFunction: BooleanArray,
        mask: Int
    ): BooleanArray {
        val result = baseModules.copyOf()
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y * size + x] && shouldFlipMask(mask, x, y)) {
                    result[y * size + x] = !result[y * size + x]
                }
            }
        }

        // Format info for ECC Level M (00) + 3-bit mask -> 15 bits with BCH(15,5) + XOR 0x5412
        val dataBits = (0b00 shl 3) or mask
        var rem = dataBits shl 10
        for (bit in 14 downTo 10) {
            if ((rem shr bit) and 1 != 0) rem = rem xor (0x537 shl (bit - 10))
        }
        val formatBits = ((dataBits shl 10) or rem) xor 0x5412

        // Bit 14 (MSB) .. Bit 0 (LSB)
        fun fBit(posFromLsb: Int): Boolean = ((formatBits shr posFromLsb) and 1) == 1

        // Around top-left finder
        val topLeftRows = intArrayOf(0, 1, 2, 3, 4, 5, 7, 8, 8, 8, 8, 8, 8, 8, 8)
        val topLeftCols = intArrayOf(8, 8, 8, 8, 8, 8, 8, 8, 7, 5, 4, 3, 2, 1, 0)
        for (k in 0 until 15) {
            result[topLeftRows[k] * size + topLeftCols[k]] = fBit(k)
        }
        // Bottom-left + top-right copies
        for (k in 0 until 7) {
            result[(size - 1 - k) * size + 8] = fBit(14 - k)
        }
        for (k in 0 until 8) {
            result[8 * size + (size - 8 + k)] = fBit(7 - k)
        }
        return result
    }

    private fun shouldFlipMask(mask: Int, x: Int, y: Int): Boolean = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> ((y / 2) + (x / 3)) % 2 == 0
        5 -> (x * y) % 2 + (x * y) % 3 == 0
        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
        7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
        else -> false
    }

    private fun evaluatePenalty(size: Int, m: BooleanArray): Int {
        var score = 0
        // N1: 5+ same-color runs horizontally & vertically
        for (y in 0 until size) {
            var runColor = m[y * size]
            var runLen = 1
            for (x in 1 until size) {
                val c = m[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }
        for (x in 0 until size) {
            var runColor = m[x]
            var runLen = 1
            for (y in 1 until size) {
                val c = m[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) score += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) score += 3 + (runLen - 5)
        }

        // N2: 2x2 uniform blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = m[y * size + x]
                if (c == m[y * size + x + 1] &&
                    c == m[(y + 1) * size + x] &&
                    c == m[(y + 1) * size + x + 1]
                ) {
                    score += 3
                }
            }
        }

        // N3: finder-like 1:1:3:1:1 patterns
        fun hasFinderPatternAt(seq: BooleanArray, start: Int): Boolean {
            // 1011101 + 0000 or 0000 + 1011101
            val pat = booleanArrayOf(true, false, true, true, true, false, true)
            for (i in 0 until 7) if (seq[start + i] != pat[i]) return false
            val whiteBefore = start >= 4 && !seq[start - 1] && !seq[start - 2] && !seq[start - 3] && !seq[start - 4]
            val whiteAfter = start + 10 < seq.size && !seq[start + 7] && !seq[start + 8] && !seq[start + 9] && !seq[start + 10]
            return whiteBefore || whiteAfter
        }

        val rowBuf = BooleanArray(size)
        val colBuf = BooleanArray(size)
        for (i in 0 until size) {
            for (j in 0 until size) {
                rowBuf[j] = m[i * size + j]
                colBuf[j] = m[j * size + i]
            }
            for (s in 0..size - 7) {
                if (hasFinderPatternAt(rowBuf, s)) score += 40
                if (hasFinderPatternAt(colBuf, s)) score += 40
            }
        }

        // N4: dark ratio balance
        val darkCount = m.count { it }
        val pct = darkCount * 100 / (size * size)
        val prev5 = (pct / 5) * 5
        val next5 = prev5 + 5
        score += minOf(kotlin.math.abs(prev5 - 50), kotlin.math.abs(next5 - 50)) / 5 * 10
        return score
    }

    private fun appendBits(dest: MutableList<Int>, value: Int, bitCount: Int) {
        for (shift in bitCount - 1 downTo 0) {
            dest.add((value shr shift) and 1)
        }
    }
}
