package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformStaffServiceTest {

  @Mock private PlatformUserRepository repository;

  @InjectMocks private PlatformStaffService service;

  @Captor private ArgumentCaptor<Collection<PlatformRole>> rolesCaptor;

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
            org.assertj.core.groups.Tuple.tuple(1L, PlatformRole.HQ_ADMIN),
            org.assertj.core.groups.Tuple.tuple(2L, PlatformRole.STORE_MANAGER));
    assertThat(result.get(1).storeIds()).containsExactly(1L);
  }
}
