package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.api.dto.AdvertisingRequests.CopyRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.CreateRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.ReplaceRequest;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CopyResponse;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingChangeRepository;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingMonth;
import com.kizuna.advertising.domain.AdvertisingValues;
import com.kizuna.advertising.infrastructure.AdvertisingRecords;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/** 広告費の service が引数の店舗で記録と媒体を引き、選べる媒体だけを受け付け、書き込みが受領を経由すること。受領の振る舞いは receipt モジュールの試験が持つ。 */
class AdvertisingServiceTest {
  static final Long STORE_ID = 7L;

  AdvertisingRecords records = mock(AdvertisingRecords.class);
  MediumLookup media = mock(MediumLookup.class);
  ActorIdentityService actors = mock(ActorIdentityService.class);
  BusinessAudit audit = mock(BusinessAudit.class);
  OperationReceipts operations = mock(OperationReceipts.class);
  AdvertisingService service =
      new AdvertisingService(
          records,
          media,
          mock(AdvertisingChangeRepository.class),
          operations,
          actors,
          audit,
          JsonMapper.builder().build(),
          Clock.systemUTC(),
          new AppProperties());

  @BeforeEach
  void setup() {
    when(actors.requireUserId("actor")).thenReturn(42L);
    doAnswer(
            invocation -> {
              Object row = invocation.getArgument(0);
              ReflectionTestUtils.setField(row, "id", "new");
              ReflectionTestUtils.setField(row, "version", 0L);
              if (row instanceof AdvertisingCost cost)
                when(records.recorded(STORE_ID, "new")).thenReturn(Optional.of(cost));
              return null;
            })
        .when(records)
        .persist(any());
    when(operations.once(any(), anyString(), any(), any()))
        .thenAnswer(
            call -> {
              Committed committed = call.<Supplier<Committed>>getArgument(2).get();
              return call.<Function<Receipt, Object>>getArgument(3)
                  .apply(
                      new Receipt(
                          "r",
                          committed.operation(),
                          committed.targetId(),
                          committed.facts(),
                          false));
            });
  }

  @Test
  void readsAskTheRecordsForTheGivenStore() {
    known(new MediumReference("m1", "現在名", true));
    when(records.list(eq(STORE_ID), eq("2026-09"), any()))
        .thenReturn(new PageImpl<>(List.of(cost("m1"))));
    assertThat(service.list(STORE_ID, "2026-09", PageRequest.of(0, 20)).getContent())
        .singleElement()
        .satisfies(c -> assertThat(c.medium().name()).isEqualTo("現在名"));
    verify(records).list(eq(STORE_ID), eq("2026-09"), any());

    when(records.cost(STORE_ID, "c")).thenReturn(cost("m1"));
    var read = service.get(STORE_ID, "c");
    assertThat(read.storeId()).isEqualTo(STORE_ID);
    assertThat(read.medium().id()).isEqualTo("m1");
    assertThat(read.medium().name()).isEqualTo("現在名");

    assertThat(service.month(STORE_ID, "2026-09").storeId()).isEqualTo(STORE_ID);
    verify(records).totals(STORE_ID, "2026-09");
    verify(records).month(STORE_ID, "2026-09");
  }

