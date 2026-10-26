package com.kamboji.quiver.ui.move

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Saves a replay as an MP4 in Movies/Quiver: copies the window's pixels
 * ~30 times a second while the replay plays and feeds them to the phone's
 * H.264 encoder. Everything stays on the phone.
 */
object ReplayVideo {
    private const val FPS = 30
    private const val WIDTH = 720

    /** Records [window] until [done] returns true. Returns the saved video, or null if it failed. */
    suspend fun record(context: Context, window: Window, name: String, done: () -> Boolean): Uri? {
        val view = window.decorView
        val w = view.width
        val h = view.height
        if (w == 0 || h == 0) return null
        // Encoders want sizes in multiples of 16; 720 wide keeps every phone's encoder happy.
        val outH = ((WIDTH.toFloat() * h / w).roundToInt() / 16 * 16).coerceAtMost(1600)
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Quiver")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            },
        ) ?: return null

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val pfd = resolver.openFileDescriptor(uri, "rw")
        return try {
            codec.configure(
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, outH).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                },
                null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            val input = codec.createInputSurface()
            codec.start()
            val muxer = MediaMuxer(pfd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            coroutineScope {
                val drain = launch(Dispatchers.IO) { drain(codec, muxer) }
                // PixelCopy scales into a video-sized bitmap itself: far less to copy each frame.
                val frame = Bitmap.createBitmap(WIDTH, outH, Bitmap.Config.ARGB_8888)
                val main = Handler(Looper.getMainLooper())
                var next = System.nanoTime()
                while (!done()) {
                    if (copy(window, frame, main)) {
                        val canvas = input.lockHardwareCanvas()
                        canvas.drawBitmap(frame, 0f, 0f, null)
                        input.unlockCanvasAndPost(canvas)
                    }
                    next += 1_000_000_000L / FPS
                    delay(((next - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                }
                codec.signalEndOfInputStream()
                drain.join()
                frame.recycle()
            }
            muxer.stop()
            muxer.release()
            input.release()
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        } finally {
            runCatching { codec.stop() }
            codec.release()
            pfd?.close()
        }
    }

    private suspend fun copy(window: Window, into: Bitmap, handler: Handler): Boolean =
        suspendCancellableCoroutine { cont ->
            PixelCopy.request(window, into, { result -> cont.resume(result == PixelCopy.SUCCESS) }, handler)
        }

    /** Moves encoded frames into the MP4 until the encoder signals the end. */
    private suspend fun drain(codec: MediaCodec, muxer: MediaMuxer) = withContext(Dispatchers.IO) {
        val info = MediaCodec.BufferInfo()
        var track = -1
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 10_000)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    val data = codec.getOutputBuffer(index)
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (data != null && track >= 0 && !config && info.size > 0) muxer.writeSampleData(track, data, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return@withContext
                }
            }
        }
    }
}
