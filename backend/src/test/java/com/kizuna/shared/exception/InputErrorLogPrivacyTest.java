package com.kizuna.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class InputErrorLogPrivacyTest {
  public void receive(String value) {}

  @Test
  void inputErrorsLogOnlyFixedCategoriesAndKeepResponseContract() throws Exception {
    var handler = new CommonExceptionHandler();
    String sensitive = "private-contact@example.invalid 面接内容\n偽ログ";
    var parameter = new MethodParameter(getClass().getMethod("receive", String.class), 0);
    var binding = new BeanPropertyBindingResult(new Object(), "request");
    binding.addError(
        new FieldError("request", "email", sensitive, false, null, null, "メールアドレスを確認してください"));
    var appender = new CaptureAppender();
    Logger logger = (Logger) LogManager.getLogger(CommonExceptionHandler.class);
    appender.start();
    logger.addAppender(appender);
    try {
      var validation = handler.handle(new MethodArgumentNotValidException(parameter, binding));
      assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(validation.getBody())
          .containsEntry("error", "メールアドレスを確認してください")
          .containsEntry("details", Map.of("email", "メールアドレスを確認してください"));
      var parsing =
          handler.handle(
              new HttpMessageNotReadableException(
                  sensitive,
                  new IllegalArgumentException(sensitive),
                  new MockHttpInputMessage(sensitive.getBytes(StandardCharsets.UTF_8))));
      assertThat(parsing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(parsing.getBody()).containsEntry("error", "リクエストの形式が正しくありません");
      var mismatch =
          handler.handle(
              new MethodArgumentTypeMismatchException(
                  sensitive,
                  Integer.class,
                  "size",
                  parameter,
                  new IllegalArgumentException(sensitive)));
      assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(mismatch.getBody()).containsEntry("details", Map.of("size", "値の形式が正しくありません"));
      var missing = handler.handle(new MissingServletRequestParameterException("size", "integer"));
      assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(appender.events)
          .extracting(event -> event.getMessage().getFormattedMessage())
          .containsExactly("入力検証エラー", "リクエスト本文解析エラー", "パラメータ型変換エラー", "必須パラメータ不足");
      assertThat(appender.events).allSatisfy(event -> assertThat(event.getThrown()).isNull());
    } finally {
      logger.removeAppender(appender);
      appender.stop();
    }
  }

  private static class CaptureAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    CaptureAppender() {
      super("input-privacy-test", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
