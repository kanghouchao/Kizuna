package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.point.domain.MemberLedgerEntryView;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
  @DisplayName("会員 ID と受け取った Pageable で問い合わせ、理由と実行者を添えた明細のページを返す")
  void returnsTheLedgerPageForTheMember() {
    var pageable = PageRequest.of(0, 2);
    when(pointEntryRepository.findLedgerEntryViews(7L, pageable))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new View(
                        3L, AT, PointEntryType.WITHDRAWAL_CLEAR, -500, null, "会員退会", null, "職員 太郎"),
                    new View(
                        2L,
                        AT.minusDays(1),
                        PointEntryType.ORDER_GRANT,
                        500,
                        null,
                        null,
                        "本店",
                        null)),
                pageable,
                3));

    var page = service.entries(7L, pageable);

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(page.getContent().getFirst().entryType()).isEqualTo(PointEntryType.WITHDRAWAL_CLEAR);
    assertThat(page.getContent().getFirst().reason()).isEqualTo("会員退会");
    assertThat(page.getContent().getFirst().actorName()).isEqualTo("職員 太郎");
  }
}
