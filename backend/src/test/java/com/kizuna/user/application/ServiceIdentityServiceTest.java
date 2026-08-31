package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.ServiceIdentityCreateRequest;
import com.kizuna.user.api.dto.ServiceIdentityResponse;
import com.kizuna.user.api.dto.ServiceIdentityUpdateRequest;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.RoleSummary;
import com.kizuna.user.domain.StaleServiceIdentityUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ServiceIdentityServiceTest {

  private static final long CUSTOM_ROLE = 20L;
  private static final long SYSTEM_ROLE = 21L;

  @Mock private PlatformUserRepository repository;

  @Mock private RoleRepository roleRepository;

  @InjectMocks private ServiceIdentityService service;

  @Captor private ArgumentCaptor<PlatformUser> userCaptor;

  private Role role(long id, String name, boolean systemRole) {
    Role role = Role.builder().name(name).systemRole(systemRole).permissionIds(Set.of(1L)).build();
    role.setId(id);
    return role;
  }

  private PlatformUser serviceIdentity(long id, Set<Long> roleIds) {
    PlatformUser user =
        PlatformUser.builder()
            .displayName("夜間バッチ")
            .enabled(true)
            .userType(UserType.SERVICE)
            .roleIds(roleIds)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    user.setId(id);
    ReflectionTestUtils.setField(user, "version", 0L);
    return user;
  }

  private ServiceIdentityCreateRequest createRequest(Set<Long> roleIds) {
    ServiceIdentityCreateRequest req = new ServiceIdentityCreateRequest();
    req.setDisplayName("夜間バッチ");
    req.setRoleIds(roleIds);
    req.setStoreScopeType(StoreScopeType.SPECIFIC_STORES);
    req.setStoreIds(Set.of(1L));
    return req;
  }

  private ServiceIdentityUpdateRequest updateRequest(Set<Long> roleIds, long version) {
    ServiceIdentityUpdateRequest req = new ServiceIdentityUpdateRequest();
    req.setRoleIds(roleIds);
    req.setStoreScopeType(StoreScopeType.SPECIFIC_STORES);
    req.setStoreIds(Set.of(2L));
    req.setVersion(version);
    return req;
  }

  @Test
  @DisplayName("作成は資格情報なし・本人種別 SERVICE で永続化される")
  void create_persistsCredentialLessServiceIdentity() {
    when(roleRepository.findAllById(Set.of(CUSTOM_ROLE)))
        .thenReturn(List.of(role(CUSTOM_ROLE, "バッチ実行", false)));
    when(repository.saveAndFlush(userCaptor.capture()))
        .thenAnswer(
            invocation -> {
              PlatformUser saved = invocation.getArgument(0);
              // 永続化で version 列が初期化される実挙動を模す。
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });

    ServiceIdentityResponse res = service.create(createRequest(Set.of(CUSTOM_ROLE)));

    PlatformUser saved = userCaptor.getValue();
    assertThat(saved.getUserType()).isEqualTo(UserType.SERVICE);
    assertThat(saved.getEmail()).isNull();
    assertThat(saved.getPassword()).isNull();
    assertThat(saved.getEnabled()).isTrue();
    assertThat(saved.getRoleIds()).containsExactly(CUSTOM_ROLE);
    assertThat(res.displayName()).isEqualTo("夜間バッチ");
    assertThat(res.roles())
        .extracting(ServiceIdentityResponse.RoleRef::name)
        .containsExactly("バッチ実行");
  }

  @Test
  @DisplayName("平台既定ロール（is_system）を載せた作成は 400 で拒絶される")
  void create_rejectsSystemRoleGrant() {
    // 名称でなく is_system フラグの実体で判定していることを固定する — 既定ロールと同名でない行でも拒む。
    when(roleRepository.findAllById(Set.of(CUSTOM_ROLE, SYSTEM_ROLE)))
        .thenReturn(List.of(role(CUSTOM_ROLE, "バッチ実行", false), role(SYSTEM_ROLE, "任意の名称", true)));

    assertThatThrownBy(() -> service.create(createRequest(Set.of(CUSTOM_ROLE, SYSTEM_ROLE))))
        .isInstanceOf(InvalidRoleGrantException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("実在しないロールを載せた作成は 400 で拒絶される")
  void create_rejectsUnknownRole() {
    when(roleRepository.findAllById(Set.of(CUSTOM_ROLE, 99L)))
        .thenReturn(List.of(role(CUSTOM_ROLE, "バッチ実行", false)));

    assertThatThrownBy(() -> service.create(createRequest(Set.of(CUSTOM_ROLE, 99L))))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("授権変更はロール×店舗集合を再割当てする")
  void update_reassignsGrants() {
    when(roleRepository.findAllById(Set.of(CUSTOM_ROLE)))
        .thenReturn(List.of(role(CUSTOM_ROLE, "バッチ実行", false)));
    PlatformUser existing = serviceIdentity(5L, Set.of(CUSTOM_ROLE));
    when(repository.findById(5L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    ServiceIdentityResponse res = service.update(5L, updateRequest(Set.of(CUSTOM_ROLE), 0L));

    assertThat(existing.getStoreIds()).containsExactly(2L);
    assertThat(res.storeIds()).containsExactly(2L);
  }

  @Test
  @DisplayName("平台既定ロール（is_system）を載せた授権変更は 400 で拒絶される")
  void update_rejectsSystemRoleGrant() {
    when(roleRepository.findAllById(Set.of(SYSTEM_ROLE)))
        .thenReturn(List.of(role(SYSTEM_ROLE, "任意の名称", true)));

    assertThatThrownBy(() -> service.update(5L, updateRequest(Set.of(SYSTEM_ROLE), 0L)))
        .isInstanceOf(InvalidRoleGrantException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("version 不一致の授権変更は 409 で拒絶される")
  void update_staleVersion_conflicts() {
    when(roleRepository.findAllById(Set.of(CUSTOM_ROLE)))
        .thenReturn(List.of(role(CUSTOM_ROLE, "バッチ実行", false)));
    when(repository.findById(5L)).thenReturn(Optional.of(serviceIdentity(5L, Set.of(CUSTOM_ROLE))));

    assertThatThrownBy(() -> service.update(5L, updateRequest(Set.of(CUSTOM_ROLE), 1L)))
        .isInstanceOf(StaleServiceIdentityUpdateException.class);
  }

  @Test
  @DisplayName("本人種別 SERVICE 以外の行は存在しても「見つからない」に倒す")
  void update_nonServiceTarget_isNotFound() {
    when(roleRepository.findAllById(Set.of(CUSTOM_ROLE)))
        .thenReturn(List.of(role(CUSTOM_ROLE, "バッチ実行", false)));
    PlatformUser staff =
        PlatformUser.builder()
            .email("staff@kizuna.test")
            .password("hash")
            .displayName("職員")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(CUSTOM_ROLE))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    when(repository.findById(6L)).thenReturn(Optional.of(staff));

    assertThatThrownBy(() -> service.update(6L, updateRequest(Set.of(CUSTOM_ROLE), 0L)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("停止は enabled=false へ落とし、停止済みへの再送は何も書かない（冪等）")
  void suspend_isIdempotent() {
    PlatformUser existing = serviceIdentity(5L, Set.of(CUSTOM_ROLE));
    when(repository.findById(5L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    service.suspend(5L);
    assertThat(existing.getEnabled()).isFalse();

    service.suspend(5L);
    verify(repository).saveAndFlush(existing);
  }

  @Test
  @DisplayName("再開は enabled=true へ戻し、有効への再送は何も書かない（冪等）")
  void resume_isIdempotent() {
    PlatformUser existing = serviceIdentity(5L, Set.of(CUSTOM_ROLE));
    existing.stop();
    when(repository.findById(5L)).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(existing)).thenReturn(existing);

    service.resume(5L);
    assertThat(existing.getEnabled()).isTrue();

    service.resume(5L);
    verify(repository).saveAndFlush(existing);
  }

  @Test
  @DisplayName("付与可能ロールの読み口は平台既定ロールを含まない")
  void grantableRoles_excludeSystemRoles() {
    when(roleRepository.findAllSummaries())
        .thenReturn(List.of(summary(1L, "HQ管理者", true), summary(2L, "バッチ実行", false)));

    List<RoleSummaryResponse> roles = service.grantableRoles();

    assertThat(roles)
        .extracting(RoleSummaryResponse::name)
        .containsExactly("バッチ実行")
        .doesNotContain("HQ管理者");
  }

  private static RoleSummary summary(long id, String name, boolean systemRole) {
    return new RoleSummary() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Boolean getSystemRole() {
        return systemRole;
      }

      @Override
      public long getPermissionCount() {
        return 1L;
      }
    };
  }
}
