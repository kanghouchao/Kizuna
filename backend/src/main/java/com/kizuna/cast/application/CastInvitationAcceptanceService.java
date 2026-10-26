package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastAcceptanceResponse;
import com.kizuna.cast.api.dto.CastInvitationAcceptRequest;
import com.kizuna.cast.api.dto.CastInvitationDetailResponse;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 招待受諾ユースケース（公開照会・新規登録受諾・既存アカウント受諾）。
 *
 * <p>招待は店舗横断で一意な token で引き、それ以降の照会はその招待の店舗に限る。受諾は本人アカウントの作成・プロフィールへの紐づけ・招待の状態遷移を 単一トランザクションで確定する。
 */
@Service
@RequiredArgsConstructor
public class CastInvitationAcceptanceService {

  private static final String DUPLICATE_EMAIL_MESSAGE =
      "このメールアドレスは既に登録されています。既存アカウントでログインして受諾してください";

  private final CastInvitationRepository castInvitationRepository;
  private final CastEnrollmentRepository castRepository;
  private final CastRepository personRepository;
  private final CastProfileRepository profileRepository;
  private final UserRepository userRepository;
  private final StoreRepository storeRepository;
  private final PasswordEncoder passwordEncoder;

  /** 招待を照会する。受諾可否のビュー状態と店舗名・档案名を返す。 */
  @Transactional(readOnly = true)
  public CastInvitationDetailResponse view(String token) {
    CastInvitation invitation = findByToken(token);
    CastEnrollment cast = requireCast(invitation);
    return new CastInvitationDetailResponse(
        storeName(invitation.getStoreId()),
        profileRepository
            .findByEnrollmentIdAndStoreId(cast.getId(), invitation.getStoreId())
            .orElseThrow(() -> new NotFoundException("プロフィールが見つかりません"))
            .getName(),
        viewStatus(invitation, cast),
        invitation.getExpiresAt());
  }

