package com.kizuna.shared.web;

import com.kizuna.shared.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 作業キュー型一覧の「続き」を指すカーソル。並びの先頭キーと、全順序を成す副キー id の組を持つ。
 *
 * <p>外へ出すときは符号化して不透明な 1 個の文字列にする。生の組を問い合わせ引数として晒すと、呼出側が並びの内部（どの列で
 * 並んでいるか）を知らないと続きを要求できず、並びを変えるたびに呼出側の契約が壊れる。符号化に URL 安全な Base64 を使うのは、 時刻キーの表記に含まれる {@code
 * +}（時差）が問い合わせ文字列では空白として復号され、そのままでは往復しないため。
 *
 * <p>復号の失敗は利用者が是正しうる要求誤りとして扱う（400）。壊れたカーソルを黙って先頭扱いにすると、続きを求めた呼出側に 先頭から取り直した結果が返り、取りこぼしが成功に見える。
 */
public record PageCursor(String key, String id) {

  /** 鍵と id の区切り。id・時刻表記のいずれにも現れない制御文字（US）を使い、値の中身と衝突させない。 */
  private static final String SEPARATOR = String.valueOf((char) 0x1F);

  private static final String MALFORMED_MESSAGE = "続きの位置（cursor）が不正です";

  public String encode() {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((key + SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
  }

  public static PageCursor decode(String encoded) {
    String decoded;
    try {
      decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
    String[] parts = decoded.split(SEPARATOR, -1);
    if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
    return new PageCursor(parts[0], parts[1]);
  }

  /** 受付時刻（{@code created_at}）で並ぶ一覧の鍵。 */
  public OffsetDateTime timestampKey() {
    try {
      return OffsetDateTime.parse(key);
    } catch (DateTimeParseException e) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
  }

  /** 副キーが数値の主キーである一覧の id。文字列のまま渡すと問い合わせの型と合わない。 */
  public Long longId() {
    try {
      return Long.valueOf(id);
    } catch (NumberFormatException e) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
  }

  /** 業務日（{@code business_date}）で並ぶ一覧の鍵。 */
  public LocalDate dateKey() {
    try {
      return LocalDate.parse(key);
    } catch (DateTimeParseException e) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
  }
}
