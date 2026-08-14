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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerReferenceResolverTest {

  private static final String CUSTOMER_ID = "c1";

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
  @DisplayName("ロックした実体からは状態を読まないこと")
  void resolveDoesNotReadTheLockedEntity() {
    // 悲観排他ロックは既に永続化文脈に載っている実体の状態を更新しないため、ロック後に読んだ値は古い。
    // id を持たない実体を返しても解決結果が変わらないことで、実体を読んでいないことが判る。
    Mockito.when(customerRepository.findByIdForUpdate(CUSTOMER_ID))
        .thenReturn(Optional.of(Customer.builder().name("氏名だけの実体").build()));

    assertThat(resolver.resolveForWrite(CUSTOMER_ID)).isEqualTo(CUSTOMER_ID);
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
