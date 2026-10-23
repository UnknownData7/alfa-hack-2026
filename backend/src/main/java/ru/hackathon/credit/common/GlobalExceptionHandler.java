package ru.hackathon.credit.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Единый формат ошибок RFC 7807: title, status, detail, а также code и traceId.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException e) {
        return build(e.status(), e.code(), titleFor(e.status()), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String detail = fields.isEmpty() ? "Проверьте введённые данные" : String.join("; ", fields.values());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Ошибка в данных", detail, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleUnreadable(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Ошибка в данных",
                "Запрос имеет неверный формат", null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Конфликт изменений",
                "Данные изменились, пока вы их редактировали. Обновите страницу и повторите действие", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Нет доступа", "Недостаточно прав для этого действия", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadSize(MaxUploadSizeExceededException e) {
        return build(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Файл слишком большой",
                "Максимальный размер файла 10 МБ", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Не найдено", "Ресурс не найден", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleOther(Exception e) {
        log.error("Необработанная ошибка", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Внутренняя ошибка",
                "Что-то пошло не так. Повторите попытку позже", null);
    }

    static ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String title, String detail,
                                               Map<String, String> fields) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("code", code);
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
        }
        if (fields != null && !fields.isEmpty()) {
            pd.setProperty("fields", fields);
        }
        return ResponseEntity.status(status).body(pd);
    }

    static String titleFor(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "Требуется вход";
            case NOT_FOUND -> "Не найдено";
            case FORBIDDEN -> "Нет доступа";
            case CONFLICT -> "Действие недоступно в текущем статусе";
            case UNPROCESSABLE_ENTITY -> "Нарушено правило процесса";
            case BAD_REQUEST -> "Ошибка в данных";
            default -> status.getReasonPhrase();
        };
    }
}
