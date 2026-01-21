package com.kizuna.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

public class ExceptionHandlerTests {

  @Test
  void handleAuthentication_returnsUnauthorizedBody() {
    CommonExceptionHandler handler = new CommonExceptionHandler();
    AuthenticationException ex = mock(AuthenticationException.class);
    when(ex.getMessage()).thenReturn("Invalid credentials");
    ResponseEntity<Map<String, Object>> resp = handler.handle(ex);
    assertThat(resp.getStatusCode().value()).isEqualTo(401);
    assertThat(resp.getBody()).isInstanceOf(Map.class);
    Map<String, Object> body = Objects.requireNonNull(resp.getBody());
    assertThat(body.get("error")).isEqualTo("Invalid credentials");
  }

  @Test
  void handleValidation_returnsBadRequest_withFieldErrors() {
    CommonExceptionHandler handler = new CommonExceptionHandler();

    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "objectName");
    bindingResult.addError(new FieldError("objectName", "username", "must not be blank"));

    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    when(ex.getBindingResult()).thenReturn(bindingResult);
    // The real implementation uses ex.getMessage() for logging, so we might want to mock it to
    // avoid NPE if logger uses it,
    // but checking the code: log.error(ex.getMessage(), ex); so it's safe if it returns null?
    // Actually the implementation uses ex.getBindingResult().getFieldError().getDefaultMessage()
    // for the body "error".

    // We also need to ensure ex.getMessage() doesn't crash the logger if that's critical,
    // but here we just care about the response.

    ResponseEntity<Map<String, Object>> resp = handler.handle(ex);
    assertThat(resp.getStatusCode().value()).isEqualTo(400);
    assertThat(resp.getBody()).isNotNull();
    Map<String, Object> body = Objects.requireNonNull(resp.getBody());
    // The implementation puts the first field error message as "error"
    assertThat(body.get("error")).isEqualTo("must not be blank");
    assertThat(body.get("details")).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, String> details = (Map<String, String>) body.get("details");
    assertThat(details.get("username")).isEqualTo("must not be blank");
  }
}
