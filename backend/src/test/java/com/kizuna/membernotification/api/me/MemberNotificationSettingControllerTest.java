package com.kizuna.membernotification.api.me;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.membernotification.application.MemberNotificationSettingService;
import com.kizuna.membernotification.application.MemberNotificationSettings;
import com.kizuna.membernotification.domain.InvalidMemberNotificationSettingException;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 会員の通知設定の HTTP 契約: 会員本人だけが届き、応答は snake_case の実効値、選択は許可か拒否だけを受ける。 */
@WebMvcTest(MemberNotificationSettingController.class)
@Import({
  MemberNotificationSettingControllerTest.MethodSecurityConfig.class,
  CommonExceptionHandler.class,
  StoreScopeHolder.class
})
class MemberNotificationSettingControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String PATH = "/me/member/notification-settings";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberNotificationSettingService service;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("会員は自分の設定を実効値で読めること")
  void memberReadsOwnSettings() throws Exception {
    when(service.view(10L))
        .thenReturn(
            new MemberNotificationSettings(
                ContactPermissionStatus.UNKNOWN, ContactPermissionStatus.UNKNOWN));

    mockMvc
        .perform(get(PATH).with(member()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.business_status").value("UNKNOWN"))
        .andExpect(jsonPath("$.marketing_status").value("UNKNOWN"));
  }

  @Test
  @DisplayName("会員は用途ごとに許可・拒否を選べ、応答は変更後の設定全体であること")
  void memberChoosesPerPurpose() throws Exception {
    when(service.choose(10L, ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED))
        .thenReturn(
            new MemberNotificationSettings(
                ContactPermissionStatus.ALLOWED, ContactPermissionStatus.UNKNOWN));

    mockMvc
        .perform(choose("BUSINESS", "{\"status\":\"ALLOWED\"}").with(member()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.business_status").value("ALLOWED"))
        .andExpect(jsonPath("$.marketing_status").value("UNKNOWN"));
  }

  @Test
  @DisplayName("状態の欠落は 400 で、変更には到達しないこと")
  void missingStatusIsRejected() throws Exception {
    mockMvc
        .perform(choose("BUSINESS", "{}").with(member()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.status").exists());

    verify(service, never()).choose(anyLong(), any(), any());
  }

  @Test
  @DisplayName("未確認を選ぶ要求は集約が拒み 400 になること")
  void unknownStatusIsRejected() throws Exception {
    when(service.choose(10L, ContactPurpose.MARKETING, ContactPermissionStatus.UNKNOWN))
        .thenThrow(new InvalidMemberNotificationSettingException("通知の設定は許可か拒否を選んでください"));

    mockMvc
        .perform(choose("MARKETING", "{\"status\":\"UNKNOWN\"}").with(member()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("通知の設定は許可か拒否を選んでください"));
  }

  @Test
  @DisplayName("用途の値域外は 400 で、変更には到達しないこと")
  void unknownPurposeIsRejected() throws Exception {
    mockMvc
        .perform(choose("SURVEY", "{\"status\":\"ALLOWED\"}").with(member()))
        .andExpect(status().isBadRequest());

    verify(service, never()).choose(anyLong(), any(), any());
  }

  @Test
  @DisplayName("職員の権限では照会も変更も 403 で拒まれること")
  @WithMockUser(authorities = {"PERM_CUSTOMER_MANAGE", "PERM_NOTIFICATION_MANAGE"})
  void staffIsForbidden() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
    mockMvc
        .perform(choose("BUSINESS", "{\"status\":\"ALLOWED\"}"))
        .andExpect(status().isForbidden());

    verify(service, never()).view(anyLong());
    verify(service, never()).choose(anyLong(), any(), any());
  }

  @Test
  @DisplayName("未認証は 401 で拒まれること")
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

    verify(service, never()).view(anyLong());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  /** 検証済み Bearer トークンの会員。MVC 単体の文脈には security filter が無いので、認可と handler の Principal の両方へ同じ認証を置く。 */
  private static RequestPostProcessor member() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("member@example.com")
            .claim("userId", 10L)
            .build();
    var authentication =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    return request -> {
      SecurityContextHolder.getContext().setAuthentication(authentication);
      request.setUserPrincipal(authentication);
      return request;
    };
  }

  private static MockHttpServletRequestBuilder choose(String purpose, String body) {
    return put(PATH + "/" + purpose).contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
