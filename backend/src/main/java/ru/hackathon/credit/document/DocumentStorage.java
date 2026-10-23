package ru.hackathon.credit.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import ru.hackathon.credit.common.ApiException;

/**
 * Файловое хранилище документов вне веб-корня. Имя файла на диске случайное, исходное имя хранится в БД.
 */
@Component
public class DocumentStorage {

    public static final long MAX_SIZE = 10L * 1024 * 1024;

    private static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "csv", "text/csv",
            "txt", "text/plain");

    private final Path root;

    public DocumentStorage(@Value("${app.uploads.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public record Stored(String storagePath, String safeName, String contentType, long size) {
    }

    public Stored save(Long applicationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Файл не выбран или пустой");
        }
        if (file.getSize() > MAX_SIZE) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Максимальный размер файла 10 МБ");
        }
        String safeName = safeName(file.getOriginalFilename());
        String ext = extension(safeName);
        String contentType = ALLOWED.get(ext);
        if (contentType == null) {
            throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED",
                    "Допустимые форматы: PDF, JPG, PNG, DOCX, XLSX, CSV, TXT");
        }
        Path dir = root.resolve(String.valueOf(applicationId));
        Path target = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("BAD_FILE_NAME", "Недопустимое имя файла");
        }
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить файл", e);
        }
        return new Stored(root.relativize(target).toString(), safeName, contentType, file.getSize());
    }

    public Resource load(String storagePath) {
        Path path = root.resolve(storagePath).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw ApiException.notFound("Файл документа не найден");
        }
        return new FileSystemResource(path);
    }

    /** Удаляет все файлы (используется при сбросе демо-данных). */
    public void clear() {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(p -> !p.equals(root))
                    .forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось очистить хранилище", e);
        }
    }

    static String safeName(String original) {
        String name = original == null ? "document" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").trim();
        if (name.isEmpty() || name.startsWith(".")) {
            name = "document" + name;
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
