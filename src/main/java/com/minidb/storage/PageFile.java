package com.minidb.storage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

/**
 * Manages fixed-size page I/O operations directly against an underlying
 * database file.
 * Each page is fixed at 4096 bytes (4 KB).
 */
public class PageFile implements Closeable, AutoCloseable {

    /**
     * Standard page size in bytes (4 KB).
     */
    public static final int PAGE_SIZE = 4096;

    private final File file;
    private final RandomAccessFile raf;
    private final FileChannel channel;

    /**
     * Opens or creates a PageFile at the specified file path.
     *
     * @param file the underlying database file
     * @throws IOException if the file cannot be opened
     */
    public PageFile(File file) throws IOException {
        this.file = Objects.requireNonNull(file, "File must not be null");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        this.raf = new RandomAccessFile(file, "rw");
        this.channel = this.raf.getChannel();
    }

    /**
     * Reads the contents of a specific page into the provided destination buffer.
     *
     * @param pageId the zero-based page identifier
     * @param buffer byte array where the page content will be stored (must be at
     *               least PAGE_SIZE)
     * @throws IOException if an I/O error occurs or pageId is out of bounds
     */
    public synchronized void readPage(int pageId, byte[] buffer) throws IOException {
        validatePageId(pageId);
        Objects.requireNonNull(buffer, "Buffer must not be null");
        if (buffer.length < PAGE_SIZE) {
            throw new IllegalArgumentException("Buffer size must be at least " + PAGE_SIZE + " bytes");
        }

        long offset = (long) pageId * PAGE_SIZE;
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, PAGE_SIZE);
        channel.position(offset);
        int attempt = 0;
        int bytesRead = 0;
        while (bytesRead < PAGE_SIZE) {
            int read = channel.read(byteBuffer);
            if (read == -1) {
                // If end of file is reached unexpectedly before reading full page, zero-fill
                // the rest
                while (bytesRead < PAGE_SIZE) {
                    buffer[bytesRead++] = 0;
                }
                break;
            }
            if (read == 0) {
                attempt++;
                if (attempt >= 3) {
                    throw new IOException("No data readed from the last 3 attempts");
                }
            } else {
                attempt = 0;
            }
            bytesRead += read;

        }
    }

    /**
     * Writes the contents of the given buffer to the specified page.
     *
     * @param pageId the zero-based page identifier
     * @param buffer byte array containing data to write (must have at least
     *               PAGE_SIZE bytes)
     * @throws IOException if an I/O error occurs or pageId is out of bounds
     */
    public synchronized void writePage(int pageId, byte[] buffer) throws IOException {
        validatePageId(pageId);
        Objects.requireNonNull(buffer, "Buffer must not be null");
        if (buffer.length < PAGE_SIZE) {
            throw new IllegalArgumentException("Buffer size must be at least " + PAGE_SIZE + " bytes");
        }

        long offset = (long) pageId * PAGE_SIZE;
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, PAGE_SIZE);
        channel.position(offset);
        int attempt = 0;
        while (byteBuffer.hasRemaining()) {
            int writter = channel.write(byteBuffer);
            if (writter == 0) {
                attempt++;
                if (attempt >= 3) {
                    throw new IOException("No data written to the last 3 attempts");
                }
            } else {
                attempt = 0;
            }
        }
    }

    /**
     * Allocates a new empty page at the end of the file and returns its pageId.
     *
     * @return the zero-based pageId of the newly allocated page
     * @throws IOException if an I/O error occurs
     */
    public synchronized int allocatePage() throws IOException {
        int newPageId = getPageCount();
        long newSize = (long) (newPageId + 1) * PAGE_SIZE;
        raf.setLength(newSize);
        return newPageId;
    }

    /**
     * Returns the total number of pages currently in this file.
     *
     * @return page count
     * @throws IOException if an I/O error occurs
     */
    public synchronized int getPageCount() throws IOException {
        long length = channel.size();
        return (int) (length / PAGE_SIZE);
    }

    /**
     * Flushes any unwritten page modifications to the underlying storage device.
     *
     * @throws IOException if an I/O error occurs
     */
    public synchronized void sync() throws IOException {
        channel.force(true);
    }

    /**
     * Validates that the requested pageId exists within the file.
     */
    private void validatePageId(int pageId) throws IOException {
        if (pageId < 0) {
            throw new IllegalArgumentException("pageId cannot be negative: " + pageId);
        }
        int totalPages = getPageCount();
        if (pageId >= totalPages) {
            throw new IndexOutOfBoundsException(
                    "pageId " + pageId + " out of bounds. Current page count: " + totalPages);
        }
    }

    /**
     * Returns the underlying file handle.
     */
    public File getFile() {
        return file;
    }

    @Override
    public synchronized void close() throws IOException {
        if (channel.isOpen()) {
            sync();
            channel.close();
        }
        raf.close();
    }
}
