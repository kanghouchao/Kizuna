package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.point.domain.InsufficientPointBalanceException;
import com.kizuna.point.domain.InvalidPointEntryException;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointAllocationRepository;
import com.kizuna.point.domain.PointConsumption;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.settings.application.PointSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
  private static final LocalDate FAR_FUTURE = LocalDate.of(2099, 12, 31);

  @Mock private PointEntryRepository pointEntryRepository;
  @Mock private PointAllocationRepository pointAllocationRepository;
  @Mock private SystemConfigService systemConfigService;

  @InjectMocks private PointLedgerService pointLedgerService;

  @Captor private ArgumentCaptor<PointEntry> savedEntry;

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
  @DisplayName("付与が 0 なら台帳へ何も書かないこと")
  void grantForOrderWritesNothingWhenZero() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 1, 100));

    assertThat(pointLedgerService.grantForOrder(MEMBER_ID, "o1", STORE_ID, 99, ACTOR_ID)).isZero();
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("付与は ORDER_GRANT の加算仕訳として記録されること")
  void grantForOrderSavesCreditEntry() {
    when(systemConfigService.pointSettings()).thenReturn(new PointSettings(100, 2, 100));

    assertThat(pointLedgerService.grantForOrder(MEMBER_ID, "o1", STORE_ID, 12345, ACTOR_ID))
        .isEqualTo(246);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.ORDER_GRANT);
    assertThat(entry.getAmount()).isEqualTo(246);
    assertThat(entry.getOrderId()).isEqualTo("o1");
    assertThat(entry.getOriginatingStoreId()).isEqualTo(STORE_ID);
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
            () -> pointLedgerService.adjust(MEMBER_ID, STORE_ID, 0, "理由", null, ACTOR_ID))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("増減は 0 以外");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("加算の手動調整は新しいロットとして記録されること")
  void adjustPositiveSavesNewLot() {
    pointLedgerService.adjust(MEMBER_ID, STORE_ID, 500, "お詫び", FAR_FUTURE, ACTOR_ID);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.MANUAL_ADJUST);
    assertThat(entry.getAmount()).isEqualTo(500);
    assertThat(entry.getExpiresOn()).isEqualTo(FAR_FUTURE);
    assertThat(entry.getAllocations()).isEmpty();
  }

  @Test
  @DisplayName("減算の手動調整に有効期限は指定できないこと")
  void adjustNegativeRejectsExpiry() {
    assertThatThrownBy(
            () -> pointLedgerService.adjust(MEMBER_ID, STORE_ID, -500, "訂正", FAR_FUTURE, ACTOR_ID))
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

    pointLedgerService.adjust(MEMBER_ID, STORE_ID, -300, "訂正", null, ACTOR_ID);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getAmount()).isEqualTo(-300);
    assertThat(entry.getReason()).isEqualTo("訂正");
    assertThat(entry.getAllocations())
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(1L, 300));
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
  @DisplayName("存在しない仕訳の取消は見つからないこと")
  void cancelRejectsUnknownEntry() {
    when(pointEntryRepository.findById(11L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> pointLedgerService.cancel(11L, ACTOR_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("取消は消費経路と同じ行ロックを取ってから未消費分だけを打ち消すこと")
  void cancelDrainsRemainingOfCredit() {
    when(pointEntryRepository.findById(11L)).thenReturn(Optional.of(credit(11L, 500, null)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L)))
        .thenReturn(List.of(consumption(11L, 200)));

    pointLedgerService.cancel(11L, ACTOR_ID);

    verify(pointEntryRepository).findCreditsForUpdate(MEMBER_ID);
    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.CANCEL);
    assertThat(entry.getAmount()).isEqualTo(-300);
    assertThat(entry.getOriginalEntryId()).isEqualTo(11L);
    assertThat(entry.getAllocations())
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(11L, 300));
  }

  @Test
  @DisplayName("消費し切った加算は取り消せないこと")
  void cancelRejectsDrainedCredit() {
    when(pointEntryRepository.findById(11L)).thenReturn(Optional.of(credit(11L, 500, null)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L)))
        .thenReturn(List.of(consumption(11L, 500)));

    assertThatThrownBy(() -> pointLedgerService.cancel(11L, ACTOR_ID))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("既に消費済み");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("減算の仕訳は取り消せないこと")
  void cancelRejectsDebitEntry() {
    PointEntry debit =
        PointEntry.useForOrder(
            MEMBER_ID, "o1", STORE_ID, 300, List.of(PointAllocation.of(1L, 300)), ACTOR_ID);
    debit.setId(12L);
    when(pointEntryRepository.findById(12L)).thenReturn(Optional.of(debit));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(12L)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> pointLedgerService.cancel(12L, ACTOR_ID))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("加算の仕訳だけ");
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("受注単位の取消は、その受注を根拠とするすべての付与を打ち消すこと")
  void cancelForOrderCancelsEveryGrantOfTheOrder() {
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500), orderGrant(12L, "o1", 300)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L, 12L)))
        .thenReturn(List.of());

    pointLedgerService.cancelForOrder("o1", ACTOR_ID);

    verify(pointEntryRepository, times(2)).save(savedEntry.capture());
    assertThat(savedEntry.getAllValues())
        .extracting(PointEntry::getEntryType, PointEntry::getAmount, PointEntry::getOriginalEntryId)
        .containsExactly(
            tuple(PointEntryType.CANCEL, -500, 11L), tuple(PointEntryType.CANCEL, -300, 12L));
  }

  @Test
  @DisplayName("消費し切った付与は飛ばし、残余のある付与だけを打ち消すこと")
  void cancelForOrderSkipsDrainedGrants() {
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500), orderGrant(12L, "o1", 500)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L, 12L)))
        .thenReturn(List.of(consumption(11L, 500), consumption(12L, 200)));

    pointLedgerService.cancelForOrder("o1", ACTOR_ID);

    verify(pointEntryRepository).save(savedEntry.capture());
    PointEntry entry = savedEntry.getValue();
    assertThat(entry.getOriginalEntryId()).isEqualTo(12L);
    assertThat(entry.getAmount()).isEqualTo(-300);
    assertThat(entry.getAllocations())
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(12L, 300));
  }

  @Test
  @DisplayName("付与の無い受注の取消は何もしないこと（存在しない受注 ID も同じ）")
  void cancelForOrderIsNoOpWithoutGrants() {
    when(pointEntryRepository.findCreditsByOrderId("unknown")).thenReturn(List.of());

    pointLedgerService.cancelForOrder("unknown", ACTOR_ID);

    verify(pointEntryRepository, never()).findCreditsForUpdate(anyLong());
    verify(pointAllocationRepository, never()).findConsumedBySourceEntryIds(any());
    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("受注単位の取消も未消費分を数える前に消費経路と同じ行ロックを取ること")
  void cancelForOrderLocksBeforeCountingConsumption() {
    when(pointEntryRepository.findCreditsByOrderId("o1"))
        .thenReturn(List.of(orderGrant(11L, "o1", 500)));
    when(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(11L)))
        .thenReturn(List.of());

    pointLedgerService.cancelForOrder("o1", ACTOR_ID);

    InOrder inOrder = inOrder(pointEntryRepository, pointAllocationRepository);
    inOrder.verify(pointEntryRepository).findCreditsForUpdate(MEMBER_ID);
    inOrder.verify(pointAllocationRepository).findConsumedBySourceEntryIds(List.of(11L));
  }

  private static PointEntry orderGrant(long id, String orderId, int amount) {
    PointEntry entry = PointEntry.grantForOrder(MEMBER_ID, orderId, STORE_ID, amount, ACTOR_ID);
    entry.setId(id);
    return entry;
  }

  private static PointEntry credit(long id, int amount, LocalDate expiresOn) {
    PointEntry entry =
        PointEntry.manualAdjust(
            MEMBER_ID, STORE_ID, amount, "seed", expiresOn, List.of(), ACTOR_ID);
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
