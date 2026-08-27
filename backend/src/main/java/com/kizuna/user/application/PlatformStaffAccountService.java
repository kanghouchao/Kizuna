package com.kizuna.user.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.api.dto.StaffAccountResponse;
import com.kizuna.user.api.dto.StaffAccountRoleRef;
import com.kizuna.user.api.dto.StaffAccountSummaryResponse;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * アカウント管理ユースケース。対象は本人種別 STAFF の全アカウントで、ロール構成（HQ 側／店舗側）を問わない — CAST/MEMBER は権限モデルの外なので在否も出さない。
 *
 * <p>授権は一切書かない。停止・再開だけを扱い、ロールと店舗集合は表示にしか現れない。
 *
 * <p><b>ロックの獲得順は 権限目録行（{@link PermissionRepository#lockIdByCode}）→ 利用者行</b>で、授権管理の PUT
 * 経路と同一。逆順の行使点が 1 つでも生まれると、二経路が互いの保持する行を待って環になる。
 */
@Service
@RequiredArgsConstructor
public class PlatformStaffAccountService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final ApplicationEventPublisher eventPublisher;

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
    // 対象が母集団に属するかは押さえる前には決められない（並行するロール編集が対象へ ROLE_MANAGE を
    // 足しうる）ため、停止は経路を問わず無条件に直列化点を押さえてから判定する。
    permissionRepository.lockIdByCode(PermissionCode.ROLE_MANAGE.name());
    // 目録行を待った後に取り直す。事前検査で実体を読んでいないので、ここでの獲得は版の照合を伴わない。
    PlatformUser target = repository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    if (target.getEnabled()) {
      requireRoleManageHolderRemains(target);
      target.stop();
      repository.saveAndFlush(target);
    }
    eventPublisher.publishEvent(new PlatformUserStopped(target.getEmail()));
  }

  /** 再開する。既に有効でも 204 で受理し、解除イベントを発行する（停止と同じ理由の冪等化）。 */
  @Transactional
  public void resume(Long id) {
    PlatformUser target = requireStaffAccount(id);
    if (!target.getEnabled()) {
      target.resume();
      repository.saveAndFlush(target);
    }
    eventPublisher.publishEvent(new PlatformUserResumed(target.getEmail()));
  }

  /**
   * 不減零（ADR 0020 の守衛 G5）。有効な ROLE_MANAGE 実効保持者が 0 になる停止を拒む。役職名でなく実効権限で数える理由は、ロール剥奪経路（{@link
   * PlatformStaffService} の同名検査）に単源化してある。
   *
   * <p>母集団の行も押さえてから数え直す。押さえた問い合わせの結果を数えてはならない（待っている間に確定した降格を見ない — {@link
   * PlatformUserRepository#lockEnabledRoleHolderIds}）。
   */
  private void requireRoleManageHolderRemains(PlatformUser target) {
    Set<Long> roleManageRoleIds =
        roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name());
    if (!holdsAny(target.getRoleIds(), roleManageRoleIds)) {
      return;
    }
    repository.lockEnabledRoleHolderIds(roleManageRoleIds);
    List<Long> holders = repository.findEnabledRoleHolderIds(roleManageRoleIds);
    if (holders.size() == 1 && holders.contains(target.getId())) {
      throw new LastRoleManageHolderException("最後の管理権限保持者を停止・降格することはできません");
    }
  }

  private static boolean holdsAny(Set<Long> roleIds, Set<Long> targetRoleIds) {
    return roleIds.stream().anyMatch(targetRoleIds::contains);
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