  @Test
  void createLocksReadsAndStampsEveryRowInTheGivenStore() {
    known(new MediumReference("m1", "媒体", true));
    var key = UUID.randomUUID();

    var created = service.create(STORE_ID, create("m1", key), "actor");

    assertThat(created.storeId()).isEqualTo(STORE_ID);
    assertThat(created.medium().name()).isEqualTo("媒体");
    verify(operations)
        .once(
            eq(new ReceiptKey(STORE_ID, 42L, key.toString())),
            eq(create("m1", key).fingerprint()),
            any(),
            any());
    verify(media, atLeastOnce()).findAll(STORE_ID, Set.of("m1"));
    verify(records).lockMonth(STORE_ID, "2026-09");
    verify(records).advance(STORE_ID, "2026-09");
    var persisted = ArgumentCaptor.forClass(Object.class);
    verify(records, times(2)).persist(persisted.capture());
    assertThat(persisted.getAllValues())
        .hasExactlyElementsOfTypes(AdvertisingCost.class, AdvertisingChange.class);
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

  @Test
  void createRejectsAMediumThatIsAbsentFromTheStore() {
    known();

    assertThatThrownBy(
            () -> service.create(STORE_ID, create("foreign", UUID.randomUUID()), "actor"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("媒体が見つかりません。一覧から選択してください");
    verify(records, never()).persist(any());
  }

  @Test
  void createRejectsAStoppedMedium() {
    known(new MediumReference("m1", "停止中", false));

    assertThatThrownBy(() -> service.create(STORE_ID, create("m1", UUID.randomUUID()), "actor"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("停止中の媒体は選択できません");
    verify(records, never()).persist(any());
  }

  @Test
  void aMediumDeletedAfterTheCheckIsAnInputErrorNotAServerError() {
    known(new MediumReference("m1", "有効", true));
    doThrow(
            new DataIntegrityViolationException(
                "fk",
                new ConstraintViolationException("fk", null, "fk_t_advertising_costs_medium")))
        .when(records)
        .flush();

    assertThatThrownBy(() -> service.create(STORE_ID, create("m1", UUID.randomUUID()), "actor"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("媒体が見つかりません。一覧から選択してください");
  }

  @Test
  void replaceRejectsAnAbsentMediumAndSwitchingToAnotherStoppedMedium() {
    known(new MediumReference("m1", "現在", true), new MediumReference("m2", "停止中", false));
    lockedCost("m1");

    assertThatThrownBy(() -> service.replace(STORE_ID, "c", replace("m2"), "actor"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("停止中の媒体は選択できません");
    assertThatThrownBy(() -> service.replace(STORE_ID, "c", replace("foreign"), "actor"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("媒体が見つかりません。一覧から選択してください");
    verify(records, never()).persist(any());
  }

  @Test
  void replaceKeepsTheCurrentStoppedMediumAndRecordsTheNamesAtThatMoment() {
    known(new MediumReference("m1", "停止中", false), new MediumReference("m2", "新媒体", true));
    lockedCost("m1");

    var kept = service.replace(STORE_ID, "c", replace("m1"), "actor");

    assertThat(kept.medium().id()).isEqualTo("m1");
    assertThat(kept.medium().name()).isEqualTo("停止中");
    var persisted = ArgumentCaptor.forClass(AdvertisingChange.class);
    verify(records).persist(persisted.capture());
    var change = persisted.getValue();
    assertThat(change.getBeforeValue()).contains("\"id\":\"m1\"", "\"name\":\"停止中\"");
    assertThat(change.getAfterValue()).contains("\"id\":\"m1\"", "\"name\":\"停止中\"");
  }

  @Test
  void copyCarriesAStoppedMediumAsIs() {
    known(new MediumReference("m1", "停止中", false));
    when(records.count(STORE_ID, "2026-10")).thenReturn(0L);
    when(records.all(eq(STORE_ID), eq("2026-09"), anyInt())).thenReturn(List.of(cost("m1")));
    when(records.advance(STORE_ID, "2026-10"))
        .thenReturn(AdvertisingMonth.builder().month("2026-10").build());

    var copied =
        service.copy(
            STORE_ID, "2026-10", new CopyRequest(0L, 0L, "引継ぎ", UUID.randomUUID()), "actor");

    assertThat(copied.copiedCount()).isEqualTo(1);
    var persisted = ArgumentCaptor.forClass(Object.class);
    verify(records, times(2)).persist(persisted.capture());
    assertThat(persisted.getAllValues().getFirst())
        .isInstanceOfSatisfying(
            AdvertisingCost.class, c -> assertThat(c.getMediumId()).isEqualTo("m1"));
  }

  @Test
  void aReplayReadsTheCurrentCostWithoutWriting() {
    known(new MediumReference("m1", "現在名", true));
    lockedCost("m1");
    replayed(new Receipt("r", "ADVERTISING_COST_UPDATED", "c", Map.of(), true));

    var current = service.replace(STORE_ID, "c", replace("m1"), "actor");

    assertThat(current.medium().name()).isEqualTo("現在名");
    verify(records, never()).lockMonth(any(), any());
    verify(records, never()).persist(any());
    verifyNoInteractions(audit);
  }

  @Test
  void aReplayedCopyAnswersFromTheCommittedFactsAlone() {
    replayed(
        new Receipt(
            "r",
            "ADVERTISING_COST_COPIED",
            "2026-10",
            Map.of(
                "copy_id",
                "copy",
                "copied_count",
                2,
                "source_version",
                3,
                "target_version",
                1,
                "created_at",
                "2026-10-01T00:00Z"),
            true));

    var copied =
        service.copy(
            STORE_ID, "2026-10", new CopyRequest(3L, 0L, "引継ぎ", UUID.randomUUID()), "actor");

    assertThat(copied)
        .isEqualTo(
            new CopyResponse(
                "copy",
                STORE_ID,
                "2026-09",
                "2026-10",
                2,
                3,
                1,
                OffsetDateTime.parse("2026-10-01T00:00Z")));
    verifyNoInteractions(records, media, audit);
  }

  private void replayed(Receipt receipt) {
    doAnswer(call -> call.<Function<Receipt, Object>>getArgument(3).apply(receipt))
        .when(operations)
        .once(any(), anyString(), any(), any());
  }

  private void known(MediumReference... references) {
    var byId =
        Arrays.stream(references)
            .collect(Collectors.toMap(MediumReference::id, Function.identity()));
    when(media.findAll(eq(STORE_ID), any()))
        .thenAnswer(
            invocation -> {
              Collection<String> ids = invocation.getArgument(1);
              return ids.stream()
                  .filter(byId::containsKey)
                  .collect(Collectors.toMap(Function.identity(), byId::get));
            });
  }

  private void lockedCost(String mediumId) {
    var cost = cost(mediumId);
    when(records.cost(STORE_ID, "c")).thenReturn(cost);
    when(records.refresh(cost)).thenReturn(cost);
    when(records.recorded(STORE_ID, "c")).thenReturn(Optional.of(cost));
  }

  private static CreateRequest create(String mediumId, UUID key) {
    return new CreateRequest(
        "2026-09", AdvertisingCategory.SALES, mediumId, null, null, 1, 100, key);
  }

  private static ReplaceRequest replace(String mediumId) {
    return new ReplaceRequest(
        AdvertisingCategory.SALES, mediumId, null, null, 1, 200, 0L, "修正", UUID.randomUUID());
  }

  private AdvertisingCost cost(String mediumId) {
    var cost =
        AdvertisingCost.builder()
            .month("2026-09")
            .values(new AdvertisingValues(AdvertisingCategory.SALES, mediumId, null, null, 1, 100))
            .build();
    cost.setStoreId(STORE_ID);
    ReflectionTestUtils.setField(cost, "id", "c");
    ReflectionTestUtils.setField(cost, "version", 0L);
    return cost;
  }
}
