package com.kizuna.user.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.api.dto.StaffAccountResponse;
import com.kizuna.user.api.dto.StaffAccountRoleRef;
import com.kizuna.user.api.dto.StaffAccountSummaryResponse;
import com.kizuna.user.domain.HqPasswordResetNotAllowedException;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfPasswordResetNotAllowedException;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.Predicate;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * アカウント管理ユースケース。対象は本人種別 STAFF の全アカウントで、ロール構成（HQ 側／店舗側）を問わない — CAST/MEMBER は権限モデルの外なので在否も出さない。
 *
 * <p>授権は一切書かない。停止・再開とパスワード再設定だけを扱い、ロールと店舗集合は表示にしか現れない。
 */
@Service
@RequiredArgsConstructor
public class PlatformStaffAccountService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  /** 仮パスワードの生成源。予測可能な乱数だと発行直後の乗っ取りを許すため暗号論的乱数に限る。 */
  private final SecureRandom random = new SecureRandom();

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;
  private final RoleManageHolderGuard roleManageHolderGuard;
  private final PasswordEncoder passwordEncoder;
  private final CredentialOperations credentialOperations;

  @Transactional(readOnly = true)
  public Page<StaffAccountSummaryResponse> list(String search, Long storeId, Pageable pageable) {
    Page<PlatformUser> accounts = repository.findAll(accountSpec(search, storeId), pageable);
    Map<Long, String> roleNames =
        roleNamesOf(
            accounts.getContent().stream()
                .flatMap(user -> user.getRoleIds().stream())
                .collect(Collectors.toSet()));
    return accounts.map(
        user ->
            new StaffAccountSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getEnabled(),
                rolesOf(user, roleNames)));
  }

  /**
   * 対象は本人種別 STAFF の全行（ロール構成で絞らない）。検索語は表示名とメールアドレスを横断する部分一致。店舗 id は「その店舗を担当範囲に含む」行への絞り込みで、ALL_STORES
   * は個別 id を持たないまま全店舗を覆うため常に該当させる。
   *
   * <p>null の条件は述語を生成しない（JPQL の ":param is null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため
   * Specification で組み立てる）。
   */
  private static Specification<PlatformUser> accountSpec(String search, Long storeId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userType"), UserType.STAFF));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("displayName")), pattern, escape),
                cb.like(cb.lower(root.get("email")), pattern, escape)));
      }
      if (storeId != null) {
        // 担当店舗集合は @ElementCollection のため、member of は Hibernate が相関副問い合わせ（exists）へ展開する。
        // 親行を結合で増やさないので、ページングの件数・境界に影響しない。
        predicates.add(
            cb.or(
                cb.equal(root.get("storeScopeType"), StoreScopeType.ALL_STORES),
                cb.isMember(storeId, root.get("storeIds"))));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Transactional(readOnly = true)
  public StaffAccountResponse get(Long id) {
    PlatformUser user = requireStaffAccount(id);
    return new StaffAccountResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getEnabled(),
        rolesOf(user, roleNamesOf(user.getRoleIds())),
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()));
  }

  /**
   * 停止する。既に停止済みでも 204 で受理し、失効イベントだけは必ず発行する — AFTER_COMMIT の Redis 書込みが失敗したとき、同じ要求の再送で失効を書き直せるようにする
   * （差分語義だと再送では何も起きず、再開してから止め直す以外に復旧手段が無くなる）。
   */
  @Transactional
  public void suspend(Long id, String actorEmail) {
    String email = repository.findStaffEmailById(id).orElseThrow(() -> notFound(id));
    // 自分自身を停止すると自らのセッションも即時失効し、以後の操作ができなくなる（サポート経路がない自己ロックアウト）ため拒否する。
    if (email.equals(actorEmail)) {
      throw new SelfStopNotAllowedException("自分自身を停止することはできません");
    }
    PlatformUser target = roleManageHolderGuard.loadForSuspension(id);
    boolean wasEnabled = target.getEnabled();
    credentialOperations.stop(target);
    if (wasEnabled) {
      repository.saveAndFlush(target);
    }
  }

  /**
   * 仮パスワードを発行して再設定し、対象の既存セッションを失効させる。生値の戻り値はこの一度だけで、以後どこからも取り出せない。
   *
   * <p>直列化点（{@link PermissionRepository#lockIdByCode}）は押さえない。再設定は enabled もロール構成も動かさないので
   * ROLE_MANAGE 保持者の母集団を減らしようがなく、「増やすだけの操作は押さえない」に当たる。行ロックも取らない —
   * 状態機械の遷移が無く、並行書込みは楽観ロック（@Version）が担う。
   */
  @Transactional
  public String resetPassword(Long id, String actorEmail) {
    PlatformUser target = requireStaffAccount(id);
    // 自己再設定は G6 に委ねず名指しで拒む。JWT の権限は発行時の写しでロール降格後も失効まで残るため、
    // 降格済みの残存セッションからは自分が店舗側に見え、G6 を素通りして口座を恒久奪取できてしまう。
    if (target.getEmail().equals(actorEmail)) {
      throw new SelfPasswordResetNotAllowedException("自分自身のパスワードは再設定できません");
    }
    // HQ 側ロール保持者は対象外（ADR 0021 の守衛 G6）。
    if (new HqRoleMembership(roleRepository.findHqRoleIds()).holdsAny(target.getRoleIds())) {
      throw new HqPasswordResetNotAllowedException("HQ 側ロール保持者のパスワードは再設定できません");
    }
    String temporaryPassword = temporaryPassword();
    credentialOperations.changePassword(target, passwordEncoder.encode(temporaryPassword));
    repository.saveAndFlush(target);
    return temporaryPassword;
  }

  /** 仮パスワード（URL 安全 Base64 の 16 文字）。生値は符号化して保存した後、応答以外のどこにも残さない。 */
  private String temporaryPassword() {
    byte[] bytes = new byte[12];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 再開する。既に有効でも 204 で受理する（冪等）。失効機構には何もしない — 停止で増えた版は戻らないため、 停止前のセッションは復活せず再ログインを要する（ADR 0022）。 */
  @Transactional
  public void resume(Long id) {
    PlatformUser target = requireStaffAccount(id);
    if (!target.getEnabled()) {
      target.resume();
      repository.saveAndFlush(target);
    }
  }

  /**
   * アカウント面の対象行を取り出す。本人種別がスタッフ以外（CAST/MEMBER）の行は、存在しても対象外として「見つからない」に倒す。
   * 対象外と不在を呼出側から区別できないようにするため、両者は id を添えた同一の応答になる。
   */
  private PlatformUser requireStaffAccount(Long id) {
    return repository
        .findById(id)
        .filter(user -> user.getUserType() == UserType.STAFF)
        .orElseThrow(() -> notFound(id));
  }

  private static NotFoundException notFound(Long id) {
    return new NotFoundException("アカウントが見つかりません: " + id);
  }

  private static List<StaffAccountRoleRef> rolesOf(PlatformUser user, Map<Long, String> roleNames) {
    return user.getRoleIds().stream()
        .map(id -> new StaffAccountRoleRef(id, roleNames.get(id)))
        .sorted(
            Comparator.comparing(
                StaffAccountRoleRef::name, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private Map<Long, String> roleNamesOf(Set<Long> roleIds) {
    return roleRepository.findAllById(roleIds).stream()
        .collect(Collectors.toMap(Role::getId, Role::getName));
  }
}
