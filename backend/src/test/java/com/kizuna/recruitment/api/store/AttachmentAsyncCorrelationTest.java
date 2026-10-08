package com.kizuna.recruitment.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.recruitment.api.dto.AttachmentSummaryResponse;
import com.kizuna.recruitment.application.AttachmentService;
import com.kizuna.recruitment.application.AttachmentTransactions;
import com.kizuna.recruitment.infrastructure.AttachmentBodyReceiver;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScopeExecutor;
import com.kizuna.shared.web.RequestCorrelationFilter;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.async.WebAsyncTask;

class AttachmentAsyncCorrelationTest {
  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  void uploadAndRecoveryKeepTheirRequestIdAndReleaseWorkerContext(boolean recovery, boolean failure)
      throws Exception {
    var service = mock(AttachmentService.class);
    var receiver = mock(AttachmentBodyReceiver.class);
    var admission = mock(AttachmentBodyReceiver.Admission.class);
    var store = new StoreContext();
    var scope = new StoreScopeExecutor(store, id -> true);
    var controller = new AttachmentController(service, receiver, store, scope, new AppProperties());
    var filter = new RequestCorrelationFilter(store);
    var receivedId = new AtomicReference<String>();
    var storedId = new AtomicReference<String>();
    when(service.preflight(anyString(), nullable(String.class), anyString()))
        .thenReturn(UUID.randomUUID());
    when(receiver.admit(any())).thenReturn(admission);
    when(admission.receive(any()))
        .thenAnswer(
            invocation -> {
              receivedId.set(ThreadContext.get("requestId"));
              return new AttachmentBodyReceiver.Received(Path.of("unused"), "hash", "image/png");
            });
    when(service.upload(anyString(), nullable(String.class), any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              storedId.set(ThreadContext.get("requestId"));
              if (failure) throw new ServiceUnavailableException("非公開ストレージに接続できません");
              return new AttachmentTransactions.Completion(
                  true,
                  new AttachmentSummaryResponse("image", "image/png", 3, OffsetDateTime.now()));
            });
    var jwt =
        Jwt.withTokenValue("synthetic")
            .header("alg", "none")
            .subject("actor")
            .claim("storeScopeType", "SPECIFIC_STORES")
            .claim("storeIds", List.of(1L))
            .build();
    try (var worker = Executors.newSingleThreadExecutor()) {
      for (int attempt = 0; attempt < 2; attempt++) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var task = new AtomicReference<WebAsyncTask<ResponseEntity<AttachmentSummaryResponse>>>();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(
            request,
            response,
            (input, output) -> {
              store.setStoreId(1L);
              task.set(
                  recovery
                      ? controller.recover("a", "image", "key", request, () -> "actor")
                      : controller.upload("a", "key", request, () -> "actor"));
            });
        SecurityContextHolder.clearContext();
        assertThat(ThreadContext.get("requestId")).isNull();
        var result = worker.submit(task.get().getCallable());
        if (failure) {
          assertThatThrownBy(result::get).hasRootCauseInstanceOf(ServiceUnavailableException.class);
        } else {
          assertThat(result.get())
              .isInstanceOfSatisfying(
                  ResponseEntity.class,
                  entity ->
                      assertThat(entity.getStatusCode().value()).isEqualTo(recovery ? 200 : 201));
        }
        String expected = response.getHeader("X-Request-ID");
        assertThat(expected).isNotBlank();
        assertThat(receivedId.get()).isEqualTo(expected);
        assertThat(storedId.get()).isEqualTo(expected);
        assertThat(worker.submit(() -> ThreadContext.getImmutableContext()).get())
            .doesNotContainKeys("requestId", "storeId");
        assertThat(worker.submit(store::hasStoreId).get()).isFalse();
        assertThat(
                worker.submit(() -> SecurityContextHolder.getContext().getAuthentication()).get())
            .isNull();
      }
    } finally {
      SecurityContextHolder.clearContext();
      ThreadContext.clearAll();
      store.clear();
    }
  }
}
