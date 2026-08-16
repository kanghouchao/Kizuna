package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
  @DisplayName("墓標を渡されたら、統合先を別問い合わせで読んで存続行を返すこと")
  void resolveFollowsTheMergeTargetReadWithASeparateQuery() {
    // 悲観排他ロックは既に永続化文脈に載っている実体の状態を更新しない。統合先を持たない実体を
    // 返しつつ問い合わせだけが統合先を答える形で、実体ではなく問い合わせを読んでいることを固定する
    // （この規律は経路が実体を先に載せるかどうかに依らず、解決口の契約そのものである）。
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().name("統合先を持たない実体").build()));
    Mockito.when(customerRepository.findByIdForUpdate(SURVIVING_CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
    Mockito.when(customerRepository.findMergedIntoId(CUSTOMER_ID))
        .thenReturn(Optional.of(SURVIVING_CUSTOMER_ID));

    assertThat(resolver.resolveForWrite(CUSTOMER_ID)).isEqualTo(SURVIVING_CUSTOMER_ID);
  }

  @Test
  @DisplayName("統合先へ向け直すときは、着地する存続行も押さえること")
  void resolveLocksTheSurvivingRowAsWell() {
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
    Mockito.when(customerRepository.findByIdForUpdate(SURVIVING_CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().build()));
    Mockito.when(customerRepository.findMergedIntoId(CUSTOMER_ID))
        .thenReturn(Optional.of(SURVIVING_CUSTOMER_ID));

    resolver.resolveForWrite(CUSTOMER_ID);

    // 押さえるのは書き込みが着地する行。押さえないと、存続行を更に統合する要求が付替えを
    // 済ませた後にこの書き込みが割り込み、付替えの済んだ行へ着いたまま取り残される。
    InOrder locks = Mockito.inOrder(customerRepository);
    locks.verify(customerRepository).findByIdForUpdate(CUSTOMER_ID);
    locks.verify(customerRepository).findByIdForUpdate(SURVIVING_CUSTOMER_ID);
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
