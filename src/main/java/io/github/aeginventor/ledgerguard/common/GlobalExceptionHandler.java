package io.github.aeginventor.ledgerguard.common;

import io.github.aeginventor.ledgerguard.voucher.dto.ValidationResultResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), null, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        ValidationResultResponse validation = e.getValidation() == null ? null
                : ValidationResultResponse.from(e.getValidation());
        return build(HttpStatus.CONFLICT, e.getMessage(), null, validation);
    }

    /** 낙관적 락 충돌. 같은 전표를 다른 요청이 먼저 바꿨다. 재시도는 호출자가 다시 조회한 뒤 결정한다. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> optimisticLock(OptimisticLockingFailureException e) {
        return build(HttpStatus.CONFLICT, "다른 요청이 먼저 이 전표를 바꿨다. 다시 조회한 뒤 시도해야 한다", null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "요청 본문이 유효하지 않다", details, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception e) {
        String message = e.getMessage() == null ? "요청을 읽을 수 없다" : e.getMessage();
        return build(HttpStatus.BAD_REQUEST, message, null, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, List<String> details,
            ValidationResultResponse validation) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message, details, validation,
                        LocalDateTime.now()));
    }
}
