package com.minidb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PageFileTest {
    @TempDir
    Path tempDir;

    private File dbFile;
    private PageFile pageFile;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = tempDir.resolve("test_storage.db").toFile();
        pageFile = new PageFile(dbFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (pageFile != null) {
            pageFile.close();
        }
    }

    @Test
    @DisplayName("Initial page count should be 0 for a new empty file")
    void testInitialPageCount() throws IOException {
        assertEquals(0, pageFile.getPageCount());
    }

    @Test
    @DisplayName("Allocating pages increments page count and returns correct page IDs")
    void testAllocatePage() throws IOException {
        int page0 = pageFile.allocatePage();
        int page1 = pageFile.allocatePage();

        assertEquals(0, page0);
        assertEquals(1, page1);
        assertEquals(2, pageFile.getPageCount());
    }

    @Test
    @DisplayName("Write and read page roundtrip preserves data accurately")
    void testWriteAndReadPage() throws IOException {
        int pageId = pageFile.allocatePage();

        byte[] writeData = new byte[PageFile.PAGE_SIZE];
        byte[] payload = "Hello MiniDB Storage Engine!".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(payload, 0, writeData, 0, payload.length);

        pageFile.writePage(pageId, writeData);

        byte[] readData = new byte[PageFile.PAGE_SIZE];
        pageFile.readPage(pageId, readData);

        assertArrayEquals(writeData, readData);
    }

    @Test
    @DisplayName("Rejects a buffer smaller than one page while reading")
    void rejectsBufferSmallerThanPageSize() throws IOException {
        byte[] smallBuffer = new byte[100];
        int pageId = pageFile.allocatePage();
        assertThrows(
                IllegalArgumentException.class,
                () -> pageFile.readPage(pageId, smallBuffer));
    }

    @Test
    @DisplayName("Rejects a buffer smaller than one page while writing")
    void rejectsBufferSmallerThanPageSizeToWrite() throws IOException {
        byte[] smallBuffer = new byte[100];
        int pageId = pageFile.allocatePage();
        assertThrows(
                IllegalArgumentException.class,
                () -> pageFile.writePage(pageId, smallBuffer));
    }

    @Test
    @DisplayName("Allocate several pages in sequence and verifies that the returned page IDs are consistent and unique.")
    void allocateSeveralPages() throws IOException {
        int first = pageFile.allocatePage();
        int second = pageFile.allocatePage();
        int third = pageFile.allocatePage();
        int fourth = pageFile.allocatePage();
        assertEquals(0, first);
        assertEquals(1, second);
        assertEquals(2, third);
        assertEquals(3, fourth);
        assertEquals(4, pageFile.getPageCount());
    }

    @Test
    @DisplayName("Allocate multiple pages and verify consistancy after reopening")
    void allocateMultiplePagesAndVerifyConsistancyAfterReopening() throws IOException {
        pageFile.allocatePage();
        pageFile.allocatePage();
        pageFile.allocatePage();
        pageFile.close();
        pageFile = null;

        // Reopen the same database file
        try (PageFile reopenedPageFile = new PageFile(dbFile)) {
            assertEquals(3, reopenedPageFile.getPageCount());

        }
    }

    @Test
    @DisplayName("If the current API supports reopening the same file, test that allocating one more page after reopening returns the next expected page ID and increases the page count by one. Avoid adding this test if the current class contract does not allow the file to be reopened that way; document the limitation instead.")
    void testAllocateOneMorePageAfterReopening() throws IOException {
        pageFile.allocatePage();
        pageFile.close();
        try (PageFile reopenedPageFile = new PageFile(dbFile)) {
            assertEquals(1, reopenedPageFile.getPageCount());
            int page1 = reopenedPageFile.allocatePage();
            assertEquals(1, page1);
            assertEquals(2, reopenedPageFile.getPageCount());
        }
    }

    @Test
    @DisplayName("Multiple pages maintain distinct content without cross-page corruption")
    void testMultiplePagesIsolation() throws IOException {
        int page0 = pageFile.allocatePage();
        int page1 = pageFile.allocatePage();

        byte[] buf0 = new byte[PageFile.PAGE_SIZE];
        byte[] buf1 = new byte[PageFile.PAGE_SIZE];
        Arrays.fill(buf0, (byte) 0xAA);
        Arrays.fill(buf1, (byte) 0x55);

        pageFile.writePage(page0, buf0);
        pageFile.writePage(page1, buf1);

        byte[] read0 = new byte[PageFile.PAGE_SIZE];
        byte[] read1 = new byte[PageFile.PAGE_SIZE];
        pageFile.readPage(page0, read0);
        pageFile.readPage(page1, read1);

        assertArrayEquals(buf0, read0);
        assertArrayEquals(buf1, read1);
    }

    @Test
    @DisplayName("Reading an unallocated pageId throws IndexOutOfBoundsException")
    void testReadOutOfBounds() {
        byte[] buffer = new byte[PageFile.PAGE_SIZE];
        assertThrows(IndexOutOfBoundsException.class, () -> pageFile.readPage(0, buffer));
    }

    @Test
    @DisplayName("Writing to an unallocated pageId throws IndexOutOfBoundsException")
    void testWriteOutOfBounds() {
        byte[] buffer = new byte[PageFile.PAGE_SIZE];
        assertThrows(IndexOutOfBoundsException.class, () -> pageFile.writePage(0, buffer));
    }

    @Test
    @DisplayName("Negative page IDs throw IllegalArgumentException")
    void testNegativePageId() {
        byte[] buffer = new byte[PageFile.PAGE_SIZE];
        assertThrows(IllegalArgumentException.class, () -> pageFile.readPage(-1, buffer));
        assertThrows(IllegalArgumentException.class, () -> pageFile.writePage(-1, buffer));
    }

    @Test
    @DisplayName("Data persists across closing and reopening the file")
    void testPersistenceAcrossReopen() throws IOException {
        int pageId = pageFile.allocatePage();
        byte[] data = new byte[PageFile.PAGE_SIZE];
        data[0] = 42;
        data[4095] = 99;
        pageFile.writePage(pageId, data);
        pageFile.close();

        // Reopen existing database file
        try (PageFile reopened = new PageFile(dbFile)) {
            assertEquals(1, reopened.getPageCount());
            byte[] readData = new byte[PageFile.PAGE_SIZE];
            reopened.readPage(pageId, readData);
            assertArrayEquals(data, readData);
        }
    }

    @Test
    @DisplayName("Passing a null buffer to readPage throws NullPointerException")
    void testNullBufferToReadPage() throws IOException {
        int pageId = pageFile.allocatePage();
        assertThrows(NullPointerException.class, () -> pageFile.readPage(pageId, null));
    }

    @Test
    @DisplayName("Passing a null buffer to writePage throws NullPointerException")
    void testNullBufferToWritePage() throws IOException {
        int pageId = pageFile.allocatePage();
        assertThrows(NullPointerException.class, () -> pageFile.writePage(pageId, null));
    }

    @Test
    @DisplayName("Oversized buffer writes only the first page-sized portion")
    void testOversizedBuffer() throws IOException {
        int pageId = pageFile.allocatePage();

        byte[] oversizedBuffer = new byte[PageFile.PAGE_SIZE + 100];

        Arrays.fill(oversizedBuffer, 0, PageFile.PAGE_SIZE, (byte) 0xAA);
        Arrays.fill(oversizedBuffer, PageFile.PAGE_SIZE, oversizedBuffer.length, (byte) 0x55);

        pageFile.writePage(pageId, oversizedBuffer);

        byte[] readBuffer = new byte[PageFile.PAGE_SIZE];
        pageFile.readPage(pageId, readBuffer);

        byte[] expectedBuffer = new byte[PageFile.PAGE_SIZE];
        Arrays.fill(expectedBuffer, (byte) 0xAA);

        assertArrayEquals(expectedBuffer, readBuffer);
    }

}
