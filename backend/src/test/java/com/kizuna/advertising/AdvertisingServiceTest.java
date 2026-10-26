package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.api.dto.AdvertisingRequests.CreateRequest;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingRequest;
import com.kizuna.advertising.domain.AdvertisingValues;
import com.kizuna.advertising.infrastructure.AdvertisingRecords;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/** 広告費の service が引数の店舗で記録を引き、作る行にもその店舗を明示することの単体テスト。 */
class AdvertisingServiceTest {
  static final Long STORE_ID = 7L;

  AdvertisingRecords records = mock(AdvertisingRecords.class);
  ActorIdentityService actors = mock(ActorIdentityService.class);
  BusinessAudit audit = mock(BusinessAudit.class);
  AdvertisingService service =
      new AdvertisingService(
          records,
          actors,
          audit,
          JsonMapper.builder().build(),
          Clock.systemUTC(),
          new AppProperties());

  @Test
  void readsAskTheRecordsForTheGivenStore() {
    when(records.list(eq(STORE_ID), eq("2026-09"), any())).thenReturn(new PageImpl<>(List.of()));
    service.list(STORE_ID, "2026-09", 0, 20);
    verify(records).list(eq(STORE_ID), eq("2026-09"), any());

    when(records.cost(STORE_ID, "c")).thenReturn(cost());
    assertThat(service.get(STORE_ID, "c").storeId()).isEqualTo(STORE_ID);

    assertThat(service.month(STORE_ID, "2026-09").storeId()).isEqualTo(STORE_ID);
    verify(records).totals(STORE_ID, "2026-09");
    verify(records).month(STORE_ID, "2026-09");
  }

  @Test
  void createLocksReadsAndStampsEveryRowInTheGivenStore() {
    var key = UUID.randomUUID();
    when(actors.requireUserId("actor")).thenReturn(42L);
    when(records.receipt(STORE_ID, 42L, key)).thenReturn(Optional.empty());
    doAnswer(
            invocation -> {
              Object row = invocation.getArgument(0);
              ReflectionTestUtils.setField(row, "version", 0L);
              return null;
            })
        .when(records)
        .persist(any());

    var created =
        service.create(
            STORE_ID,
            new CreateRequest("2026-09", AdvertisingCategory.SALES, "媒体", null, null, 1, 100, key),
            "actor");

    assertThat(created.storeId()).isEqualTo(STORE_ID);
    verify(records).lock("request:" + STORE_ID + ":42:" + key);
    verify(records).receipt(STORE_ID, 42L, key);
    verify(records).lockMonth(STORE_ID, "2026-09");
    verify(records).advance(STORE_ID, "2026-09");
    var persisted = ArgumentCaptor.forClass(Object.class);
    verify(records, times(3)).persist(persisted.capture());
    assertThat(persisted.getAllValues())
        .hasExactlyElementsOfTypes(
            AdvertisingCost.class, AdvertisingChange.class, AdvertisingRequest.class);
    assertThat(persisted.getAllValues())
        .allSatisfy(row -> assertThat(((StoreScopedEntity) row).getStoreId()).isEqualTo(STORE_ID));
    verify(audit)
        .record(
            eq("actor"),
            eq(STORE_ID),
            eq("ADVERTISING_COST_CREATED"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  private AdvertisingCost cost() {
    var cost =
        AdvertisingCost.builder()
            .month("2026-09")
            .values(new AdvertisingValues(AdvertisingCategory.SALES, "媒体", null, null, 1, 100))
            .build();
    cost.setStoreId(STORE_ID);
    ReflectionTestUtils.setField(cost, "version", 0L);
    return cost;
  }
}
