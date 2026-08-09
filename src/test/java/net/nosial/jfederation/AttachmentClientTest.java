package net.nosial.jfederation;

import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.FileAttachmentRecord;
import net.nosial.jfederation.records.UploadResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentClientTest extends FederationClientTestBase {

    @Test
    void testUploadFileAttachment() throws IOException {
        String entityUuid = client.pushEntity("attachment-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "attachment_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for attachment", "Attachment test", "attachment");
        createdEvidenceRecords.add(evidenceUuid);

        Path testFile = createTempFile("test_attachment.txt", "This is test content for file attachment.");
        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        assertNotNull(uploadResult);
        assertNotNull(uploadResult.uuid());
        assertNotNull(uploadResult.url());
        createdAttachments.add(uploadResult.uuid());

        FileAttachmentRecord info = client.getAttachmentInfo(uploadResult.uuid());
        assertNotNull(info);
        assertEquals(evidenceUuid, info.evidenceUuid());
        assertEquals(testFile.getFileName().toString(), info.fileName());
        assertTrue(info.fileSize() > 0);
        assertNotNull(info.fileMime());
    }

    @Test
    void testUploadNoteAttachment() throws IOException {
        String entityUuid = client.pushEntity("note-attachment-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "note_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for note", "Note test", "note");
        createdEvidenceRecords.add(evidenceUuid);

        UploadResult result = client.uploadNoteAttachment(evidenceUuid, "note.txt", "Note attachment content");
        assertNotNull(result);
        assertNotNull(result.uuid());
        createdAttachments.add(result.uuid());

        FileAttachmentRecord info = client.getAttachmentInfo(result.uuid());
        assertEquals(evidenceUuid, info.evidenceUuid());
    }

    @Test
    void testUploadNoteAttachmentAppendsTxtExtension() throws IOException {
        String entityUuid = client.pushEntity("note-extension-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "note_ext_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for note extension", "Note ext test", "note_ext");
        createdEvidenceRecords.add(evidenceUuid);

        UploadResult result = client.uploadNoteAttachment(evidenceUuid, "note_without_extension", "Content for extension test");
        assertNotNull(result.uuid());
        createdAttachments.add(result.uuid());

        FileAttachmentRecord info = client.getAttachmentInfo(result.uuid());
        assertEquals("note_without_extension.txt", info.fileName(),
            ".txt extension should be appended when the name has no extension");
    }

    @Test
    void testUploadNoteAttachmentKeepsExistingTxtExtension() throws IOException {
        String entityUuid = client.pushEntity("note-keeps-ext-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "note_keep_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for note keeps extension", "Note keep test", "note_keep");
        createdEvidenceRecords.add(evidenceUuid);

        UploadResult result = client.uploadNoteAttachment(evidenceUuid, "note_already.txt", "Content for keep test");
        assertNotNull(result.uuid());
        createdAttachments.add(result.uuid());

        FileAttachmentRecord info = client.getAttachmentInfo(result.uuid());
        assertEquals("note_already.txt", info.fileName(),
            "Existing .txt extension should not be duplicated");
    }

    @Test
    void testUploadFileAttachmentWithCustomFileName() throws IOException {
        String entityUuid = client.pushEntity("custom-name-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "custom_name");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for custom file name", "Custom name test", "custom_name");
        createdEvidenceRecords.add(evidenceUuid);

        Path testFile = createTempFile("actual_name.txt", "Content with a custom server-side name.");
        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString(), "renamed.bin");
        assertNotNull(uploadResult.uuid());
        createdAttachments.add(uploadResult.uuid());

        FileAttachmentRecord info = client.getAttachmentInfo(uploadResult.uuid());
        assertEquals("renamed.bin", info.fileName());
    }

    @Test
    void testUploadFileAttachmentFromUrlWithDefaultMaxSize() throws Exception {
        String entityUuid = client.pushEntity("url-default-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "url_default");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for URL attachment", "URL test", "url_default");
        createdEvidenceRecords.add(evidenceUuid);

        byte[] content = "Remote content for default max size upload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        com.sun.net.httpserver.HttpServer httpServer = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/file.txt", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try (var os = exchange.getResponseBody()) {
                os.write(content);
            }
        });
        httpServer.start();
        try {
            String fileUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/file.txt";

            UploadResult result = client.uploadFileAttachmentFromUrl(evidenceUuid, fileUrl);
            assertNotNull(result.uuid());
            createdAttachments.add(result.uuid());

            FileAttachmentRecord info = client.getAttachmentInfo(result.uuid());
            assertEquals("file.txt", info.fileName());
            assertEquals(content.length, info.fileSize());
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void testUploadFileAttachmentFromUrlFailsOnRemoteError() throws Exception {
        String entityUuid = client.pushEntity("url-error-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "url_error");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for failed URL attachment", "URL error test", "url_error");
        createdEvidenceRecords.add(evidenceUuid);

        com.sun.net.httpserver.HttpServer httpServer = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/missing.bin", exchange -> {
            exchange.sendResponseHeaders(404, -1);
        });
        httpServer.start();
        try {
            String fileUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/missing.bin";
            FederationClientException ex = assertThrows(FederationClientException.class,
                () -> client.uploadFileAttachmentFromUrl(evidenceUuid, fileUrl));
            assertEquals(404, ex.getStatusCode(),
                "Remote download failure should surface the remote HTTP status code");
            assertTrue(ex.getMessage().contains("Failed to download file from URL"),
                "Unexpected message: " + ex.getMessage());
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void testUploadFileAttachmentFromUrlFallsBackToDefaultFileName() throws Exception {
        String entityUuid = client.pushEntity("url-fallback-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "url_fallback");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for URL filename fallback", "URL fallback test", "url_fallback");
        createdEvidenceRecords.add(evidenceUuid);

        byte[] content = "Fallback filename content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        com.sun.net.httpserver.HttpServer httpServer = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/upload/", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try (var os = exchange.getResponseBody()) {
                os.write(content);
            }
        });
        httpServer.start();
        try {
            String trailingSlashUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/upload/";
            UploadResult result = client.uploadFileAttachmentFromUrl(evidenceUuid, trailingSlashUrl);
            assertNotNull(result.uuid());
            createdAttachments.add(result.uuid());
            assertEquals("downloaded_file", client.getAttachmentInfo(result.uuid()).fileName(),
                "URL ending in '/' should fall back to 'downloaded_file'");
        } finally {
            httpServer.stop(0);
        }

        com.sun.net.httpserver.HttpServer queryServer = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        queryServer.createContext("/upload/file.txt", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try (var os = exchange.getResponseBody()) {
                os.write(content);
            }
        });
        queryServer.start();
        String queryUrl = "http://127.0.0.1:" + queryServer.getAddress().getPort() + "/upload/file.txt?token=abc";
        try {
            UploadResult result = client.uploadFileAttachmentFromUrl(evidenceUuid, queryUrl);
            assertNotNull(result.uuid());
            createdAttachments.add(result.uuid());
            assertEquals("downloaded_file", client.getAttachmentInfo(result.uuid()).fileName(),
                "URL with query string should fall back to 'downloaded_file'");
        } finally {
            queryServer.stop(0);
        }
    }

    @Test
    void testDownloadAttachment() throws IOException {
        String entityUuid = client.pushEntity("download-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "download_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for download", "Download test", "download");
        createdEvidenceRecords.add(evidenceUuid);

        String originalContent = "This is the original content for download test.";
        Path testFile = createTempFile("download_test.txt", originalContent);

        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        createdAttachments.add(uploadResult.uuid());

        Path tempDir = Files.createTempDirectory("fed_download_");
        createdTempFiles.add(tempDir);
        String downloadedFile = client.downloadAttachment(uploadResult.uuid(), tempDir.toString());
        Path downloadedPath = Path.of(downloadedFile);

        assertTrue(Files.exists(downloadedPath));
        assertEquals(originalContent, Files.readString(downloadedPath));
    }

    @Test
    void testDeleteAttachment() throws IOException {
        String entityUuid = client.pushEntity("delete-attachment-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "delete_attachment_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence for delete", "Delete attachment test", "delete_attachment");
        createdEvidenceRecords.add(evidenceUuid);

        Path testFile = createTempFile("delete_test.txt", "This file will be deleted.");
        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        String attachmentUuid = uploadResult.uuid();

        assertNotNull(client.getAttachmentInfo(attachmentUuid));
        client.deleteAttachment(attachmentUuid);

        try {
            client.getAttachmentInfo(attachmentUuid);
            fail("Expected FederationClientException for deleted attachment");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testUploadAttachmentInvalidEvidenceUuid() {
        Path testFile = createTempFile("invalid_evidence.txt", "Test content");
        assertThrows(IllegalArgumentException.class,
            () -> client.uploadFileAttachment("", testFile.toString()));
    }

    @Test
    void testUploadAttachmentNonExistentFile() {
        assertThrows(IllegalArgumentException.class,
            () -> client.uploadFileAttachment(UUID.randomUUID().toString(), "/non/existent/file.txt"));
    }

    @Test
    void testGetAttachmentInfoInvalidUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.getAttachmentInfo(""));
    }

    @Test
    void testGetAttachmentInfoNonExistent() {
        String fakeUuid = "0198f41f-45c7-78eb-a2a7-86de4e99991a";
        try {
            client.getAttachmentInfo(fakeUuid);
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testDeleteAttachmentInvalidUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.deleteAttachment(""));
    }

    @Test
    void testGetEvidenceAttachments() throws IOException {
        String entityUuid = client.pushEntity("evidence-attachments-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "evidence_attachments_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Evidence with attachments", "Evidence attachments test", "evidence_attachments");
        createdEvidenceRecords.add(evidenceUuid);

        for (int i = 1; i <= 2; i++) {
            Path f = createTempFile("evidence_attachment_" + i + ".txt", "Content " + i);
            UploadResult r = client.uploadFileAttachment(evidenceUuid, f.toString());
            createdAttachments.add(r.uuid());
        }

        List<FileAttachmentRecord> attachments = client.getEvidenceAttachments(evidenceUuid);
        assertEquals(2, attachments.size());
        for (FileAttachmentRecord a : attachments) {
            assertEquals(evidenceUuid, a.evidenceUuid());
        }
    }

    @Test
    void testListAttachments() throws IOException {
        String entityUuid = client.pushEntity("list-attachments-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "list_attachments_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Evidence for listing", "List attachments test", "list_attachments");
        createdEvidenceRecords.add(evidenceUuid);

        for (int i = 1; i <= 3; i++) {
            Path f = createTempFile("list_attachment_" + i + ".txt", "Content " + i);
            UploadResult r = client.uploadFileAttachment(evidenceUuid, f.toString());
            createdAttachments.add(r.uuid());
        }

        List<FileAttachmentRecord> attachments = client.listAttachments(1, 100);
        assertNotNull(attachments);
        assertTrue(attachments.size() >= 3);
    }

    @Test
    void testListAttachmentsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> client.listAttachments(0, 10));
        assertThrows(IllegalArgumentException.class, () -> client.listAttachments(1, 0));
    }

    @Test
    void testUploadAttachmentUnauthorized() throws IOException {
        FederationClient restricted = createLimitedOperator("no-upload");

        String entityUuid = client.pushEntity("unauthorized-upload-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "unauthorized_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence", "Test", "test");
        createdEvidenceRecords.add(evidenceUuid);

        Path testFile = createTempFile("unauthorized.txt", "Unauthorized upload test");
        try {
            restricted.uploadFileAttachment(evidenceUuid, testFile.toString());
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        restricted.close();
    }

    @Test
    void testDownloadAttachmentInvalidUuid() {
        assertThrows(IllegalArgumentException.class,
            () -> client.downloadAttachment("", "/tmp"));
    }

    @Test
    void testMultipleAttachmentsPerEvidence() throws IOException {
        String entityUuid = client.pushEntity("multiple-attachments-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "multiple_attachments_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Evidence with multiple attachments", "Multiple attachments test", "multiple");
        createdEvidenceRecords.add(evidenceUuid);

        for (int i = 1; i <= 3; i++) {
            Path f = createTempFile("attachment_" + i + ".txt", "Content " + i);
            UploadResult r = client.uploadFileAttachment(evidenceUuid, f.toString());
            createdAttachments.add(r.uuid());

            FileAttachmentRecord info = client.getAttachmentInfo(r.uuid());
            assertEquals(evidenceUuid, info.evidenceUuid());
        }
    }

    @Test
    void testAttachmentLifecycleIntegrity() throws IOException {
        String entityUuid = client.pushEntity("lifecycle-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "lifecycle_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Lifecycle test evidence", "Lifecycle test", "lifecycle");
        createdEvidenceRecords.add(evidenceUuid);

        Path testFile = createTempFile("lifecycle_test.txt", "Lifecycle test content");
        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        String attachmentUuid = uploadResult.uuid();
        createdAttachments.add(attachmentUuid);

        FileAttachmentRecord originalInfo = client.getAttachmentInfo(attachmentUuid);
        assertNotNull(originalInfo);

        Path tempDir = Files.createTempDirectory("fed_dl_");
        createdTempFiles.add(tempDir);
        String downloaded = client.downloadAttachment(attachmentUuid, tempDir.toString());
        assertEquals("Lifecycle test content", Files.readString(Path.of(downloaded)));

        client.deleteAttachment(attachmentUuid);
        removeFromCleanup(createdAttachments, attachmentUuid);

        try {
            client.getAttachmentInfo(attachmentUuid);
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testBinaryAttachmentDownloadIntegrity() throws IOException {
        String entityUuid = client.pushEntity("binary-attachment-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "binary_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Binary attachment evidence", "Note", "binary");
        createdEvidenceRecords.add(evidenceUuid);

        byte[] binaryContent = new byte[1024];
        new java.util.Random().nextBytes(binaryContent);
        Path testFile = Files.createTempFile("binary_test", ".bin");
        createdTempFiles.add(testFile);
        Files.write(testFile, binaryContent);

        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        createdAttachments.add(uploadResult.uuid());

        Path tempDir = Files.createTempDirectory("fed_bin_");
        createdTempFiles.add(tempDir);
        String downloaded = client.downloadAttachment(uploadResult.uuid(), tempDir.toString());
        byte[] downloadedBytes = Files.readAllBytes(Path.of(downloaded));

        assertArrayEquals(binaryContent, downloadedBytes);
    }
}
