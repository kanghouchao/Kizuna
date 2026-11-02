package com.kizuna.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.receipt.domain.OperationReceipt;
import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReceiptValuesTest {
  @Test
  void keyAndCommittedRejectWhatTheTableCannotHold() {
    assertThatThrownBy(() -> new ReceiptKey(1L, 2L, "x".repeat(121)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReceiptKey(1L, null, "key"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Committed("OP", " ", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new Committed("OP", "1", null).facts()).isNull();
  }

  @Test
  void factsReadBackAsLongWhicheverNumberTypeJsonGave() {
    var receipt = new Receipt("1", "OP", "9", Map.of("version", 3), true);
    assertThat(receipt.longFact("version")).isEqualTo(3L);
    assertThatThrownBy(() -> receipt.longFact("missing")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void recordKeepsTheKeyFingerprintAndCommittedFacts() {
    var row = OperationReceipt.record(1L, 2L, "key", "hash", "OP", "9", Map.of("v", 1L));
    assertThat(row)
        .extracting(
            OperationReceipt::getStoreId,
            OperationReceipt::getActorId,
            OperationReceipt::getOperationKey,
            OperationReceipt::getFingerprint,
            OperationReceipt::getOperation,
            OperationReceipt::getTargetId,
            OperationReceipt::getCommittedFacts)
        .containsExactly(1L, 2L, "key", "hash", "OP", "9", Map.of("v", 1L));
  }
}
