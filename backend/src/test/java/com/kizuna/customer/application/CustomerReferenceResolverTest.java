package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class CustomerReferenceResolverTest {

  private static final String CUSTOMER_ID = "c1";
  private static final String SURVIVING_CUSTOMER_ID = "c2";

  @Mock private CustomerRepository customerRepository;

  @InjectMocks private CustomerReferenceResolver resolver;

  @Test
  @DisplayName("書き込み先の解決は顧客行を悲観排他ロックしてから返すこと")
  void resolveLocksTheCustomerRow() {
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));

    assertThat(resolver.resolveForWrite(CUSTOMER_ID)).isEqualTo(CUSTOMER_ID);

    // 存在確認だけの問い合わせでは、並行する統合・紐づけの書き換えと直列化しない
    Mockito.verify(customerRepository).findByIdForUpdate(CUSTOMER_ID);
    Mockito.verify(customerRepository, Mockito.never()).existsById(any());
    Mockito.verify(customerRepository, Mockito.never()).findById(any());
  }

  @Test
  @DisplayName("墓標を渡されたとき押さえるのは着地する存続行だけで、墓標は押さえないこと")
  void resolveLocksOnlyTheRowItLandsOn() {
    Mockito.when(customerRepository.findMergedIntoId(CUSTOMER_ID))
        .thenReturn(Optional.of(SURVIVING_CUSTOMER_ID));
    Mockito.when(customerRepository.findByIdForUpdate(SURVIVING_CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));

    assertThat(resolver.resolveForWrite(CUSTOMER_ID)).isEqualTo(SURVIVING_CUSTOMER_ID);

    // 墓標を押さえたまま統合先を待つと、その統合先を更に統合する要求（墓標の圧平で墓標の行を押さえる）
    // と待ちが環になる
    Mockito.verify(customerRepository, Mockito.never()).findByIdForUpdate(CUSTOMER_ID);
  }

  @Test
  @DisplayName("下見の後に統合が確定したら、統合先は待たずに取りに行くこと")
  void resolveChasesTheLateMergeWithoutWaiting() {
    // 下見の時点では生きていたので押さえに行き、押さえた時には墓標になっていた形。ここで待つと
    // 「墓標を押さえたまま統合先を待つ」形になり、圧平と待ちが環になる
    Mockito.when(customerRepository.findMergedIntoId(CUSTOMER_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(SURVIVING_CUSTOMER_ID));
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
    Mockito.when(customerRepository.findByIdForUpdateNoWait(SURVIVING_CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));

    assertThat(resolver.resolveForWrite(CUSTOMER_ID)).isEqualTo(SURVIVING_CUSTOMER_ID);

    Mockito.verify(customerRepository, Mockito.never()).findByIdForUpdate(SURVIVING_CUSTOMER_ID);
  }

  @Test
  @DisplayName("統合先を待たずに取れなかったら、やり直しの判る 409 になること")
  void resolveReportsAConflictWhenTheTargetIsHeldByAMerge() {
    Mockito.when(customerRepository.findMergedIntoId(CUSTOMER_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(SURVIVING_CUSTOMER_ID));
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
    Mockito.when(customerRepository.findByIdForUpdateNoWait(SURVIVING_CUSTOMER_ID))
        .thenThrow(new CannotAcquireLockException("statement timeout"));

    assertThatThrownBy(() -> resolver.resolveForWrite(CUSTOMER_ID))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("統合中の顧客です");
  }

  @Test
  @DisplayName("統合先の判定は押さえた実体からではなく別問い合わせで行うこと")
  void resolveReadsTheMergeTargetWithASeparateQuery() {
    // 悲観排他ロックは既に永続化文脈に載っている実体の状態を更新しない。統合先を持たない実体を
    // 押さえつつ問い合わせだけが統合先を答える形で、実体ではなく問い合わせを読んでいることを固定する。
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().name("統合先を持たない実体").build()));
    Mockito.when(customerRepository.findMergedIntoId(CUSTOMER_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(SURVIVING_CUSTOMER_ID));
    Mockito.when(customerRepository.findByIdForUpdateNoWait(SURVIVING_CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));

    assertThat(resolver.resolveForWrite(CUSTOMER_ID)).isEqualTo(SURVIVING_CUSTOMER_ID);
  }

  @Test
  @DisplayName("押さえられない顧客（不在・他店舗）は 404 で、存在の有無が漏れないこと")
  void resolveFailsWhenTheCustomerCannotBeLocked() {
    // 他店舗の顧客は storeFilter でこの問い合わせから落ちるため、不在と同じ応答になる
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.resolveForWrite(CUSTOMER_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("顧客が見つかりません");
  }
}
