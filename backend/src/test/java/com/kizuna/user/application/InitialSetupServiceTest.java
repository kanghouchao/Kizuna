package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.api.dto.InitialSetupRequest;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialSetupServiceTest {

  private static final long ADMIN_ROLE = 10L;

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private BusinessAudit businessAudit;

  private final AppProperties appProperties = new AppProperties();

  private InitialSetupService service;

  @BeforeEach
  void setUp() {
    appProperties.setDomain("kizuna.example.com");
    service =
        new InitialSetupService(
            userRepository, roleRepository, passwordEncoder, businessAudit, appProperties);
  }

  private static InitialSetupRequest request() {
    InitialSetupRequest request = new InitialSetupRequest();
    request.setEmail("Owner@Kizuna.test");
    request.setPassword("password123");
    request.setDisplayName("管理者 太郎");
    return request;
  }

  private void adminRoleExists() {
    Role role = Role.builder().name("グループ管理者").systemRole(true).permissionIds(Set.of(1L)).build();
    role.setId(ADMIN_ROLE);
    when(roleRepository.findByName("グループ管理者")).thenReturn(Optional.of(role));
  }

  @Test
  @DisplayName("STAFF の行が無ければ初期設定が必要、1 件でもあれば不要")
  void requiredOnlyWhileNoStaffExists() {
    when(userRepository.existsByUserType(UserType.STAFF)).thenReturn(false);
    assertThat(service.required()).isTrue();

    when(userRepository.existsByUserType(UserType.STAFF)).thenReturn(true);
    assertThat(service.required()).isFalse();
  }

  @Test
  @DisplayName("登録はグループ管理者ロール + ALL_STORES の有効な STAFF を作り、本人を行為者として監査する")
  void registerCreatesRootAdministratorAuditedAsItself() {
    when(userRepository.existsByUserType(UserType.STAFF)).thenReturn(false);
    adminRoleExists();
    when(passwordEncoder.encode("password123")).thenReturn("encoded");
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    when(userRepository.saveAndFlush(userCaptor.capture()))
        .thenAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              saved.setId(42L);
              return saved;
            });

    Long id = service.register(request());

    User user = userCaptor.getValue();
    assertThat(id).isEqualTo(42L);
    assertThat(user.getUserType()).isEqualTo(UserType.STAFF);
    assertThat(user.getEnabled()).isTrue();
    assertThat(user.getEmail()).isEqualTo("owner@kizuna.test");
    assertThat(user.getPassword()).isEqualTo("encoded");
    assertThat(user.getDisplayName()).isEqualTo("管理者 太郎");
    assertThat(user.getRoleIds()).containsExactly(ADMIN_ROLE);
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.ALL_STORES);
    assertThat(user.getStoreIds()).isEmpty();
    verify(businessAudit)
        .recordById(
            eq(42L),
            isNull(),
            eq("STAFF_CREATED"),
            eq("USER_GRANTS"),
            eq("42"),
            isNull(),
            isNull(),
            eq(Map.of()),
            eq(BusinessAudit.grants(user)));
  }

  @Test
  @DisplayName("STAFF が 1 件でもあれば登録は 404 で、行も監査も作らない")
  void registerIsNotFoundOnceStaffExists() {
    when(userRepository.existsByUserType(UserType.STAFF)).thenReturn(true);

    assertThatThrownBy(() -> service.register(request()))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("初期設定は完了しています");

    verify(userRepository, never()).saveAndFlush(any());
    verifyNoInteractions(businessAudit);
  }

  @Test
  @DisplayName("メールアドレスの一意制約違反は従業員作成と同じ重複エラー（400）に写像する")
  void registerMapsEmailConstraintViolationToDuplicateStaffEmail() {
    when(userRepository.existsByUserType(UserType.STAFF)).thenReturn(false);
    adminRoleExists();
    when(passwordEncoder.encode("password123")).thenReturn("encoded");
    when(userRepository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "save failed",
                new ConstraintViolationException(
                    "save failed",
                    new SQLException("duplicate key value violates unique constraint"),
                    "uq_t_users_email")));

    assertThatThrownBy(() -> service.register(request()))
        .isInstanceOf(DuplicateStaffEmailException.class)
        .hasMessage("このメールアドレスは既に登録されています");
  }

  @Test
  @DisplayName("起動時の案内は STAFF が居ないときだけ、設定のスキームとドメインで組んだ入口 URL で出す")
  void startupNoticeNamesTheEntryWithConfiguredSchemeAndDomain() {
    appProperties.setScheme(AppProperties.Scheme.HTTPS);
    when(userRepository.existsByUserType(UserType.STAFF)).thenReturn(false);

    Optional<String> notice = service.startupNotice();

    assertThat(notice)
        .hasValue("初期設定が未完了です。https://kizuna.example.com/auth/initial-setup を開いて最初の管理者を登録してください");
  }

  @Test
  @DisplayName("STAFF が 1 件でもあれば起動時の案内を出さない")
  void startupNoticeIsAbsentOnceStaffExists() {
    when(userRepository.existsByUserType(UserType.STAFF)).thenReturn(true);

    assertThat(service.startupNotice()).isEmpty();
  }
}
