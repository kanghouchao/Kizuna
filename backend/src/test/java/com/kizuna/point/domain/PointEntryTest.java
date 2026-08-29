package com.kizuna.point.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointEntryTest {

  private static final LocalDate EXPIRES_ON = LocalDate.of(2027, 3, 31);

  @Test
  @DisplayName("受注付与は加算の仕訳になり、発生店舗と受注を帰属として持つこと")
  void grantForOrderIsCredit() {
    PointEntry entry = PointEntry.grantForOrder(7L, "o1", 3L, 120, 9L);

    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.ORDER_GRANT);
    assertThat(entry.getAmount()).isEqualTo(120);
    assertThat(entry.getMemberId()).isEqualTo(7L);
    assertThat(entry.getOrderId()).isEqualTo("o1");
    assertThat(entry.getOriginatingStoreId()).isEqualTo(3L);
    assertThat(entry.getActorUserId()).isEqualTo(9L);
    assertThat(entry.getExpiresOn()).isNull();
    assertThat(entry.getAllocations()).isEmpty();
  }

  @Test
  @DisplayName("0 以下の付与は記録できないこと")
  void nonPositiveGrantIsRejected() {
    assertThatThrownBy(() -> PointEntry.grantForOrder(7L, "o1", 3L, 0, 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("付与ポイントは 1 以上");
  }

  @Test
  @DisplayName("受注 ID の無い付与は記録できないこと")
  void grantWithoutOrderIdIsRejected() {
    assertThatThrownBy(() -> PointEntry.grantForOrder(7L, " ", 3L, 120, 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("受注 ID");
  }

  @Test
  @DisplayName("会員 ID の無い仕訳は記録できないこと")
  void missingMemberIdIsRejected() {
    assertThatThrownBy(() -> PointEntry.grantForOrder(null, "o1", 3L, 120, 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("会員 ID");
  }

  @Test
  @DisplayName("利用は負の増減になり、引き当ての合計が利用量と一致すること")
  void useForOrderIsDebitWithMatchingAllocations() {
    PointEntry entry =
        PointEntry.useForOrder(
            7L,
            "o1",
            3L,
            300,
            List.of(PointAllocation.of(11L, 100), PointAllocation.of(12L, 200)),
            9L);

    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.USE);
    assertThat(entry.getAmount()).isEqualTo(-300);
    assertThat(entry.getAllocations()).hasSize(2);
    assertThat(entry.getAllocations())
        .extracting(PointAllocation::getAmount)
        .containsExactly(100, 200);
  }

  @Test
  @DisplayName("引き当ての合計が利用量と一致しない仕訳は記録できないこと")
  void useWithMismatchedAllocationsIsRejected() {
    List<PointAllocation> allocations = List.of(PointAllocation.of(11L, 100));

    assertThatThrownBy(() -> PointEntry.useForOrder(7L, "o1", 3L, 300, allocations, 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("引き当ての合計");
  }

  @Test
  @DisplayName("引き当ての無い減算は記録できないこと")
  void debitWithoutAllocationsIsRejected() {
    assertThatThrownBy(() -> PointEntry.useForOrder(7L, "o1", 3L, 300, List.of(), 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("引き当ての合計");
  }

  @Test
  @DisplayName("受注 ID の無い利用は記録できないこと")
  void useWithoutOrderIdIsRejected() {
    List<PointAllocation> allocations = List.of(PointAllocation.of(11L, 300));

    assertThatThrownBy(() -> PointEntry.useForOrder(7L, null, 3L, 300, allocations, 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("受注 ID");
  }

  @Test
  @DisplayName("加算の手動調整は有効期限を持てて引き当てを持たないこと")
  void positiveManualAdjustCarriesExpiry() {
    PointEntry entry =
        PointEntry.manualAdjust(7L, 3L, 500, "お詫び", EXPIRES_ON, List.of(), 9L, "key-1");

    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.MANUAL_ADJUST);
    assertThat(entry.getAmount()).isEqualTo(500);
    assertThat(entry.getExpiresOn()).isEqualTo(EXPIRES_ON);
    assertThat(entry.getReason()).isEqualTo("お詫び");
    assertThat(entry.getIdempotencyKey()).isEqualTo("key-1");
    assertThat(entry.getAllocations()).isEmpty();
  }

  @Test
  @DisplayName("減算の手動調整は引き当てを伴い、有効期限を持てないこと")
  void negativeManualAdjustConsumesLots() {
    List<PointAllocation> allocations = List.of(PointAllocation.of(11L, 500));

    PointEntry entry =
        PointEntry.manualAdjust(7L, 3L, -500, "誤付与の訂正", null, allocations, 9L, "key-1");

    assertThat(entry.getAmount()).isEqualTo(-500);
    assertThat(entry.getAllocations()).hasSize(1);

    assertThatThrownBy(
            () ->
                PointEntry.manualAdjust(
                    7L, 3L, -500, "誤付与の訂正", EXPIRES_ON, allocations, 9L, "key-2"))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("有効期限");
  }

  @Test
  @DisplayName("理由の無い手動調整は記録できないこと")
  void manualAdjustWithoutReasonIsRejected() {
    assertThatThrownBy(
            () -> PointEntry.manualAdjust(7L, 3L, 500, " ", null, List.of(), 9L, "key-1"))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("理由");
  }

  @Test
  @DisplayName("冪等キーの無い手動調整は記録できないこと")
  void manualAdjustWithoutIdempotencyKeyIsRejected() {
    assertThatThrownBy(() -> PointEntry.manualAdjust(7L, 3L, 500, "理由", null, List.of(), 9L, null))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("冪等キー");
    assertThatThrownBy(() -> PointEntry.manualAdjust(7L, 3L, 500, "理由", null, List.of(), 9L, " "))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("冪等キー");
  }

  @Test
  @DisplayName("増減 0 の仕訳は記録できないこと")
  void zeroAmountIsRejected() {
    assertThatThrownBy(() -> PointEntry.manualAdjust(7L, 3L, 0, "理由", null, List.of(), 9L, "key-1"))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("増減が 0");
  }

  @Test
  @DisplayName("加算の仕訳は引き当てを持てないこと")
  void creditWithAllocationsIsRejected() {
    List<PointAllocation> allocations = List.of(PointAllocation.of(11L, 100));

    assertThatThrownBy(
            () -> PointEntry.manualAdjust(7L, 3L, 500, "理由", null, allocations, 9L, "key-1"))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("加算の仕訳は引き当てを持ちません");
  }

  @Test
  @DisplayName("取消は元の加算を指し、未消費分だけを打ち消すこと")
  void cancelDrainsAvailableOfOriginal() {
    PointEntry original = credit(11L, 500);

    PointEntry entry = PointEntry.cancel(original, 300, "巻き戻し", 9L);

    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.CANCEL);
    assertThat(entry.getAmount()).isEqualTo(-300);
    assertThat(entry.getOriginalEntryId()).isEqualTo(11L);
    assertThat(entry.getMemberId()).isEqualTo(original.getMemberId());
    assertThat(entry.getOriginatingStoreId()).isEqualTo(original.getOriginatingStoreId());
    assertThat(entry.getAllocations()).hasSize(1);
    assertThat(entry.getAllocations().get(0).getSourceEntryId()).isEqualTo(11L);
    assertThat(entry.getAllocations().get(0).getAmount()).isEqualTo(300);
  }

  @Test
  @DisplayName("未保存の仕訳は取り消せないこと（取消対象の ID が無い）")
  void cancelRequiresPersistedOriginal() {
    PointEntry original = PointEntry.grantForOrder(7L, "o1", 3L, 500, 9L);

    assertThatThrownBy(() -> PointEntry.cancel(original, 500, "巻き戻し", 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("取消対象の仕訳 ID");
  }

  @Test
  @DisplayName("減算の仕訳は取り消せないこと")
  void cancelRejectsDebit() {
    PointEntry debit =
        PointEntry.useForOrder(7L, "o1", 3L, 300, List.of(PointAllocation.of(11L, 300)), 9L);
    debit.setId(12L);

    assertThatThrownBy(() -> PointEntry.cancel(debit, 300, "巻き戻し", 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("加算の仕訳だけ");
  }

  @Test
  @DisplayName("消費済みの加算は取り消せないこと")
  void cancelRejectsDrainedCredit() {
    PointEntry original = credit(11L, 500);

    assertThatThrownBy(() -> PointEntry.cancel(original, 0, "巻き戻し", 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("既に消費済み");
  }

  @Test
  @DisplayName("失効と退会消去は引き当てを伴う減算になること")
  void expireAndWithdrawalClearAreDebits() {
    PointEntry expired = PointEntry.expire(7L, 200, List.of(PointAllocation.of(11L, 200)));
    PointEntry cleared =
        PointEntry.withdrawalClear(7L, 200, List.of(PointAllocation.of(11L, 200)), 9L);

    assertThat(expired.getEntryType()).isEqualTo(PointEntryType.EXPIRE);
    assertThat(expired.getAmount()).isEqualTo(-200);
    assertThat(expired.getActorUserId()).isNull();
    assertThat(cleared.getEntryType()).isEqualTo(PointEntryType.WITHDRAWAL_CLEAR);
    assertThat(cleared.getAmount()).isEqualTo(-200);
    assertThat(cleared.getActorUserId()).isEqualTo(9L);
  }

  @Test
  @DisplayName("失効・退会消去も引き当ての合計が減算量に一致しなければ記録できないこと")
  void systemDebitsRequireMatchingAllocations() {
    List<PointAllocation> allocations = List.of(PointAllocation.of(11L, 100));

    assertThatThrownBy(() -> PointEntry.expire(7L, 200, allocations))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("引き当ての合計");
    assertThatThrownBy(() -> PointEntry.withdrawalClear(7L, 200, allocations, 9L))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("引き当ての合計");
  }

  @Test
  @DisplayName("引き当ては 1 以上でなければ作れないこと")
  void allocationRequiresPositiveAmount() {
    assertThatThrownBy(() -> PointAllocation.of(11L, 0))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("引き当て量は 1 以上");
    assertThatThrownBy(() -> PointAllocation.of(null, 100))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("引き当て元の仕訳 ID");
  }

  @Test
  @DisplayName("引き当ての一覧は外から差し替えられないこと")
  void allocationsAreNotModifiableFromOutside() {
    PointEntry entry =
        PointEntry.useForOrder(7L, "o1", 3L, 300, List.of(PointAllocation.of(11L, 300)), 9L);

    assertThatThrownBy(() -> entry.getAllocations().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static PointEntry credit(long id, int amount) {
    PointEntry entry = PointEntry.grantForOrder(7L, "o1", 3L, amount, 9L);
    entry.setId(id);
    return entry;
  }
}
