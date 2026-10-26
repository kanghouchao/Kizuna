package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.point.domain.MemberLedgerEntryView;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.shared.web.PageCursor;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class MemberLedgerQueryServiceTest {

  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

  @Mock private PointEntryRepository pointEntryRepository;

  @InjectMocks private MemberLedgerQueryService service;

  private record View(
      Long id,
      OffsetDateTime createdAt,
      PointEntryType entryType,
      Integer amount,
      LocalDate expiresOn,
      String reason,
      String storeName,
      String actorName)
      implements MemberLedgerEntryView {
    @Override
    public Long getId() {
      return id;
    }

    @Override
    public OffsetDateTime getCreatedAt() {
      return createdAt;
    }

    @Override
    public PointEntryType getEntryType() {
      return entryType;
    }

    @Override
    public Integer getAmount() {
      return amount;
    }

    @Override
    public LocalDate getExpiresOn() {
      return expiresOn;
    }

    @Override
    public String getReason() {
      return reason;
    }

    @Override
    public String getStoreName() {
      return storeName;
    }

    @Override
    public String getActorName() {
      return actorName;
    }
  }

  @Test
  @DisplayName("先頭は上限より 1 件多く取り、余分があれば末尾の行から続きの位置を作る")
  void firstPageFetchesOneExtraAndBuildsCursor() {
    ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
    when(pointEntryRepository.findLedgerEntryViews(eq(7L), limit.capture()))
        .thenReturn(
            List.of(
                new View(
                    3L, AT, PointEntryType.WITHDRAWAL_CLEAR, -500, null, "会員退会", null, "職員 太郎"),
                new View(
                    2L, AT.minusDays(1), PointEntryType.ORDER_GRANT, 500, null, null, "本店", null),
                new View(
                    1L,
                    AT.minusDays(2),
                    PointEntryType.BENEFIT_GRANT,
                    100,
                    LocalDate.of(2027, 1, 1),
                    null,
                    null,
                    null)));

    var page = service.entries(7L, null, 2);

    assertThat(limit.getValue().max()).isEqualTo(3);
    assertThat(page.content()).hasSize(2);
    assertThat(page.content().getFirst().entryType()).isEqualTo(PointEntryType.WITHDRAWAL_CLEAR);
    assertThat(page.content().getFirst().reason()).isEqualTo("会員退会");
    assertThat(page.content().getFirst().actorName()).isEqualTo("職員 太郎");
    assertThat(PageCursor.decode(page.nextCursor()))
        .isEqualTo(new PageCursor(AT.minusDays(1).toString(), "2"));
  }

  @Test
  @DisplayName("続きは復号したカーソルの組（記帳時刻・id）で同じ会員から取り、余分が無ければ続きは無い")
  void nextPageUsesDecodedCursorTuple() {
    String cursor = new PageCursor(AT.toString(), "3").encode();
    when(pointEntryRepository.findLedgerEntryViewsAfter(eq(7L), eq(AT), eq(3L), any()))
        .thenReturn(
            List.of(
                new View(2L, AT.minusDays(1), PointEntryType.USE, -30, null, null, "本店", null)));

    var page = service.entries(7L, cursor, 20);

    assertThat(page.content()).hasSize(1);
    assertThat(page.nextCursor()).isNull();
    verify(pointEntryRepository).findLedgerEntryViewsAfter(eq(7L), eq(AT), eq(3L), any());
  }
}
