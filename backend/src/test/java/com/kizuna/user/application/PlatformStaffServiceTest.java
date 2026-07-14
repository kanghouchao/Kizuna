package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformStaffServiceTest {

  @Mock private PlatformUserRepository repository;

  @Mock private PasswordEncoder encoder;

  @InjectMocks private PlatformStaffService service;

  @Captor private ArgumentCaptor<Collection<PlatformRole>> rolesCaptor;

  @Captor private ArgumentCaptor<PlatformUser> userCaptor;

  private PlatformUser staff(
      long id, String email, PlatformRole role, StoreScopeType scopeType, Set<Long> storeIds) {
    PlatformUser user =
        PlatformUser.builder()
            .email(email)
            .password("hash")
            .displayName("表示名")
            .enabled(true)
            .role(role)
            .storeScopeType(scopeType)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    return user;
  }

  private PlatformStaffCreateRequest createRequest(
      String email,
      String password,
      PlatformRole role,
      StoreScopeType scopeType,
      Set<Long> storeIds) {
    PlatformStaffCreateRequest req = new PlatformStaffCreateRequest();
    req.setEmail(email);
    req.setPassword(password);
    req.setDisplayName("表示名");
    req.setRole(role);
    req.setStoreScopeType(scopeType);
    req.setStoreIds(storeIds);
    return req;
  }

  @Test
  void list_filtersByStaffRolesExcludingCastAndMember() {
    when(repository.findByRoleInOrderByDisplayNameAsc(rolesCaptor.capture()))
        .thenReturn(
            List.of(
                staff(
                    1L,
                    "hq@kizuna.test",
                    PlatformRole.HQ_ADMIN,
                    StoreScopeType.ALL_STORES,
                    Set.of()),
                staff(
                    2L,
                    "mgr@kizuna.test",
                    PlatformRole.STORE_MANAGER,
                    StoreScopeType.SPECIFIC_STORES,
                    Set.of(1L))));

    List<PlatformStaffResponse> result = service.list();

    verify(repository).findByRoleInOrderByDisplayNameAsc(rolesCaptor.getValue());
    assertThat(rolesCaptor.getValue())
        .containsExactlyInAnyOrder(
            PlatformRole.HQ_ADMIN, PlatformRole.STORE_MANAGER, PlatformRole.STORE_STAFF);
    assertThat(rolesCaptor.getValue()).doesNotContain(PlatformRole.CAST, PlatformRole.MEMBER);

    assertThat(result)
        .extracting(PlatformStaffResponse::id, PlatformStaffResponse::role)
        .containsExactly(
            Tuple.tuple(1L, PlatformRole.HQ_ADMIN), Tuple.tuple(2L, PlatformRole.STORE_MANAGER));
    assertThat(result.get(1).storeIds()).containsExactly(1L);
  }

  @Test
  void create_encodesPasswordAndSavesStaff() {
    PlatformStaffCreateRequest req =
        createRequest(
            "new@kizuna.test",
            "rawpass",
            PlatformRole.STORE_MANAGER,
            StoreScopeType.SPECIFIC_STORES,
            Set.of(1L));
    when(repository.findByEmail("new@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.save(userCaptor.capture()))
        .thenAnswer(
            invocation -> {
              PlatformUser saved = invocation.getArgument(0);
              saved.setId(9L);
              return saved;
            });

    PlatformStaffResponse res = service.create(req);

    verify(encoder).encode("rawpass");
    PlatformUser saved = userCaptor.getValue();
    assertThat(saved.getEmail()).isEqualTo("new@kizuna.test");
    assertThat(saved.getPassword()).isEqualTo("ENCODED");
    assertThat(saved.getDisplayName()).isEqualTo("表示名");
    assertThat(saved.getEnabled()).isTrue();
    assertThat(saved.getRole()).isEqualTo(PlatformRole.STORE_MANAGER);
    assertThat(saved.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(saved.getStoreIds()).containsExactly(1L);
    assertThat(res.id()).isEqualTo(9L);
    assertThat(res.role()).isEqualTo(PlatformRole.STORE_MANAGER);
  }

  @Test
  void create_duplicateEmail_throwsAndDoesNotSave() {
    PlatformStaffCreateRequest req =
        createRequest(
            "dup@kizuna.test",
            "rawpass",
            PlatformRole.STORE_STAFF,
            StoreScopeType.ALL_STORES,
            Set.of());
    when(repository.findByEmail("dup@kizuna.test"))
        .thenReturn(
            Optional.of(
                staff(
                    5L,
                    "dup@kizuna.test",
                    PlatformRole.STORE_STAFF,
                    StoreScopeType.ALL_STORES,
                    Set.of())));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(DuplicateStaffEmailException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void create_nonStaffRole_throwsWithoutLookupOrEncode() {
    PlatformStaffCreateRequest req =
        createRequest(
            "cast@kizuna.test", "rawpass", PlatformRole.CAST, StoreScopeType.ALL_STORES, Set.of());

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(ServiceException.class);

    verify(repository, never()).findByEmail(any());
    verifyNoInteractions(encoder);
  }

  @Test
  void create_unknownStoreId_convertsToInvalidStoreScope() {
    PlatformStaffCreateRequest req =
        createRequest(
            "fk@kizuna.test",
            "rawpass",
            PlatformRole.STORE_MANAGER,
            StoreScopeType.SPECIFIC_STORES,
            Set.of(999L));
    when(repository.findByEmail("fk@kizuna.test")).thenReturn(Optional.empty());
    when(encoder.encode("rawpass")).thenReturn("ENCODED");
    when(repository.save(any())).thenThrow(new DataIntegrityViolationException("fk violation"));

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(InvalidStoreScopeException.class);
  }
}
