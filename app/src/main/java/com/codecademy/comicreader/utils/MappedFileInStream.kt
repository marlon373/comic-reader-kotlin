package com.codecademy.comicreader.utils

import net.sf.sevenzipjbinding.IInStream
import java.nio.channels.FileChannel

/**
 * IInStream implementation backed by a memory-mapped FileChannel.
 * Advantages:
 * - Zero-copy file access
 * - Very fast for large archives (CBR / CBZ / 7z)
 * - Low heap usage (uses direct buffers)
 * Note:
 * Android does not support unmapping MappedByteBuffer explicitly.
 * Keep mappings small to avoid file-lock issues.
 */
class MappedFileInStream(
    // File channel backing this stream
    private val channel: FileChannel
) : IInStream {

    // Current read position
    private var position = 0L

    /**
     * Reads bytes into the provided buffer.
     *
     * @param buffer destination buffer
     * @return number of bytes read, or 0 if EOF
     */
    override fun read(buffer: ByteArray): Int {
        val size = buffer.size
        val mapSize = minOf(size.toLong(), channel.size() - position)
        if (mapSize <= 0) return 0

        val mapped = channel.map(
            FileChannel.MapMode.READ_ONLY,
            position,
            mapSize
        )

        mapped.get(buffer, 0, mapSize.toInt())
        position += mapSize
        return mapSize.toInt()
    }

    /**
     * Moves the current read position.
     *
     * @param offset byte offset
     * @param origin SEEK_SET, SEEK_CUR, or SEEK_END
     * @return new position
     */
    override fun seek(offset: Long, origin: Int): Long {
        position = when (origin) {
            IInStream.SEEK_SET -> offset
            IInStream.SEEK_CUR -> position + offset
            IInStream.SEEK_END -> channel.size() + offset
            else -> position
        }
        return position
    }

    /**
     * Closes the underlying FileChannel.
     */
    override fun close() {
        channel.close()
    }
}