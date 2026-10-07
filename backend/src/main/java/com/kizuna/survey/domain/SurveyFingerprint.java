package com.kizuna.survey.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class SurveyFingerprint {
  private SurveyFingerprint() {}

  public static String of(Object... values) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (var value : values) append(digest, value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("要求の照合を初期化できません");
    }
  }

  private static void append(MessageDigest digest, Object value) {
    if (value instanceof SurveyDefinition d) {
      append(digest, d.title());
      append(digest, d.questions());
      return;
    }
    if (value instanceof SurveyDefinition.Question q) {
      append(digest, q.questionKey());
      append(digest, q.type());
      append(digest, q.prompt());
      append(digest, q.required());
      append(digest, q.options());
      return;
    }
    if (value instanceof SurveyDefinition.Option o) {
      append(digest, o.optionKey());
      append(digest, o.label());
      return;
    }
    if (value instanceof SurveyAnswers a) {
      append(digest, a.receivedVia());
      append(digest, a.receivedAt());
      append(digest, a.answers());
      return;
    }
    if (value instanceof SurveyAnswers.Answer a) {
      append(digest, a.questionKey());
      append(digest, a.text());
      append(digest, a.optionKey());
      return;
    }
    if (value instanceof List<?> list) {
      digest.update(ByteBuffer.allocate(4).putInt(list.size()).array());
      for (var item : list) append(digest, item);
      return;
    }
    byte[] bytes = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
    if (bytes != null) digest.update(bytes);
  }
}
