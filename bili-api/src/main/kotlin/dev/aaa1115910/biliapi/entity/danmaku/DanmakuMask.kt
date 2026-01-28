package dev.aaa1115910.biliapi.entity.danmaku

import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.GzipSource
import okio.buffer

data class DanmakuMaskSegment(
    val range: LongRange,
    val frames: List<DanmakuMaskFrame>,
)

sealed class DanmakuMaskFrame(
    open val range: LongRange
)

data class DanmakuWebMaskFrame(
    override val range: LongRange,
    val svg: String
) : DanmakuMaskFrame(range)

data class DanmakuMobMaskFrame(
    override val range: LongRange,
    val width: Int,
    val height: Int,
    val image: ByteArray
) : DanmakuMaskFrame(range) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DanmakuMobMaskFrame

        if (range != other.range) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!image.contentEquals(other.image)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = range.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + image.contentHashCode()
        return result
    }
}

data class DanmakuMask(
    val type: DanmakuMaskType,
    val segments: List<DanmakuMaskSegment>
) {
    companion object {
        fun fromBinary(binary: ByteArray, type: DanmakuMaskType): DanmakuMask {
            val source = Buffer().write(binary)

            val mask = source.readByteString(4)
            require(mask.utf8() == "MASK") { "Not a mask file" }

            val version = source.readInt()
            source.skip(4) // unused
            val size = source.readInt()

            val times = LongArray(size)
            val offsets = LongArray(size)

            for (i in 0 until size) {
                times[i] = source.readLong()
                offsets[i] = source.readLong()
            }

            val danmakuMaskSegments = ArrayList<DanmakuMaskSegment>(size)
            var lastTime = 0L
            var segLastTime = 0L

            for (i in 0 until size) {
                val compressedSize = if (i == size - 1) {
                    source.size
                } else {
                    offsets[i + 1] - offsets[i]
                }


                val compressedBuffer = Buffer()
                source.read(compressedBuffer, compressedSize)

                val frameList = mutableListOf<DanmakuMaskFrame>()

                GzipSource(compressedBuffer).buffer().use { gzipStream ->
                    while (!gzipStream.exhausted()) {
                        when (type) {
                            DanmakuMaskType.WebMask -> {
                                val svgLength = gzipStream.readInt().toLong()
                                val time = gzipStream.readLong()

                                val fullSvgString = gzipStream.readUtf8(svgLength)

                                val commaIndex = fullSvgString.indexOf(',')
                                val base64Part = if (commaIndex != -1) {
                                    fullSvgString.substring(commaIndex + 1)
                                } else {
                                    fullSvgString
                                }

                                val cleanBase64 = base64Part.replace("\n", "")

                                val decodedSvg = cleanBase64.decodeBase64()?.utf8() ?: ""

                                frameList.add(
                                    DanmakuWebMaskFrame(
                                        range = lastTime until time,
                                        svg = decodedSvg
                                    )
                                )
                                lastTime = time
                            }

                            DanmakuMaskType.MobMask -> {
                                val width = gzipStream.readShort().toInt()
                                val height = gzipStream.readShort().toInt()
                                val time = gzipStream.readLong()

                                val imageBinary = gzipStream.readByteArray(7200)

                                frameList.add(
                                    DanmakuMobMaskFrame(
                                        range = lastTime until time,
                                        width = width,
                                        height = height,
                                        image = imageBinary
                                    )
                                )
                                lastTime = time
                            }
                        }
                    }
                }

                val startTime = segLastTime
                val endTime = if (i == size - 1) Long.MAX_VALUE else times[i + 1]
                danmakuMaskSegments.add(
                    DanmakuMaskSegment(range = startTime until endTime, frames = frameList)
                )
                segLastTime = endTime
            }

            return DanmakuMask(type, danmakuMaskSegments)
        }
    }
}

enum class DanmakuMaskType {
    WebMask, MobMask
}