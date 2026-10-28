package com.kizuna.storeprofile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.storeprofile.api.dto.StoreProfileMapperImpl;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;
import com.kizuna.storeprofile.domain.SnsLink;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class StoreProfileAuditTest {
  private static final Long STORE_ID = 7L;
  private final StoreProfileRepository profiles = mock(StoreProfileRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private StoreProfileService service;
  private StoreProfile profile;

  @BeforeEach
  void setup() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
    service =
        new StoreProfileService(
            profiles,
            new StoreProfileMapperImpl(),
            new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC())));
    var actor =
        User.builder()
            .email("operator@example.test")
            .password(UUID.randomUUID().toString())
            .displayName("監査担当")
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
    profile = StoreProfile.createDefault(STORE_ID);
    profile.setLogoUrl("https://example.test/logo.png");
    profile.setDescription("元の紹介文");
    ReflectionTestUtils.setField(profile, "version", 3L);
    when(profiles.findByStoreId(STORE_ID)).thenReturn(Optional.of(profile));
    when(profiles.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ReflectionTestUtils.setField(profile, "version", 4L);
              return profile;
            });
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recordsOnlyChangedFieldNamesNeverValues() {
    var request = new StoreProfileUpdateRequest();
    request.setLogoUrl("https://example.test/new-logo.png");
    request.setDescription("新しい紹介文");
    request.setMvType("image");

    service.update(STORE_ID, request);

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("STORE_PROFILE_UPDATED");
    assertThat(event.getTargetType()).isEqualTo("STORE_PROFILE");
    assertThat(event.getTargetId()).isEqualTo("7");
    assertThat(event.getStoreId()).isEqualTo(STORE_ID);
    assertThat(event.getBeforeValues()).containsOnly(Map.entry("version", "3"));
    assertThat(event.getAfterValues())
        .containsOnly(
            Map.entry("version", "4"), Map.entry("changed_fields", "logo_url,description"));
    assertThat(event.toString()).doesNotContain("new-logo", "紹介文");
  }

  @Test
  void collectionChangesAreDetectedEvenWhenMapperMutatesInPlace() {
    var request = new StoreProfileUpdateRequest();
    request.setCustomTexts(Map.of("greeting", "ようこそ"));
    request.setSnsLinks(List.of(new SnsLink("x", "https://x.example.test/a", null)));

    service.update(STORE_ID, request);

    assertThat(recorded.getFirst().getAfterValues())
        .containsEntry("changed_fields", "custom_texts,sns_links");
  }

  @Test
  void nullLinkElementsDoNotBreakSnapshot() {
    profile.setSnsLinks(new ArrayList<>(Arrays.asList((SnsLink) null)));
    var request = new StoreProfileUpdateRequest();
    request.setSnsLinks(new ArrayList<>());

    service.update(STORE_ID, request);

    assertThat(recorded.getFirst().getAfterValues()).containsEntry("changed_fields", "sns_links");
  }

  @Test
  void sameValueAndEmptyRequestsRecordNothing() {
    var same = new StoreProfileUpdateRequest();
    same.setLogoUrl("https://example.test/logo.png");
    same.setDescription("元の紹介文");
    same.setSnsLinks(new ArrayList<>());

    service.update(STORE_ID, same);
    service.update(STORE_ID, new StoreProfileUpdateRequest());

    assertThat(recorded).isEmpty();
  }

  @Test
  void failedSaveRecordsNothing() {
    when(profiles.saveAndFlush(any())).thenThrow(new IllegalStateException("flush"));
    var request = new StoreProfileUpdateRequest();
    request.setPhone("03-0000-0000");

    assertThatThrownBy(() -> service.update(STORE_ID, request))
        .isInstanceOf(IllegalStateException.class);

    assertThat(recorded).isEmpty();
  }

  @Test
  void auditFailurePropagatesSoBusinessUpdateRollsBack() {
    when(events.saveAndFlush(any())).thenThrow(new IllegalStateException("audit"));
    var request = new StoreProfileUpdateRequest();
    request.setPhone("03-0000-0000");

    assertThatThrownBy(() -> service.update(STORE_ID, request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("audit");
  }
}
