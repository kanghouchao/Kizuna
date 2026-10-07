package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditChange;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class BusinessAuditTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void requiresAuthenticatedActorAndNeverCopiesCredentialClaims() {
    var users = mock(PlatformUserRepository.class);
    var writer = mock(AuditWriter.class);
    var audit = new BusinessAudit(users, writer);
    assertThatThrownBy(
            () -> audit.recordCurrent(null, "ROLE_CHANGED", "ROLE", "1", Map.of(), Map.of()))
        .isInstanceOf(StaleSessionException.class);
    var actor =
        PlatformUser.builder()
            .email("operator@kizuna.test")
            .password("encoded")
            .displayName("監査担当")
            .userType(UserType.STAFF)
            .enabled(true)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    actor.setId(10L);
    when(users.findByEmail("operator@kizuna.test")).thenReturn(Optional.of(actor));
    var jwt =
        Jwt.withTokenValue("opaque")
            .header("alg", "RS256")
            .subject("operator@kizuna.test")
            .claim("elevationId", 22L)
            .claim("password", "must-not-copy")
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
    audit.recordCurrent(
        null, "ROLE_CHANGED", "ROLE", "1", Map.of("role_ids", "1"), Map.of("role_ids", "2"));
    var event = ArgumentCaptor.forClass(AuditChange.class);
    verify(writer).append(event.capture());
    assertThat(event.getValue().actor().id()).isEqualTo(10L);
    assertThat(event.getValue().afterValues())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("role_ids", "2", "emergency_elevation_id", "22"));
  }

  @Test
  void largeExistingGrantSetsAreRetainedWithoutTruncation() {
    var stores = LongStream.rangeClosed(1, 2000).boxed().collect(Collectors.toSet());
    var user =
        PlatformUser.builder()
            .displayName("多数店舗の担当")
            .userType(UserType.SERVICE)
            .enabled(true)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(stores)
            .build();
    var actor = new com.kizuna.audit.recording.AuditActor(1L, "STAFF", "監査担当");
    var event =
        new AuditChange(
            actor,
            null,
            "SERVICE_ID_GRANTS_CHANGED",
            "SERVICE_ID",
            "2",
            null,
            null,
            BusinessAudit.grants(user),
            Map.of());
    assertThat(event.beforeValues().get("store_ids").split(",")).hasSize(2000);
    assertThat(event.beforeValues().get("store_ids")).endsWith(",2000");
  }
}
