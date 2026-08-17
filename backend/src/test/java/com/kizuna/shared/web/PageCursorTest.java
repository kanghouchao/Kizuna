package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageCursorTest {

  @Test
  @DisplayName("符号化した位置が元の組へ戻ること")
  void roundTripsThroughEncoding() {
    PageCursor cursor = new PageCursor("2026-08-04T10:00:00+09:00", "order-1");

    assertThat(PageCursor.decode(cursor.encode())).isEqualTo(cursor);
  }

  @Test
  @DisplayName("符号化した位置が問い合わせ文字列をそのまま通れること")
  void encodesToUrlSafeCharactersOnly() {
    // 時差の + が生のまま問い合わせ文字列に載ると空白として復号され、位置が往復しない。
    String encoded = new PageCursor("2026-08-04T10:00:00+09:00", "order-1").encode();

    assertThat(encoded).matches("[A-Za-z0-9_-]+");
  }

  @Test
  @DisplayName("受付時刻の鍵を時刻として読めること")
  void readsTheTimestampKey() {
    OffsetDateTime receivedAt = OffsetDateTime.parse("2026-08-04T10:00:00+09:00");

    assertThat(new PageCursor(receivedAt.toString(), "order-1").timestampKey())
        .isEqualTo(receivedAt);
  }

  @Test
  @DisplayName("業務日の鍵を日付として読めること")
  void readsTheDateKey() {
    assertThat(new PageCursor("2026-08-04", "order-1").dateKey())
        .isEqualTo(LocalDate.parse("2026-08-04"));
  }

  @Test
  @DisplayName("数値の鍵を数として読めること（人数・コース時間・分に均した到着予定時刻）")
  void readsTheNumberKey() {
    assertThat(new PageCursor("1170", "order-1").numberKey()).isEqualTo(1170);
    // 未設定を均した番兵もそのまま往復する（均さないとその行へ二度と到達できない）
    assertThat(new PageCursor(String.valueOf(Integer.MAX_VALUE), "order-1").numberKey())
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  @DisplayName("数値の副キーを数として読めること")
  void readsTheNumericId() {
    assertThat(new PageCursor("2026-08-04T10:00:00+09:00", "42").longId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("復号できない位置は要求誤りとして撥ねること")
  void rejectsAnUndecodableCursor() {
    // 黙って先頭扱いにすると、続きを求めた呼出側に先頭が返り、取りこぼしが成功に見える。
    assertThatThrownBy(() -> PageCursor.decode("!!!not-base64!!!"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("組になっていない位置は要求誤りとして撥ねること")
  void rejectsACursorWithoutBothParts() {
    String single = Base64.getUrlEncoder().withoutPadding().encodeToString("o1".getBytes());

    assertThatThrownBy(() -> PageCursor.decode(single)).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("鍵が並びの型として読めない位置は要求誤りとして撥ねること")
  void rejectsAKeyThatIsNotOfTheListsOrdering() {
    PageCursor cursor = new PageCursor("not-a-timestamp", "order-1");

    assertThatThrownBy(cursor::timestampKey).isInstanceOf(ServiceException.class);
    assertThatThrownBy(cursor::dateKey).isInstanceOf(ServiceException.class);
    assertThatThrownBy(cursor::numberKey).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("副キーが数として読めない位置は要求誤りとして撥ねること")
  void rejectsANonNumericIdWhereTheListOrdersByNumber() {
    // 数として読めない副キーをそのまま問い合わせへ渡すと、要求誤りが 500 として出る。
    assertThatThrownBy(new PageCursor("2026-08-04T10:00:00+09:00", "order-1")::longId)
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("復号すると NUL を含むカーソルは、要求誤りとして撥ねられること")
  void rejectsCursorsWhoseDecodedFormCarriesANulCharacter() {
    // "AA" は 1 バイトの 0x00 に復号される。空ではないので長さの検査は素通りするが、
    // PostgreSQL の text は NUL を保持できないため、渡すと問い合わせの束縛で落ちて
    // 是正しうる要求誤り（400）のはずが DB 由来の 500 になる
    assertThatThrownBy(() -> PageCursor.decodeKey("AA"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("続きの位置");
  }

  @Test
  @DisplayName("鍵と id の組でも、NUL を含むカーソルは撥ねられること")
  void rejectsPairCursorsCarryingANulCharacter() {
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("2026-08-17T00:00:00Z" + (char) 0x1F + "\u0000").getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> PageCursor.decode(encoded))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("続きの位置");
  }
}
