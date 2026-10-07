package com.kizuna.review.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ReviewFingerprint {
  private ReviewFingerprint() {}

  public static String of(Object... values) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (Object value : values) {
        byte[] bytes = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("要求の照合を初期化できません");
    }
  }

  public static String receipt(ReviewInput i) {
    return of(
        "RECEIVED", i.body(), i.displayName(), i.receivedVia(), i.receivedAt(), i.originOrderId());
  }
}
