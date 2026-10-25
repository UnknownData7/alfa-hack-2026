package ru.hackathon.credit.application;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import ru.hackathon.credit.document.DocumentService;

/** Общая выдача файла документа клиенту и сотруднику банка. */
public final class ApplicationControllerSupport {

    private ApplicationControllerSupport() {
    }

    public static ResponseEntity<Resource> file(DocumentService.DownloadedFile f) {
        ContentDisposition cd = ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(f.resource());
    }
}
