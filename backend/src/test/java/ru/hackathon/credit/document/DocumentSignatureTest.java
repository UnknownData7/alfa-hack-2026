package ru.hackathon.credit.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DocumentSignatureTest {

    private static boolean check(String ext, int... bytes) {
        byte[] head = new byte[8];
        for (int i = 0; i < bytes.length && i < head.length; i++) {
            head[i] = (byte) bytes[i];
        }
        return DocumentStorage.signatureMatches(ext, head, Math.min(bytes.length, head.length));
    }

    @Test
    void acceptsRealSignatures() {
        assertTrue(check("pdf", '%', 'P', 'D', 'F', '-', '1'));
        assertTrue(check("png", 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A));
        assertTrue(check("jpg", 0xFF, 0xD8, 0xFF, 0xE0));
        assertTrue(check("jpeg", 0xFF, 0xD8, 0xFF, 0xE1));
        assertTrue(check("docx", 'P', 'K', 3, 4));
        assertTrue(check("xlsx", 'P', 'K', 3, 4));
        assertTrue(check("csv", 'd', 'a', 't', 'e', ';'));
    }

    @Test
    void rejectsSpoofedFiles() {
        assertFalse(check("pdf", 'M', 'Z', 0x90, 0));
        assertFalse(check("png", 0xFF, 0xD8, 0xFF));
        assertFalse(check("xlsx", '%', 'P', 'D', 'F'));
        assertFalse(check("csv", 'M', 'Z', 0x90));
        assertFalse(check("txt", 0x7F, 'E', 'L', 'F'));
        assertFalse(check("pdf", '%', 'P'));
        assertFalse(check("exe", 'M', 'Z'));
    }
}
