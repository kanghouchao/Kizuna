package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * サービスID（本人種別 SERVICE）を本物の PostgreSQL で固定する統合テスト。
 *
 * <p>固定するのは 3 つ。①資格情報の 2 列が NULL のまま行として成立し、複数の SERVICE 行が email の一意制約に触れないこと（対話ログイン不可が
 * 運用規約でなく構造上の事実であることの土台）。②既存の actor 列（ポイント台帳の {@code actor_user_id}）が SERVICE の id を外部キーとして受けること。
 * ③停止しても行が残り、過去の実行主体の記録が辿れること。
 *
 * <p>種子ユーザーには一切触れず、本 IT 専用の行だけを repository で直挿する。
 */
@SpringBootTest
class ServiceIdentityIT {

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("SERVICE 行は email・パスワードが NULL のまま永続化でき、複数行が共存する（email の一意制約は複数の NULL を許す）")
  void serviceRowsPersistWithoutCredentials() {
    PlatformUser first = saveServiceIdentity("夜間バッチ");
    PlatformUser second = saveServiceIdentity("外部連携");

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from t_users where id in (?, ?)"
                    + " and email is null and password is null",
                Integer.class,
                first.getId(),
                second.getId()))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("SERVICE の id は既存の actor 列（ポイント台帳）に外部キーとして記録できる")
  void serviceIdentityIsRecordableAsLedgerActor() {
    PlatformUser service = saveServiceIdentity("台帳記帳");
    Long memberId = saveMember().getId();

    PointEntry entry =
        pointEntryRepository.saveAndFlush(
            PointEntry.manualAdjust(
                memberId, null, 10, "サービスIDの記帳", null, List.of(), service.getId(), unique("idem")));

    assertThat(
            jdbcTemplate.queryForObject(
                "select actor_user_id from t_point_entries where id = ?",
                Long.class,
                entry.getId()))
        .isEqualTo(service.getId());
  }

  @Test
  @DisplayName("停止した SERVICE の行は残り、実行主体の記録として辿れる")
  void stoppedServiceIdentityKeepsItsRow() {
    PlatformUser service = saveServiceIdentity("停止対象");

    service.stop();
    platformUserRepository.saveAndFlush(service);

    assertThat(platformUserRepository.findById(service.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.getEnabled()).isFalse();
              assertThat(found.getUserType()).isEqualTo(UserType.SERVICE);
            });
  }

  @Test
  @Transactional
  @DisplayName("SERVICE は有効なロール保持者の母集団に数えられない（最後の管理者守衛はログインできる STAFF だけを数える）")
  void serviceIdentityIsExcludedFromEnabledRoleHolderPopulation() {
    Role role = saveCustomRole("守衛母集団");
    platformUserRepository.saveAndFlush(serviceIdentityWith("母集団検査", role));
    PlatformUser staff =
        platformUserRepository.saveAndFlush(
            PlatformUser.builder()
                .email(unique("svc-it-staff") + "@kizuna.test")
                .password(passwordEncoder.encode("pass"))
                .displayName("サービスIT 職員")
                .enabled(true)
                .userType(UserType.STAFF)
                .roleIds(Set.of(role.getId()))
                .storeScopeType(StoreScopeType.ALL_STORES)
                .storeIds(Set.of())
                .build());

    assertThat(platformUserRepository.findEnabledRoleHolderIds(Set.of(role.getId())))
        .containsExactly(staff.getId());
    assertThat(platformUserRepository.lockEnabledRoleHolderIds(Set.of(role.getId())))
        .containsExactly(staff.getId());
  }

  /** 用途ごとの自作ロールを 1 つ持つ SERVICE 行を作る（平台既定ロールは授与しない）。 */
  private PlatformUser saveServiceIdentity(String displayName) {
    return platformUserRepository.saveAndFlush(
        serviceIdentityWith(displayName, saveCustomRole(displayName)));
  }

  private static PlatformUser serviceIdentityWith(String displayName, Role role) {
    return PlatformUser.builder()
        .displayName(displayName)
        .enabled(true)
        .userType(UserType.SERVICE)
        .roleIds(Set.of(role.getId()))
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .build();
  }

  private Role saveCustomRole(String label) {
    Long permissionId =
        permissionRepository
            .findByCodeIn(Set.of(PermissionCode.ORDER_MANAGE.name()))
            .getFirst()
            .getId();
    return roleRepository.saveAndFlush(
        Role.builder().name(unique("サービスIT " + label)).permissionIds(Set.of(permissionId)).build());
  }

  /** 台帳の宛先に要る会員 1 件（会員身分は MEMBER のプラットフォームユーザーと 1 対 1）。 */
  private Member saveMember() {
    PlatformUser memberUser =
        platformUserRepository.saveAndFlush(
            PlatformUser.builder()
                .email(unique("svc-it") + "@kizuna.test")
                .password(passwordEncoder.encode("pass"))
                .displayName("サービスIT 会員")
                .enabled(true)
                .userType(UserType.MEMBER)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of())
                .build());
    return memberRepository.saveAndFlush(
        Member.builder()
            .memberCode(
                String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L)))
            .platformUserId(memberUser.getId())
            .build());
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }
}
