package com.kizuna.medium;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.medium.api.MediumController;
import com.kizuna.medium.api.dto.MediumResponses.MediumOptionResponse;
import com.kizuna.medium.api.dto.MediumResponses.MediumSummary;
import com.kizuna.medium.application.MediumService;
import com.kizuna.medium.domain.MediumStatus;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MediumController.class)
@Import({MediumControllerTest.MethodSecurity.class, StoreScopeHolder.class})
class MediumControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean MediumService service;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @BeforeEach
  void setup() {
    when(stores.exists(anyLong())).thenReturn(true);
  }

  private static RequestPostProcessor permissions(String... permissions) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "actor",
            "unused",
            Arrays.stream(permissions).map(p -> new SimpleGrantedAuthority("PERM_" + p)).toList());
    TestSecurityContextHolder.setAuthentication(authentication);
    return request -> {
      request.setUserPrincipal(authentication);
      return request;
    };
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.header("X-Store-ID", "1").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  @Test
  void listShowsEveryStatusToViewersAndFiltersByStatus() throws Exception {
    when(service.list(1L, null))
        .thenReturn(List.of(new MediumSummary("m1", "停止中", MediumStatus.STOPPED, 3L)));

    mvc.perform(get("/media").header("X-Store-ID", "1").with(permissions("ADVERTISING_COST_VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value("m1"))
        .andExpect(jsonPath("$.items[0].status").value("STOPPED"))
        .andExpect(jsonPath("$.items[0].version").value(3));
    mvc.perform(
            get("/media")
                .param("status", "ACTIVE")
                .header("X-Store-ID", "1")
                .with(permissions("ADVERTISING_COST_VIEW")))
        .andExpect(status().isOk());
    verify(service).list(1L, MediumStatus.ACTIVE);
    mvc.perform(
            get("/media").header("X-Store-ID", "1").with(permissions("ADVERTISING_COST_MANAGE")))
        .andExpect(status().isForbidden());
  }

  @Test
  void optionsAreOpenToAnyOfTheThreeChoosersOnly() throws Exception {
    when(service.options(1L)).thenReturn(List.of(new MediumOptionResponse("m1", "ヘブン")));
    for (var permission : List.of("ADVERTISING_COST_VIEW", "ORDER_MANAGE", "RECRUITMENT_MANAGE")) {
      mvc.perform(get("/media/options").header("X-Store-ID", "1").with(permissions(permission)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].name").value("ヘブン"))
          .andExpect(jsonPath("$.items[0].status").doesNotExist());
    }
    mvc.perform(
            get("/media/options").header("X-Store-ID", "1").with(permissions("RECRUITMENT_VIEW")))
        .andExpect(status().isForbidden());
  }

  @Test
  void managersCreateRenameStopResumeAndDelete() throws Exception {
    var summary = new MediumSummary("m1", "新名", MediumStatus.ACTIVE, 1L);
    when(service.create(1L, "ヘブン")).thenReturn("m1");
    when(service.rename(1L, "m1", "新名", 0L)).thenReturn(summary);
    when(service.stop(1L, "m1", 1L)).thenReturn(summary);
    when(service.resume(1L, "m1", 2L)).thenReturn(summary);
    var manage = permissions("ADVERTISING_COST_MANAGE");

    mvc.perform(json(post("/media"), "{\"name\":\" ヘブン \"}").with(manage))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("m1"));
    mvc.perform(json(put("/media/m1"), "{\"name\":\"新名\",\"version\":0}").with(manage))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(json(post("/media/m1/stop"), "{\"version\":1}").with(manage))
        .andExpect(status().isOk());
    mvc.perform(json(post("/media/m1/resume"), "{\"version\":2}").with(manage))
        .andExpect(status().isOk());
    mvc.perform(delete("/media/m1").param("version", "3").header("X-Store-ID", "1").with(manage))
        .andExpect(status().isNoContent());
    verify(service).delete(1L, "m1", 3L);
  }

  @Test
  void everyChangeIsForbiddenWithoutManage() throws Exception {
    var view = permissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE", "RECRUITMENT_MANAGE");
    mvc.perform(json(post("/media"), "{\"name\":\"ヘブン\"}").with(view))
        .andExpect(status().isForbidden());
    mvc.perform(json(put("/media/m1"), "{\"name\":\"新名\",\"version\":0}").with(view))
        .andExpect(status().isForbidden());
    mvc.perform(json(post("/media/m1/stop"), "{\"version\":0}").with(view))
        .andExpect(status().isForbidden());
    mvc.perform(json(post("/media/m1/resume"), "{\"version\":0}").with(view))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/media/m1").param("version", "0").header("X-Store-ID", "1").with(view))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void blankOrTooLongNamesAndMissingVersionsAreBadRequests() throws Exception {
    var manage = permissions("ADVERTISING_COST_MANAGE");
    mvc.perform(json(post("/media"), "{\"name\":\"　 \"}").with(manage))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.name").exists());
    mvc.perform(json(post("/media"), "{\"name\":\"" + "長".repeat(101) + "\"}").with(manage))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.name").exists());
    mvc.perform(json(post("/media"), "{\"name\":\" " + "長".repeat(100) + " \"}").with(manage))
        .andExpect(status().isCreated());
    mvc.perform(json(put("/media/m1"), "{\"name\":\"新名\"}").with(manage))
        .andExpect(status().isBadRequest());
    mvc.perform(json(post("/media/m1/stop"), "{}").with(manage)).andExpect(status().isBadRequest());
    mvc.perform(delete("/media/m1").header("X-Store-ID", "1").with(manage))
        .andExpect(status().isBadRequest());
  }
}
