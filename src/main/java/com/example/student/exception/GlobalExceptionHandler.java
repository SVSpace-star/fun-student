package com.example.student.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<AppError> notFound(EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<AppError> missingResource() {
        return error(HttpStatus.NOT_FOUND, "Страница или маршрут не найдены");
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<AppError> emailConflict(EmailAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<AppError> dataConflict() {
        return error(HttpStatus.CONFLICT, "Данные конфликтуют с существующей записью");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AppError> invalidCredentials() {
        return error(HttpStatus.UNAUTHORIZED, "Неверная электронная почта или пароль");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AppError> invalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(org.springframework.context.MessageSourceResolvable::getDefaultMessage)
                .findFirst().orElse("Проверьте заполнение полей");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<AppError> invalidParameters() {
        return error(HttpStatus.BAD_REQUEST, "Проверьте параметры запроса");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AppError> invalidJson() {
        return error(HttpStatus.BAD_REQUEST, "Проверьте формат запроса и значения полей");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AppError> statusError(ResponseStatusException ex) {
        return error(ex.getStatusCode(), ex.getReason());
    }

    private ResponseEntity<AppError> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(new AppError(status.value(), message));
    }
}
