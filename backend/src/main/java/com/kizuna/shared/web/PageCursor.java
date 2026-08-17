package com.kizuna.shared.web;

import com.kizuna.shared.exception.ServiceException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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

  /**
   * 副キーを持たないカーソルの符号化。並びの鍵そのものが全順序を成す一覧で使う — 重複候補は電話番号がグループの鍵で、グループ間で一意なので id を添える必要がない。
   *
   * <p>それでも素の値を晒さず符号化するのは {@link #encode()} と同じ理由で、鍵に現れる {@code +}（国際表記の電話番号）が問い合わせ文字列では
   * 空白として復号され、そのままでは往復しないため。
   */
  public static String encodeKey(String key) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(key.getBytes(StandardCharsets.UTF_8));
  }

  /** {@link #encodeKey} の逆。復号の失敗を要求誤り（400）として扱う理由は {@link #decode} と同じ。 */
  public static String decodeKey(String encoded) {
    String decoded = decodeBase64(encoded);
    if (decoded.isEmpty()) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
    return decoded;
  }

  /**
   * 符号を解いて中身の文字列に戻す。復号できない入力はすべてここで要求誤り（400）に落とす。
   *
   * <p>判定は 2 つで、この 2 つで<b>全てである</b>。Base64 として解けたバイト列が「問い合わせへ束縛できる文字列」にならない 経路は次の 2
   * つしかなく、どちらも塞げば残りは無い:
   *
   * <ul>
   *   <li><b>UTF-8 として不正</b>: {@code new String(bytes, UTF_8)} は不正なバイトを黙って U+FFFD に置き換えるため、
   *       捏造された鍵がそのまま問い合わせへ渡り、400 のはずが「空の成功」になる。空で返ると候補が尽きたように読めるので、
   *       黙って先頭扱いにするのと同じ害がある。報告する復号器を使って例外にする。
   *   <li><b>NUL を含む</b>: PostgreSQL の text は NUL を保持できないので、渡すと束縛で落ちて DB 由来の 500 になる。
   * </ul>
   *
   * <p>これ以外の文字（他の制御文字・孤立サロゲート・長大な値）は text にそのまま入るか、厳格な復号器が既に弾いている。
   *
   * <p>鍵と id のどちらも最終的には問い合わせへ渡るため、判定は復号の直後に 1 箇所で行う。
   */
  private static String decodeBase64(String encoded) {
    byte[] bytes;
    try {
      bytes = Base64.getUrlDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
    String decoded;
    try {
      // CharsetDecoder は状態を持つので使い回さない
      decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException e) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
    if (decoded.indexOf('\0') >= 0) {
      throw new ServiceException(MALFORMED_MESSAGE);
    }
    return decoded;
  }

  public static PageCursor decode(String encoded) {
    String decoded = decodeBase64(encoded);
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

  /** 数値で並ぶ一覧の鍵（人数・コース時間・分に均した到着予定時刻）。 */
  public Integer numberKey() {
    try {
      return Integer.valueOf(key);
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
