package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.point.application.PointLedgerService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AttributionMaterializerTransactionTest {
  @Test
  @DisplayName("包私有の入口も実際の Spring プロキシを通り、取引外では書き込み前に失敗すること")
  void rejectsInvocationWithoutATransactionThroughTheClassProxy() {
    var attributions = mock(OrderAttributionRepository.class);
    var ledger = mock(PointLedgerService.class);
    var benefits = mock(BenefitGrantService.class);
    var ranks = mock(MemberRankService.class);
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TransactionConfig.class);
      context.registerBean(TestTransactionManager.class);
      context.registerBean(
          AttributionMaterializer.class,
          () -> new AttributionMaterializer(attributions, ledger, benefits, ranks));
      context.refresh();
      var proxy = context.getBean(AttributionMaterializer.class);
      assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
      assertThatThrownBy(() -> materialize(proxy))
          .isInstanceOf(IllegalTransactionStateException.class);
      verifyNoInteractions(attributions, ledger, benefits, ranks);

      when(attributions.save(any()))
          .thenAnswer(
              invocation -> {
                OrderAttribution attribution = invocation.getArgument(0);
                attribution.setId(88L);
                return attribution;
              });
      when(ledger.grantPlannedForOrder(7L, "o1", 3L, 0, 10L)).thenReturn(null);
      var transactions = context.getBean(TestTransactionManager.class);
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              status -> {
                assertThat(materialize(proxy)).isEqualTo(new AttributionMaterializer.Result(0, 0));
              });
      assertThat(transactions.commits).isEqualTo(1);
      verify(ranks).lockForPromotion(7L);
    }
  }

  private static AttributionMaterializer.Result materialize(AttributionMaterializer proxy) {
    return proxy.materialize(
        7L,
        "123456789012",
        "o1",
        3L,
        LocalDate.of(2026, 8, 10),
        OffsetDateTime.parse("2026-09-01T12:00:00+09:00"),
        10L,
        new AttributionMaterializer.ReceiptClaim(0));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TransactionConfig {}

  static class TestTransactionManager extends AbstractPlatformTransactionManager {
    private boolean active;
    private int commits;

    @Override
    protected Object doGetTransaction() {
      return this;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
      return active;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      active = true;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
      active = false;
    }
  }
}
