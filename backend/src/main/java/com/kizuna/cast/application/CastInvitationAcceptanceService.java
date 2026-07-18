package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastAcceptanceResponse;
import com.kizuna.cast.api.dto.CastInvitationAcceptRequest;
import com.kizuna.cast.api.dto.CastInvitationDetailResponse;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
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
 * <p>token はテナント横断で一意なため {@code @TenantScoped} は付けない（グローバル検索）。
 * 受諾は身分作成・档案紐づけ・招待状態遷移を単一トランザクションで確定する。
 */
@Service
@RequiredArgsConstructor
public class CastInvitationAcceptanceService {

  private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_t_users_email";
  private static final String DUPLICATE_EMAIL_MESSAGE =
      "このメールアドレスは既に登録されています。既存アカウントでログインして受諾してください";

  private final CastInvitationRepository castInvitationRepository;
  private final CastRepository castRepository;
  private final PlatformUserRepository platformUserRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;

  /** 招待を照会する。受諾可否のビュー状態（VALID/EXPIRED/USED）と店舗名・档案名を返す。 */
  @Transactional(readOnly = true)
  public CastInvitationDetailResponse view(String token) {
    CastInvitation invitation = findByToken(token);
    Cast cast = requireCast(invitation.getCastId());
    return new CastInvitationDetailResponse(
        storeName(invitation.getTenantId()),
        cast.getName(),
        viewStatus(invitation),
        invitation.getExpiresAt());
  }

  /** 新規登録で受諾する。CAST・SPECIFIC_STORES{招待店舗} の身分を作成し、档案へ紐づけて招待を受諾済みにする。 */
  @Transactional
  public CastAcceptanceResponse acceptAsNewUser(String token, CastInvitationAcceptRequest request) {
    CastInvitation invitation = findByToken(token);
    Cast cast = requireCast(invitation.getCastId());
    if (platformUserRepository
        .findByEmail(request.getEmail().toLowerCase(Locale.ROOT))
        .isPresent()) {
      throw new ServiceException(DUPLICATE_EMAIL_MESSAGE);
    }
    claim(invitation);

    PlatformUser user =
        saveUser(
            PlatformUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .enabled(true)
                .userType(UserType.CAST)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(invitation.getTenantId()))
                .build());
    link(cast, user.getId());
    return new CastAcceptanceResponse(storeName(invitation.getTenantId()));
  }

  /**
   * 既存の CAST アカウントで受諾する。SPECIFIC_STORES は所属店舗集合に招待店舗を冪等 union し、ALL_STORES
   * は全店アクセス権をそのまま保持する（降格しない）。 いずれも档案へ紐づけて招待を受諾済みにする。
   */
  @Transactional
  public CastAcceptanceResponse acceptAsExistingUser(String token, String email) {
    CastInvitation invitation = findByToken(token);
    Cast cast = requireCast(invitation.getCastId());
    claim(invitation);

    // 招待をクレーム（招待行を先にロック）した後に PlatformUser 行を悲観排他ロックで取得する。
    // 招待行 → PlatformUser 行 のロック順を維持し（逆順ロックの経路は無い＝デッドロック回避）、
    // ロック取得後の新鮮な読み込みで storeIds の read-modify-write を直列化して並行受諾の取りこぼしを防ぐ。
    // 非CAST・不在ユーザーの防御的拒否は RuntimeException によりトランザクションごとロールバックされ、招待は消費されない。
    PlatformUser user =
        platformUserRepository
            .findByEmailForUpdate(email)
            .orElseThrow(() -> new AccessDeniedException("アクセス権限がありません"));
    if (user.getUserType() != UserType.CAST) {
      throw new AccessDeniedException("CAST アカウントのみ既存受諾できます");
    }

    // ALL_STORES は既に全店アクセス権を持つため、SPECIFIC_STORES へ降格させない（不変条件で storeIds は空）。
    // SPECIFIC_STORES の場合のみ招待店舗を冪等 union して再割当する（束・精算範囲は触らない — CAST は保持しない）。
    if (user.getStoreScopeType() == StoreScopeType.SPECIFIC_STORES) {
      Set<Long> storeIds = new HashSet<>(user.getStoreIds());
      storeIds.add(invitation.getTenantId());
      user.reassignStores(StoreScopeType.SPECIFIC_STORES, storeIds);
      platformUserRepository.save(user);
    }
    link(cast, user.getId());
    return new CastAcceptanceResponse(storeName(invitation.getTenantId()));
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
            invitation.getId(), now, CastInvitation.Status.PENDING, CastInvitation.Status.ACCEPTED);
    if (claimed != 1) {
      throw new CastInvitationStateException("この招待は既に受諾済みまたは失効しています");
    }
  }

  private void link(Cast cast, Long platformUserId) {
    cast.linkPlatformUser(platformUserId);
    castRepository.save(cast);
  }

  private CastInvitation findByToken(String token) {
    return castInvitationRepository
        .findByToken(token)
        .orElseThrow(() -> new ServiceException("招待が見つかりません"));
  }

  private Cast requireCast(String castId) {
    return castRepository.findById(castId).orElseThrow(() -> new ServiceException("キャストが見つかりません"));
  }

  private String storeName(Long tenantId) {
    return tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
  }

  private String viewStatus(CastInvitation invitation) {
    if (invitation.getStatus() != CastInvitation.Status.PENDING) {
      return "USED";
    }
    return invitation.isExpired(OffsetDateTime.now()) ? "EXPIRED" : "VALID";
  }

  private PlatformUser saveUser(PlatformUser user) {
    try {
      return platformUserRepository.save(user);
    } catch (DataIntegrityViolationException ex) {
      String cause = ex.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(EMAIL_UNIQUE_CONSTRAINT)) {
        throw new ServiceException(DUPLICATE_EMAIL_MESSAGE);
      }
      throw ex;
    }
  }
}