  /** 新規登録で受諾する。CAST・SPECIFIC_STORES{招待店舗} の身分を作成し、档案へ紐づけて招待を受諾済みにする。 */
  @Transactional
  public CastAcceptanceResponse acceptAsNewUser(String token, CastInvitationAcceptRequest request) {
    CastInvitation invitation = findByToken(token);
    storeRepository.lockAgainstDeletion(invitation.getStoreId());
    CastEnrollment cast = requireCastForUpdate(invitation);
    if (userRepository.findByEmail(request.getEmail().toLowerCase(Locale.ROOT)).isPresent()) {
      throw new ServiceException(DUPLICATE_EMAIL_MESSAGE);
    }
    claim(invitation);

    User user =
        saveUser(
            User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .enabled(true)
                .userType(UserType.CAST)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(invitation.getStoreId()))
                .build());
    link(cast, user.getId());
    return new CastAcceptanceResponse(storeName(invitation.getStoreId()));
  }

  /**
   * 既存の CAST アカウントで受諾する。SPECIFIC_STORES は所属店舗集合に招待店舗を冪等 union し、ALL_STORES
   * は全店アクセス権をそのまま保持する（降格しない）。 いずれも档案へ紐づけて招待を受諾済みにする。
   */
  @Transactional
  public CastAcceptanceResponse acceptAsExistingUser(String token, String email) {
    CastInvitation invitation = findByToken(token);
    storeRepository.lockAgainstDeletion(invitation.getStoreId());
    CastEnrollment cast = requireCastForUpdate(invitation);
    claim(invitation);

    // 身分は档案・招待の後に押さえる（順序は ADR 0016）。ロック取得後の新鮮な読み込みで storeIds の
    // read-modify-write を直列化し、並行受諾の取りこぼしを防ぐ。非CAST・不在ユーザーの防御的拒否は
    // RuntimeException でトランザクションごとロールバックされ、招待は消費されない。
    User user =
        userRepository
            .findByEmailForUpdate(email)
            .orElseThrow(() -> new AccessDeniedException("アクセス権限がありません"));
    if (user.getUserType() != UserType.CAST) {
      throw new AccessDeniedException("CAST アカウントのみ既存受諾できます");
    }
    if (!castRepository
        .findIdsByUserIdAndStoreId(user.getId(), invitation.getStoreId())
        .isEmpty()) {
      throw new ConflictException("この店舗には既に有効な在籍があります");
    }

    // ALL_STORES は既に全店アクセス権を持つため、SPECIFIC_STORES へ降格させない（不変条件で storeIds は空）。
    // SPECIFIC_STORES の場合のみ招待店舗を冪等 union して再割当する（束・精算範囲は触らない — CAST は保持しない）。
    if (user.getStoreScopeType() == StoreScopeType.SPECIFIC_STORES) {
      Set<Long> storeIds = new HashSet<>(user.getStoreIds());
      storeIds.add(invitation.getStoreId());
      user.reassignStores(StoreScopeType.SPECIFIC_STORES, storeIds);
      userRepository.save(user);
    }
    link(cast, user.getId());
    return new CastAcceptanceResponse(storeName(invitation.getStoreId()));
  }

  /**
   * 招待を原子的にクレームする（身分作成・紐づけの前に受諾権を確定する）。
   *
   * <p>期限切れは物理状態が PENDING のままで条件付き UPDATE では弾けないため、事前検証で拒否する。 PENDING→ACCEPTED の遷移自体は条件付き UPDATE
   * の更新行数で直列化し、並行受諾では先着のみ 1 行を更新できる。 遷移は DB 側で確定するため、読み込んだ招待エンティティは受諾後に変更も再取得もしない（＝フラッシュで上書きされない）。
   * よって永続化コンテキストのクリアは不要。
   */
  private void claim(CastInvitation invitation) {
    OffsetDateTime now = OffsetDateTime.now();
    if (invitation.isExpired(now)) {
      throw new CastInvitationStateException("この招待は有効期限が切れています");
    }
    int claimed =
        castInvitationRepository.claimPending(
            invitation.getId(),
            invitation.getStoreId(),
            now,
            CastInvitation.Status.PENDING,
            CastInvitation.Status.ACCEPTED);
    if (claimed != 1) {
      throw new CastInvitationStateException("この招待は既に受諾済みまたは失効しています");
    }
  }

  private void link(CastEnrollment cast, Long userId) {
    Cast person =
        personRepository
            .findByUserId(userId)
            .orElseGet(() -> personRepository.save(Cast.builder().userId(userId).build()));
    cast.linkCast(person.getId());
    IntegrityViolations.translateOnFailure(
        () -> castRepository.saveAndFlush(cast),
        Map.of(
            DbConstraint.UQ_T_CAST_ENROLLMENTS_CURRENT,
            () -> new ConflictException("この店舗には既に有効な在籍があります")));
  }

  private CastInvitation findByToken(String token) {
    return castInvitationRepository
        .findByToken(token)
        .orElseThrow(() -> new NotFoundException("招待が見つかりません"));
  }

  /** 招待は発行時に档案の店舗を写すので、档案は招待行の店舗で引く。 */
  private CastEnrollment requireCast(CastInvitation invitation) {
    return castRepository
        .findByIdAndStoreId(invitation.getCastId(), invitation.getStoreId())
        .orElseThrow(() -> new NotFoundException("キャストが見つかりません"));
  }

  /**
   * 受諾で書き換える档案を、招待行を押さえる前に押さえて引く。
   *
   * <p>押さえる順序は ADR 0016 に従う。受諾は身分の所属店舗も書くので、その手前で店舗行も押さえてある （{@link
   * StoreRepository#lockAgainstDeletion}）。照会（{@link #view}）は書かないのでこの口を通らない。
   */
  private CastEnrollment requireCastForUpdate(CastInvitation invitation) {
    CastEnrollment enrollment =
        castRepository
            .findByIdAndStoreIdForUpdate(invitation.getCastId(), invitation.getStoreId())
            .orElseThrow(() -> new NotFoundException("キャストが見つかりません"));
    if (!enrollment.isMembershipActive()) {
      throw new CastInvitationStateException("退店済みの在籍の招待は受諾できません");
    }
    return enrollment;
  }

  private String storeName(Long storeId) {
    return storeRepository.findById(storeId).map(Store::getName).orElse(null);
  }

  private String viewStatus(CastInvitation invitation, CastEnrollment enrollment) {
    if (invitation.getStatus() != CastInvitation.Status.PENDING) {
      return "USED";
    }
    if (!enrollment.isMembershipActive()) {
      return "UNAVAILABLE";
    }
    return invitation.isExpired(OffsetDateTime.now()) ? "EXPIRED" : "VALID";
  }

  private User saveUser(User user) {
    try {
      return userRepository.save(user);
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.UQ_T_USERS_EMAIL, () -> new ServiceException(DUPLICATE_EMAIL_MESSAGE)));
    }
  }
}
