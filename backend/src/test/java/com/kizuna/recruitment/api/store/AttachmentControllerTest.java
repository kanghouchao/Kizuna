package com.kizuna.recruitment.api.store;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.recruitment.api.dto.AttachmentPolicyResponse;
import com.kizuna.recruitment.api.dto.AttachmentUploadResponse;
import com.kizuna.recruitment.application.AttachmentService;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.infrastructure.AttachmentBodyReceiver;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeExecutor;
import com.kizuna.store.application.StoreActivationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AttachmentController.class)
@Import({AttachmentControllerTest.MethodSecurity.class, StoreContext.class, AppProperties.class})
class AttachmentControllerTest {
  @Test
  void exactOperationIncludesAllStatesOnlyBehindWritePermission() throws Exception {
    UUID key = UUID.randomUUID();
    for (var state : AttachmentUpload.Status.values()) {
      when(service.operation("a", key.toString()))
          .thenReturn(
              new AttachmentUploadResponse(
                  "u", key, state, "image/png", 3, OffsetDateTime.now(), null));
      mvc.perform(
              get("/store/applicants/a/attachment-operations/" + key)
                  .header("X-Role", "store")
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
    when(service.preflight("a", null, key))
        .thenThrow(new ServiceException("選考が終了した応募者には画像を追加できません"));
    mvc.perform(
            post("/store/applicants/a/attachments")
                .with(csrf())
                .header("X-Role", "store")
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
  void privateFailuresKeepCacheBoundaryAndBusyRetryHint() throws Exception {
    when(service.policy()).thenThrow(new ResourceBusyException("画像処理が混み合っています"));
    mvc.perform(
            get("/store/applicants/attachment-policy")
                .header("X-Role", "store")
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
  @MockitoBean StoreScopeExecutor scope;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreActivationService activation;

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
                  request(HttpMethod.valueOf(method), "/store/applicants/" + suffix)
                      .header("X-Role", "store")
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
              post("/store/applicants/a/attachments"),
              put("/store/applicants/a/attachment-uploads/x/content"))) {
        mvc.perform(
                request
                    .with(csrf())
                    .header("X-Role", "store")
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
                get("/store/applicants/a/" + suffix)
                    .header("X-Role", "store")
                    .header("X-Store-ID", "1")
                    .with(actor(authorities)))
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
    mvc.perform(
            get("/store/applicants/attachment-policy")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(read))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(false))
        .andExpect(jsonPath("$.bucket").doesNotExist())
        .andExpect(jsonPath("$.endpoint").doesNotExist());
    when(service.download("a", "x", true))
        .thenReturn(new AttachmentService.Download("image/png", 123, null));
    mvc.perform(
            head("/store/applicants/a/attachments/x/content")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(read))
        .andExpect(status().isOk())
        .andExpect(header().string("Accept-Ranges", "none"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"attachment-x.png\""))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }
}
