package com.kizuna.recruitment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.recruitment.api.dto.AttachmentPolicyResponse;
import com.kizuna.recruitment.api.dto.AttachmentSummaryResponse;
import com.kizuna.recruitment.api.dto.AttachmentUploadResponse;
import com.kizuna.recruitment.application.AttachmentService;
import com.kizuna.recruitment.application.AttachmentTransactions;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.infrastructure.AttachmentBodyReceiver;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AttachmentController.class)
@Import({
  AttachmentControllerTest.MethodSecurity.class,
  StoreScopeHolder.class,
  AppProperties.class
})
class AttachmentControllerTest {
  @Test
  void attachmentListsArePagesNewestFirst() throws Exception {
    var listed = ArgumentCaptor.forClass(Pageable.class);
    var uploading = ArgumentCaptor.forClass(Pageable.class);
    when(service.list(eq(1L), eq("a"), listed.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    when(service.uploads(eq(1L), eq("a"), uploading.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    for (String suffix : List.of("attachments", "attachment-uploads")) {
      mvc.perform(
              get("/applicants/a/" + suffix)
                  .header("X-Store-ID", "1")
                  .with(
                      actor(
                          new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                          new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_VIEW"),
                          new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_MANAGE"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total_elements").value(0));
    }
    var newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    assertThat(listed.getValue().getSort()).isEqualTo(newestFirst);
    assertThat(uploading.getValue().getSort()).isEqualTo(newestFirst);
  }

  @Test
  void exactOperationIncludesAllStatesOnlyBehindWritePermission() throws Exception {
    UUID key = UUID.randomUUID();
    for (var state : AttachmentUpload.Status.values()) {
      when(service.operation(1L, "a", key.toString()))
          .thenReturn(
              new AttachmentUploadResponse(
                  "u", key, state, "image/png", 3, OffsetDateTime.now(), null));
      mvc.perform(
              get("/applicants/a/attachment-operations/" + key)
                  .header("X-Store-ID", "1")
                  .with(
                      actor(
                          new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                          new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_VIEW"),
                          new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_MANAGE"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("u"))
          .andExpect(jsonPath("$.idempotency_key").value(key.toString()))
          .andExpect(jsonPath("$.status").value(state.name()))
          .andExpect(jsonPath("$.failure_code").doesNotExist())
          .andExpect(jsonPath("$.object_id").doesNotExist())
          .andExpect(jsonPath("$.original_sha256").doesNotExist())
          .andExpect(jsonPath("$.created_by").doesNotExist())
          .andExpect(header().string("Cache-Control", "private, no-store"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
  }

  @Test
  void terminalPreflightFailureDoesNotAcquireOrReadBody() throws Exception {
    String key = "b61c89c9-9272-4ff5-a30a-e8f27f11c27c";
    when(service.preflight(1L, "a", null, key))
        .thenThrow(new ServiceException("選考が終了した応募者には画像を追加できません"));
    mvc.perform(
            post("/applicants/a/attachments")
                .header("X-Store-ID", "1")
                .header("Idempotency-Key", key)
                .contentType("image/svg+xml")
                .content("synthetic")
                .with(
                    actor(
                        new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                        new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_VIEW"),
                        new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_MANAGE"))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(receiver);
  }

  @Test
  void asyncUploadCarriesTheNamedStoreIntoTheWorkerAndService() throws Exception {
    UUID key = UUID.randomUUID();
    Path received = Path.of("synthetic");
    var admission = mock(AttachmentBodyReceiver.Admission.class);
    when(service.preflight(7L, "a", null, key.toString())).thenReturn(key);
    when(receiver.admit(any())).thenReturn(admission);
    when(admission.receive(any()))
        .thenReturn(new AttachmentBodyReceiver.Received(received, "hash", "image/png"));
    when(service.upload(7L, "a", null, key, received, "hash", "image/png", "actor"))
        .thenReturn(
            new AttachmentTransactions.Completion(
                true,
                new AttachmentSummaryResponse("image", "image/png", 3, OffsetDateTime.now())));
    var started =
        mvc.perform(
                post("/applicants/a/attachments")
                    .header("X-Store-ID", "7")
                    .header("Idempotency-Key", key.toString())
                    .contentType("image/png")
                    .content(new byte[] {1})
                    .with(
                        actor(
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_MANAGE"))))
            .andExpect(request().asyncStarted())
            .andReturn();
    mvc.perform(asyncDispatch(started)).andExpect(status().isCreated());
    verify(service).upload(7L, "a", null, key, received, "hash", "image/png", "actor");
  }

  @Test
  void privateFailuresKeepCacheBoundaryAndBusyRetryHint() throws Exception {
    when(service.policy()).thenThrow(new ResourceBusyException("画像処理が混み合っています"));
    mvc.perform(
            get("/applicants/attachment-policy")
                .header("X-Store-ID", "1")
                .with(
                    actor(
                        new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                        new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_VIEW"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(header().string("Cache-Control", "private, no-store"));
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean AttachmentService service;
  @MockitoBean AttachmentBodyReceiver receiver;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  private static RequestPostProcessor actor(SimpleGrantedAuthority... authorities) {
    var authentication =
        new UsernamePasswordAuthenticationToken("actor", "unused", List.of(authorities));
    TestSecurityContextHolder.setAuthentication(authentication);
    return request -> {
      request.setUserPrincipal(authentication);
      return request;
    };
  }

  @BeforeEach
  void setup() {
    when(stores.exists(anyLong())).thenReturn(true);
  }

  @Test
  void readRequiresBothRecruitmentAndAttachmentPermissionsIncludingHead() throws Exception {
    for (String permission :
        List.of(
            "CAST_MANAGE",
            "RECRUITMENT_VIEW",
            "RECRUITMENT_ATTACHMENT_VIEW",
            "RECRUITMENT_ATTACHMENT_MANAGE")) {
      for (String suffix :
          List.of("attachment-policy", "a/attachments", "a/attachments/x/content")) {
        for (String method : List.of("GET", "HEAD")) {
          mvc.perform(
                  request(HttpMethod.valueOf(method), "/applicants/" + suffix)
                      .header("X-Store-ID", "1")
                      .with(actor(new SimpleGrantedAuthority("PERM_" + permission))))
              .andExpect(status().isForbidden());
        }
      }
    }
    verifyNoInteractions(service, receiver);
  }

  @Test
  void recoveryAndInitialWriteNeedAllThreePermissionsBeforeReadingBody() throws Exception {
    for (String missing :
        List.of(
            "RECRUITMENT_VIEW", "RECRUITMENT_ATTACHMENT_VIEW", "RECRUITMENT_ATTACHMENT_MANAGE")) {
      var authorities =
          List.of(
                  "RECRUITMENT_VIEW",
                  "RECRUITMENT_ATTACHMENT_VIEW",
                  "RECRUITMENT_ATTACHMENT_MANAGE")
              .stream()
              .filter(value -> !value.equals(missing))
              .map(value -> new SimpleGrantedAuthority("PERM_" + value))
              .toArray(SimpleGrantedAuthority[]::new);
      for (var request :
          List.of(
              post("/applicants/a/attachments"),
              put("/applicants/a/attachment-uploads/x/content"))) {
        mvc.perform(
                request
                    .header("X-Store-ID", "1")
                    .contentType("image/png")
                    .content(new byte[] {1})
                    .with(actor(authorities)))
            .andExpect(status().isForbidden());
      }
      for (String suffix :
          List.of(
              "attachment-uploads", "attachment-operations/6d5ff5a4-4817-4ac5-9e76-2756f086e02b")) {
        mvc.perform(
                get("/applicants/a/" + suffix).header("X-Store-ID", "1").with(actor(authorities)))
            .andExpect(status().isForbidden())
            .andExpect(header().string("Cache-Control", "private, no-store"));
      }
    }
    verifyNoInteractions(service, receiver);
  }

  @Test
  void disabledPolicyContainsNoStorageConfigurationAndHeadHasSafeDisposition() throws Exception {
    when(service.policy())
        .thenReturn(
            new AttachmentPolicyResponse(
                false, List.of("image/jpeg", "image/png"), 10, 20, 30, 40, 20, 50));
    var read =
        actor(
            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
            new SimpleGrantedAuthority("PERM_RECRUITMENT_ATTACHMENT_VIEW"));
    mvc.perform(get("/applicants/attachment-policy").header("X-Store-ID", "1").with(read))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(false))
        .andExpect(jsonPath("$.bucket").doesNotExist())
        .andExpect(jsonPath("$.endpoint").doesNotExist());
    when(service.download(1L, "a", "x", true))
        .thenReturn(new AttachmentService.Download("image/png", 123, null));
    mvc.perform(head("/applicants/a/attachments/x/content").header("X-Store-ID", "1").with(read))
        .andExpect(status().isOk())
        .andExpect(header().string("Accept-Ranges", "none"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"attachment-x.png\""))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }
}
