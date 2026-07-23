package com.kizuna.shared.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class CommonExceptionHandler {

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, Object>> handle(AuthenticationException ex) {
    log.error(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put(
        "error", Objects.requireNonNull(ex.getBindingResult().getFieldError()).getDefaultMessage());
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
    body.put("details", fieldErrors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /** 必須クエリ／リクエストパラメータの欠落を、既定の 500 でなくクライアント誤りとして 400 へ映射する。 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handle(MissingServletRequestParameterException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handle(AccessDeniedException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "アクセス権限がありません");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
  }

  @ExceptionHandler(ServiceException.class)
  public ResponseEntity<Map<String, Object>> handle(ServiceException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handle(ConflictException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  /** JPA @Version の楽観ロック競合（並行トランザクションの敗者）を 500 でなく 409 へ映射する。 */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, Object>> handle(ObjectOptimisticLockingFailureException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", "他の操作と競合しました。最新の状態を取得してやり直してください");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handle(NoResourceFoundException ex) {
    log.warn(ex.getMessage());
    Map<String, Object> body = new HashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handle(Exception ex) {
    log.error(ex.getMessage(), ex);
    Map<String, Object> body = new HashMap<>();
    body.put("error", "サーバー内部エラーが発生しました");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
