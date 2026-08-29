package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.point.domain.InsufficientPointBalanceException;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointAllocationRepository;
import com.kizuna.point.domain.PointConsumption;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.point.domain.PointRollback;
import com.kizuna.point.domain.PointRollbackRepository;
import com.kizuna.settings.application.PointSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointLedgerServiceTest {

  private static final long MEMBER_ID = 7L;
  private static final long STORE_ID = 3L;
  private static final long ACTOR_ID = 9L;
  private static final long SAVED_ENTRY_ID = 41L;
  private static final LocalDate FAR_FUTURE = LocalDate.of(2099, 12, 31);
  private static final String TIMEZONE = "Asia/Tokyo";

  @Mock private PointEntryRepository pointEntryRepository;
  @Mock private PointAllocationRepository pointAllocationRepository;
  @Mock private PointRollbackRepository pointRollbackRepository;
  @Mock private SystemConfigService systemConfigService;
  @Mock private AppProperties appProperties;

  @InjectMocks private PointLedgerService pointLedgerService;

  @Captor private ArgumentCaptor<PointEntry> savedEntry;
  @Captor private ArgumentCaptor<PointRollback> savedRollback;

  @BeforeEach
  void stubBusinessTimezone() {
    lenient().when(appProperties.getTimezone()).thenReturn(TIMEZONE);
    lenient().when(pointEntryRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    // 記帳は保存された行の ID を返す（付与はそれを昇格判定の根拠として渡す）。永続化層の採番を写す。
    lenient()
        .when(pointEntryRepository.save(any()))
        .thenAnswer(
            invocation -> {
              PointEntry saved = invocation.getArgument(0);
              saved.setId(SAVED_ENTRY_ID);
              return saved;
            });
  }

  @Test
  @DisplayName("付与は単位金額で切り捨てた回数ぶんだけ計算されること")
  void previewGrantFloorsByUnitAmount() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 1, 100));

    assertThat(pointLedgerService.previewGrant(12345)).isEqualTo(123);
    assertThat(pointLedgerService.previewGrant(99)).isZero();
  }

  @Test
  @DisplayName("付与設定が未投入なら付与 0 になること（受注完了を落とさない）")
  void previewGrantDegradesToZeroWhenMisconfigured() {
    when(systemConfigService.pointSettings())
        .thenReturn(new PointSettings(0, 1, 100))
        .thenReturn(new PointSettings(100, 0, 100));

    assertThat(pointLedgerService.previewGrant(12345)).isZero();
    assertThat(pointLedgerService.previewGrant(12345)).isZero();
  }

  @Test
  @DisplayName("int の範囲を超える付与は、回り込んだ値を返さずに拒否されること")
  void previewGrantRejectsOverflowingGrant() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(1, 2, 100));

    assertThatThrownBy(() -> pointLedgerService.previewGrant(Integer.MAX_VALUE))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("付与ポイント");
  }

  @Test
  @DisplayName("int の範囲に収まる付与はそのまま返ること")
  void previewGrantAllowsTheLargestRepresentableGrant() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(1, 1, 100));

    assertThat(pointLedgerService.previewGrant(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  @DisplayName("付与が 0 なら台帳へ何も書かないこと")
  void grantForOrderWritesNothingWhenZero() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 1, 100));

    assertThat(pointLedgerService.grantForOrder(MEMBER_ID, "o1", STORE_ID, 99, ACTOR_ID).points())
        .isZero();
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("付与は ORDER_GRANT の加算仕訳として記録されること")
  void grantForOrderSavesCreditEntry() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 2, 100));

    assertThat(pointLedgerService.grantForOrder(MEMBER_ID, "o1", STORE_ID, 12345, ACTOR_ID))
        .isEqualTo(new PointLedgerService.GrantedPoints(246, SAVED_ENTRY_ID));

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.ORDER_GRANT);
    assertThat(entry.getAmount()).isEqualTo(246);
    assertThat(entry.getOrderId()).isEqualTo("o1");
    assertThat(entry.getOriginatingStoreId()).isEqualTo(STORE_ID);
  }

  @Test
  @DisplayName("付与の純額は台帳へそのまま問い合わせ、返った合計を丸めずに渡すこと")
  void netGrantedPointsForDelegatesToTheLedger() {
    when(pointEntryRepository.sumNetOrderGrants(MEMBER_ID)).thenReturn(4200L);

    assertThat(pointLedgerService.netGrantedPointsFor(MEMBER_ID)).isEqualTo(4200L);
  }

  @Test
  @DisplayName("事後申領の付与は、渡された確定額をそのまま記帳し付与設定を読み直さないこと")
  void grantPlannedForOrderBooksTheGivenAmountVerbatim() {
    // 額は完了時点の規則で確定している。ここで読み直すと、同じ会計が申領の早い遅いで別のポイントになる
    pointLedgerService.grantPlannedForOrder(MEMBER_ID, "o1", STORE_ID, 120, ACTOR_ID);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.ORDER_GRANT);
    assertThat(entry.getAmount()).isEqualTo(120);
    assertThat(entry.getOrderId()).isEqualTo("o1");
    assertThat(entry.getOriginatingStoreId()).isEqualTo(STORE_ID);
    assertThat(entry.getActorUserId()).isEqualTo(ACTOR_ID);
    verify(systemConfigService, never()).pointSettings();
  }

  @Test
  @DisplayName("確定額 0 の事後申領は台帳へ何も書かないこと")
  void grantPlannedForOrderWritesNothingWhenZero() {
    // 0 円完了の伝票。申領の効果は来店の可視化に閉じ、台帳には行を作らない
    pointLedgerService.grantPlannedForOrder(MEMBER_ID, "o1", STORE_ID, 0, ACTOR_ID);

    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("利用単位の設定が 0 以下なら 1 として扱うこと")
  void usageUnitNormalizesNonPositive() {
    when(systemConfigService.pointSettings())
        .thenReturn(new PointSettings(100, 1, 0))
        .thenReturn(new PointSettings(100, 1, 500));

    assertThat(pointLedgerService.usageUnit()).isEqualTo(1);
    assertThat(pointLedgerService.usageUnit()).isEqualTo(500);
  }

  @Test
  @DisplayName("利用単位の倍数でないポイント利用は拒否されること")
  void useForOrderRejectsNonMultipleOfUnit() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 1, 100));

    assertThatThrownBy(
            () -> pointLedgerService.useForOrder(MEMBER_ID, "o1", STORE_ID, 150, ACTOR_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("100 ポイント単位");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("0 以下のポイント利用は拒否されること")
  void useForOrderRejectsNonPositive() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 1, 100));

    assertThatThrownBy(() -> pointLedgerService.useForOrder(MEMBER_ID, "o1", STORE_ID, 0, ACTOR_ID))
        .isInstanceOf(ServiceException.class);
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("ポイント利用は行ロック付きで読んだロットから期限の早い順に引き当てること")
  void useForOrderConsumesLockedLotsInExpiryOrder() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 1, 100));
    when(pointEntryRepository.findCreditsForUpdate(MEMBER_ID))
        .thenReturn(List.of(credit(1L, 100, null), credit(2L, 300, FAR_FUTURE)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(1L, 2L)))
        .thenReturn(List.of());

    pointLedgerService.useForOrder(MEMBER_ID, "o1", STORE_ID, 400, ACTOR_ID);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.USE);
    assertThat(entry.getAmount()).isEqualTo(-400);
    assertThat(entry.getAllocations())
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(2L, 300), tuple(1L, 100));
  }

  @Test
  @DisplayName("残高が足りないポイント利用は台帳へ何も書かずに拒否されること")
  void useForOrderRejectsWhenBalanceInsufficient() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 1, 100));
    when(pointEntryRepository.findCreditsForUpdate(MEMBER_ID))
        .thenReturn(List.of(credit(1L, 100, null)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(1L))).thenReturn(List.of());

    assertThatThrownBy(
            () -> pointLedgerService.useForOrder(MEMBER_ID, "o1", STORE_ID, 200, ACTOR_ID))
        .isInstanceOf(InsufficientPointBalanceException.class)
        .hasMessageContaining("残高: 100");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("増減 0 の手動調整は拒否されること")
  void adjustRejectsZeroDelta() {
    assertThatThrownBy(
            () -> pointLedgerService.adjust(MEMBER_ID, STORE_ID, 0, "理由", null, ACTOR_ID, "key-1"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("増減は 0 以外");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("加算の手動調整は冪等キーを持つ新しいロットとして記録されること")
  void adjustPositiveSavesNewLot() {
    pointLedgerService.adjust(MEMBER_ID, STORE_ID, 500, "お詫び", FAR_FUTURE, ACTOR_ID, "key-1");

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.MANUAL_ADJUST);
    assertThat(entry.getAmount()).isEqualTo(500);
    assertThat(entry.getExpiresOn()).isEqualTo(FAR_FUTURE);
    assertThat(entry.getIdempotencyKey()).isEqualTo("key-1");
    assertThat(entry.getAllocations()).isEmpty();
  }

  @Test
  @DisplayName("加算の手動調整に過去の有効期限は指定できないこと")
  void adjustPositiveRejectsPastExpiry() {
    LocalDate yesterday = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(1);

    assertThatThrownBy(
            () ->
                pointLedgerService.adjust(
                    MEMBER_ID, STORE_ID, 500, "お詫び", yesterday, ACTOR_ID, "key-1"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("過去の日付");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("有効期限が本日ちょうどの加算調整は記録されること")
  void adjustPositiveAllowsExpiryOfToday() {
    LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

    pointLedgerService.adjust(MEMBER_ID, STORE_ID, 500, "お詫び", today, ACTOR_ID, "key-1");

    verify(pointEntryRepository).save(savedEntry.capture());
    assertThat(savedEntry.getValue().getExpiresOn()).isEqualTo(today);
  }

  @Test
  @DisplayName("減算の手動調整に有効期限は指定できないこと")
  void adjustNegativeRejectsExpiry() {
    assertThatThrownBy(
            () ->
                pointLedgerService.adjust(
                    MEMBER_ID, STORE_ID, -500, "訂正", FAR_FUTURE, ACTOR_ID, "key-1"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("有効期限");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("減算の手動調整は行ロック付きで読んだロットから引き当てること")
  void adjustNegativeConsumesLockedLots() {
    when(pointEntryRepository.findCreditsForUpdate(MEMBER_ID))
        .thenReturn(List.of(credit(1L, 500, null)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(1L)))
        .thenReturn(List.of(consumption(1L, 200)));

    pointLedgerService.adjust(MEMBER_ID, STORE_ID, -300, "訂正", null, ACTOR_ID, "key-1");

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getAmount()).isEqualTo(-300);
    assertThat(entry.getReason()).isEqualTo("訂正");
    assertThat(entry.getAllocations())
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(1L, 300));
  }

  @Test
  @DisplayName("同一キー・同一内容の再送は記帳せずに戻ること")
  void adjustReplaysCommittedAdjustmentWithSameKey() {
    PointEntry committed = adjustEntry(11L, 500, "お詫び", FAR_FUTURE, "key-1");
    when(pointEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(committed));

    pointLedgerService.adjust(MEMBER_ID, STORE_ID, 500, "お詫び", FAR_FUTURE, ACTOR_ID, "key-1");

    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("同一キー・内容不一致の要求は初回の成立を明告して 409 になること")
  void adjustRejectsSameKeyWithDifferentContent() {
    PointEntry committed = adjustEntry(11L, 500, "お詫び", FAR_FUTURE, "key-1");
    when(pointEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(committed));

    assertThatThrownBy(
            () ->
                pointLedgerService.adjust(
                    MEMBER_ID, STORE_ID, 300, "お詫び", FAR_FUTURE, ACTOR_ID, "key-1"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("初回の調整は既に成立");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("再送の判定は入力検証より先に行われること（期限が過去になった再送も再送として扱う）")
  void adjustReplayPrecedesInputValidation() {
    LocalDate yesterday = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(1);
    PointEntry committed = adjustEntry(11L, 500, "お詫び", yesterday, "key-1");
    when(pointEntryRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(committed));

    pointLedgerService.adjust(MEMBER_ID, STORE_ID, 500, "お詫び", yesterday, ACTOR_ID, "key-1");

    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("残高照会はロックなしのロット取得を使うこと")
  void balanceReadsWithoutLocking() {
    when(pointEntryRepository.findCredits(MEMBER_ID))
        .thenReturn(List.of(credit(1L, 500, null), credit(2L, 100, LocalDate.of(2000, 1, 1))));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(1L, 2L)))
        .thenReturn(List.of(consumption(1L, 200)));

    assertThat(pointLedgerService.balance(MEMBER_ID)).isEqualTo(300);
    verify(pointEntryRepository, never()).findCreditsForUpdate(anyLong());
  }

  @Test
  @DisplayName("ロットが 1 件も無ければ引き当て合計は照会しないこと")
  void balanceSkipsAllocationQueryWhenNoCredits() {
    when(pointEntryRepository.findCredits(MEMBER_ID)).thenReturn(List.of());

    assertThat(pointLedgerService.balance(MEMBER_ID)).isZero();
    verify(pointAllocationRepository, never()).findConsumedBySourceEntryIds(any());
  }

  @Test
  @DisplayName("期限の判定は設定のタイムゾーンの本日で行うこと")
  void balanceJudgesExpiryInTheConfiguredTimezone() {
    // 日界線の両端（UTC-12 と UTC+14）は「本日」が常に 1 日以上ずれるため、同じロットの可否が
    // タイムゾーンだけで分かれる。JVM のタイムゾーンで判定していると両者が同じ結果になる。
    LocalDate todayAtDateLineWest = LocalDate.now(ZoneId.of("Etc/GMT+12"));
    when(pointEntryRepository.findCredits(MEMBER_ID))
        .thenReturn(List.of(credit(1L, 500, todayAtDateLineWest)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(1L))).thenReturn(List.of());
    when(appProperties.getTimezone()).thenReturn("Etc/GMT+12").thenReturn("Pacific/Kiritimati");

    assertThat(pointLedgerService.balance(MEMBER_ID)).as("期限当日はまだ使える").isEqualTo(500);
    assertThat(pointLedgerService.balance(MEMBER_ID)).as("期限を過ぎたロットは残高に入らない").isZero();
  }

  @Test
  @DisplayName("巻き戻しは、その受注を根拠とするすべての付与を理由付きで打ち消すこと")
  void rollbackCancelsEveryGrantOfTheOrder() {
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500), orderGrant(12L, "o1", 300)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L, 12L)))
        .thenReturn(List.of());

    assertThat(pointLedgerService.rollbackForOrder("o1", "誤完了", ACTOR_ID))
        .isEqualTo(new PointLedgerService.PointRollbackResult(800, 0));

    verify(pointEntryRepository, times(2)).save(savedEntry.capture());
    assertThat(savedEntry.getAllValues())
        .extracting(
            PointEntry::getEntryType,
            PointEntry::getAmount,
            PointEntry::getOriginalEntryId,
            PointEntry::getReason)
        .containsExactly(
            tuple(PointEntryType.CANCEL, -500, 11L, "誤完了"),
            tuple(PointEntryType.CANCEL, -300, 12L, "誤完了"));
  }

  @Test
  @DisplayName("消費し切った付与は飛ばし、残余のある付与だけを打ち消すこと")
  void rollbackSkipsDrainedGrants() {
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500), orderGrant(12L, "o1", 500)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L, 12L)))
        .thenReturn(List.of(consumption(11L, 500), consumption(12L, 200)));

    assertThat(pointLedgerService.rollbackForOrder("o1", "誤完了", ACTOR_ID).cancelledPoints())
        .isEqualTo(300);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getOriginalEntryId()).isEqualTo(12L);
    assertThat(entry.getAmount()).isEqualTo(-300);
    assertThat(entry.getAllocations())
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(12L, 300));
  }

  @Test
  @DisplayName("仕訳ゼロの受注でも操作記録は書かれること（事後申領を記録で拒むため）")
  void rollbackRecordsEvenWithoutEntries() {
    assertThat(pointLedgerService.rollbackForOrder("empty", "無帰属のまま清零", ACTOR_ID))
        .isEqualTo(new PointLedgerService.PointRollbackResult(0, 0));

    verify(pointRollbackRepository).save(savedRollback.capture());
    assertThat(savedRollback.getValue().getOrderId()).isEqualTo("empty");
    assertThat(savedRollback.getValue().getReason()).isEqualTo("無帰属のまま清零");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("同じ受注への二度目の巻き戻しは撥ねられ、台帳にも記録にも何も書かれないこと")
  void rollbackRejectsSecondAttempt() {
    when(pointRollbackRepository.existsByOrderId("o1")).thenReturn(true);

    assertThatThrownBy(() -> pointLedgerService.rollbackForOrder("o1", "二度目", ACTOR_ID))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("既に巻き戻されています");
    verify(pointRollbackRepository, never()).save(any());
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("利用は元のロットへ引き当てを逆転して返され、新しいロットを作らないこと")
  void rollbackReversesUseIntoTheOriginalLots() {
    when(pointEntryRepository.findUsesByOrderId("o1")).thenReturn(List.of(use(21L, "o1", 300, 1L)));

    assertThat(pointLedgerService.rollbackForOrder("o1", "誤完了", ACTOR_ID).restoredPoints())
        .isEqualTo(300);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.USE_CANCEL);
    assertThat(entry.getAmount()).as("逆転は加算").isEqualTo(300);
    assertThat(entry.getOriginalEntryId()).isEqualTo(21L);
    assertThat(entry.getExpiresOn()).as("自身はロットにならないので期限を持たない").isNull();
    assertThat(entry.getOrderId()).as("受注ごとの付与合計へ混ざらない").isNull();
    assertThat(entry.getAllocations())
        .as("元の利用が引いたロットへ同量を返す")
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(1L, 300));
  }

  @Test
  @DisplayName("既に逆転済みの利用は二度逆転されないこと")
  void rollbackSkipsAlreadyReversedUses() {
    when(pointEntryRepository.findUsesByOrderId("o1")).thenReturn(List.of(use(21L, "o1", 300, 1L)));
    when(pointEntryRepository.findReversedUseIds(List.of(21L))).thenReturn(List.of(21L));

    assertThat(pointLedgerService.rollbackForOrder("o1", "誤完了", ACTOR_ID).restoredPoints())
        .isZero();
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("利用の逆転は付与の取消より先に行われること（逆順だと取り戻せる量が減る）")
  void rollbackReversesUsesBeforeCancellingGrants() {
    when(pointEntryRepository.findUsesByOrderId("o1"))
        .thenReturn(List.of(use(21L, "o1", 300, 11L)));
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500)));
    // 逆転を書き終えた後に数え直すので、消費は 300 返って 200 になる。
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L)))
        .thenReturn(List.of(consumption(11L, 200)));

    assertThat(pointLedgerService.rollbackForOrder("o1", "誤完了", ACTOR_ID))
        .isEqualTo(new PointLedgerService.PointRollbackResult(300, 300));

    verify(pointEntryRepository, times(2)).save(savedEntry.capture());
    assertThat(savedEntry.getAllValues())
        .extracting(PointEntry::getEntryType)
        .containsExactly(PointEntryType.USE_CANCEL, PointEntryType.CANCEL);
  }

  @Test
  @DisplayName("巻き戻しも未消費分を数える前に消費経路と同じ行ロックを取ること")
  void rollbackLocksBeforeCountingConsumption() {
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L)))
        .thenReturn(List.of());

    pointLedgerService.rollbackForOrder("o1", "誤完了", ACTOR_ID);

    InOrder inOrder = inOrder(pointEntryRepository, pointAllocationRepository);
    inOrder.verify(pointEntryRepository).findCreditsForUpdate(MEMBER_ID);
    inOrder.verify(pointAllocationRepository).findConsumedBySourceEntryIds(List.of(11L));
  }

  @Test
  @DisplayName("下見は動く見込みの量だけを返し、台帳へ何も書かないこと")
  void previewRollbackReportsWhatWouldMove() {
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L)))
        .thenReturn(List.of(consumption(11L, 200)));
    when(pointEntryRepository.findUsesByOrderId("o1")).thenReturn(List.of(use(21L, "o1", 300, 1L)));

    assertThat(pointLedgerService.previewRollbackForOrder("o1"))
        .isEqualTo(new PointLedgerService.PointRollbackPreview(false, 300, 300));
    verify(pointEntryRepository, never()).save(any());
  }

  private static PointEntry use(long id, String orderId, int points, long sourceEntryId) {
    PointEntry entry =
        PointEntry.useForOrder(
            MEMBER_ID,
            orderId,
            STORE_ID,
            points,
            List.of(PointAllocation.of(sourceEntryId, points)),
            ACTOR_ID);
    entry.setId(id);
    return entry;
  }

  private static PointEntry orderGrant(long id, String orderId, int amount) {
    PointEntry entry = PointEntry.grantForOrder(MEMBER_ID, orderId, STORE_ID, amount, ACTOR_ID);
    entry.setId(id);
    return entry;
  }

  private static PointEntry credit(long id, int amount, LocalDate expiresOn) {
    return adjustEntry(id, amount, "seed", expiresOn, "seed-key-" + id);
  }

  private static PointEntry adjustEntry(
      long id, int amount, String reason, LocalDate expiresOn, String idempotencyKey) {
    PointEntry entry =
        PointEntry.manualAdjust(
            MEMBER_ID, STORE_ID, amount, reason, expiresOn, List.of(), ACTOR_ID, idempotencyKey);
    entry.setId(id);
    return entry;
  }

  private static PointConsumption consumption(long sourceEntryId, long consumed) {
    return new PointConsumption() {
      @Override
      public Long getSourceEntryId() {
        return sourceEntryId;
      }

      @Override
      public Long getConsumed() {
        return consumed;
      }
    };
  }
}
