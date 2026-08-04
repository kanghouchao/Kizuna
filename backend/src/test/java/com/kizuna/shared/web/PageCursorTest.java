package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
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
  }
}
