package com.kizuna.receipt.once;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 命令が自身の同一性を決める項目から作る内容の要約。各項目に長さを前置し、連結の境界をずらした別内容と衝突させない。 */
public final class Fingerprint {
  private Fingerprint() {}

  public static String of(Object... values) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (var value : values) append(digest, value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("要求の照合を初期化できません");
    }
  }

  private static void append(MessageDigest digest, Object value) {
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
