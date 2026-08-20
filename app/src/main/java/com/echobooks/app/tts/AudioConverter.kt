package com.echobooks.app.tts

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

object AudioConverter {

    data class WavInfo(
        val file: File,
        val dataOffset: Int,
        val dataSize: Int,
        val sampleRate: Int,
        val channels: Int
    )

    fun wavToM4a(wav: File, out: File): Boolean = try {
        val info = parseWav(wav) ?: return false
        encode(info, out)
        true
    } catch (e: Exception) {
        false
    }

    fun wavDurationMs(wav: File): Long = try {
        val info = parseWav(wav) ?: return 0L
        info.dataSize * 1000L / (info.sampleRate * info.channels * 2)
    } catch (e: Exception) {
        0L
    }

    fun appendSilence(wav: File, extraMs: Long): Boolean {
        if (extraMs <= 0) return true
        val info = parseWav(wav) ?: return false
        val bytesToAdd = info.sampleRate * info.channels * 2 * extraMs / 1000
        if (bytesToAdd <= 0) return true
        return try {
            RandomAccessFile(wav, "rw").use { raf ->
                raf.seek(raf.length())
                val zero = ByteArray(bytesToAdd.toInt())
                raf.write(zero)
                // patch RIFF chunk size (bytes 4..8) and "data" chunk size at dataOffset-4
                raf.seek(4)
                raf.writeIntLE((raf.length() - 8).toInt())
                raf.seek((info.dataOffset - 4).toLong())
                raf.writeIntLE(info.dataSize + bytesToAdd.toInt())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseWav(f: File): WavInfo? {
        RandomAccessFile(f, "r").use { raf ->
            if (raf.length() < 44) return null
            val riff = ByteArray(4)
            raf.readFully(riff)
            if (String(riff, Charsets.US_ASCII) != "RIFF") return null
            raf.skipBytes(4)
            val wave = ByteArray(4)
            raf.readFully(wave)
            if (String(wave, Charsets.US_ASCII) != "WAVE") return null
            var dataOffset = -1
            var dataSize = -1
            var sampleRate = -1
            var channels = -1
            while (raf.filePointer < raf.length()) {
                val id = ByteArray(4)
                raf.readFully(id)
                val size = raf.readIntLE()
                when (String(id, Charsets.US_ASCII)) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        raf.readFully(fmt)
                        channels = fmt.readShortLE(2)
                        sampleRate = fmt.readIntLE(4)
                    }
                    "data" -> {
                        dataOffset = raf.filePointer.toInt()
                        dataSize = size
                        raf.skipBytes(size)
                    }
                    else -> raf.skipBytes(size + (size and 1))
                }
            }
            if (dataOffset < 0 || sampleRate < 0 || channels < 1) return null
            return WavInfo(f, dataOffset, dataSize, sampleRate, channels)
        }
    }

    private fun encode(info: WavInfo, out: File) {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        RandomAccessFile(info.file, "r").use { raf ->
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, info.sampleRate, info.channels
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 128 * 1024)
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            encoder!!.start()

            val outInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var inputEnded = false
            var outputEnded = false
            var pts = 0L
            val bytesPerSample = info.channels * 2
            val frameBytes = (info.sampleRate * bytesPerSample) / 8
            val pcmStart = info.dataOffset.toLong()

            while (!outputEnded) {
                if (!inputEnded) {
                    val inIndex = encoder!!.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val remaining = info.dataSize - (raf.filePointer - pcmStart)
                        if (remaining <= 0) {
                            encoder!!.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val bytes = min(frameBytes.toLong(), remaining).toInt()
                            val buf = ByteArray(bytes)
                            raf.readFully(buf)
                            val inBuf = encoder!!.getInputBuffer(inIndex)!!
                            inBuf.clear()
                            inBuf.put(buf)
                            encoder!!.queueInputBuffer(inIndex, 0, bytes, pts, 0)
                            pts += bytes * 1_000_000L / (info.sampleRate * bytesPerSample)
                        }
                    }
                }

                val outIndex = encoder!!.dequeueOutputBuffer(outInfo, 10_000L)
                if (outIndex >= 0) {
                    val outBuf = encoder!!.getOutputBuffer(outIndex)!!
                    if (outInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        outInfo.size = 0
                    }
                    if (outInfo.size > 0) {
                        if (!muxerStarted) {
                            trackIndex = muxer!!.addTrack(encoder!!.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        outBuf.position(outInfo.offset)
                        outBuf.limit(outInfo.offset + outInfo.size)
                        muxer!!.writeSampleData(trackIndex, outBuf, outInfo)
                    }
                    encoder!!.releaseOutputBuffer(outIndex, false)
                    if (outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEnded = true
                    }
                } else if (outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputEnded = true
                }
            }
        }
        if (muxerStarted) muxer!!.stop()
        muxer?.release()
        encoder?.stop()
        encoder?.release()
    }

    private fun RandomAccessFile.readIntLE(): Int {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun RandomAccessFile.readShortLE(): Int {
        val b0 = read()
        val b1 = read()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
    }

    private fun ByteArray.readIntLE(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.readShortLE(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}