package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastInvitationResponse;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantScoped;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** キャスト招待の発行と、一覧表示用の招待状態導出を担うサービス。 */
@Service
@RequiredArgsConstructor
public class CastInvitationService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private final CastRepository castRepository;
  private final CastInvitationRepository castInvitationRepository;

  /**
   * 招待を発行する。紐づき済みの档案は拒否し、既存の PENDING 招待を全て失効させてから最新 1 枚を新規発行する。 tenantFilter により他テナントの档案は見えず
   * ServiceException となる（越権はインターセプタが先に 403）。
   *
   * <p>档案あたりの有効な招待は最大 1 枚であることを部分ユニークインデックス {@code uq_t_cast_invitations_pending_cast}（{@code WHERE
   * status = 'PENDING'}）が DB レベルで保証する。 これにより店長の二重クリック等で issue() が並行しても、複数の有効トークンが同時に発行される事態を塞ぐ
   * （token 一意制約だけでは異なるトークンの二重 PENDING を防げないため — #327）。
   */
  @TenantScoped
  @Transactional
  public CastInvitationResponse issue(String castId) {
    Cast cast =
        castRepository
            .findById(castId)
            .orElseThrow(() -> new ServiceException("キャストが見つかりません: " + castId));
    if (cast.getPlatformUserId() != null) {
      throw new CastInvitationStateException("この档案は既に平台身分と連携済みのため招待を発行できません");
    }

    List<CastInvitation> existingPending =
        castInvitationRepository.findByCastIdAndStatus(castId, CastInvitation.Status.PENDING);
    if (!existingPending.isEmpty()) {
      existingPending.forEach(CastInvitation::invalidate);
      // 失効の UPDATE を新規 PENDING の INSERT より前に確定させる。Hibernate は既定で INSERT を
      // UPDATE より先に実行するため、明示 flush しないと旧 PENDING が残ったまま新 PENDING を
      // INSERT してしまい、部分ユニークインデックスに衝突する（通常の再発行が失敗する）。
      castInvitationRepository.flush();
    }

    CastInvitation invitation =
        CastInvitation.builder()
            .castId(castId)
            .token(generateToken())
            .expiresAt(OffsetDateTime.now().plus(CastInvitation.VALIDITY))
            .build();
    invitation.setTenantId(cast.getTenantId());
    try {
      CastInvitation saved = castInvitationRepository.saveAndFlush(invitation);
      return new CastInvitationResponse(saved.getToken(), saved.getExpiresAt());
    } catch (DataIntegrityViolationException ex) {
      // 真の並行発行で他トランザクションが同一档案の PENDING を先に確定していた場合、部分ユニーク
      // インデックス違反となる。意味のある 400（CastInvitationStateException）へ変換する。
      throw new CastInvitationStateException("招待の発行が他の操作と競合しました。時間をおいて再度お試しください");
    }
  }

  /** ページ内の档案について招待状態（四態）を一括導出する。呼び出し元の tenantFilter 有効なトランザクション内で使う。 */
  public Map<String, CastInvitationStatus> deriveStatuses(List<Cast> casts) {
    if (casts.isEmpty()) {
      return Map.of();
    }
    OffsetDateTime now = OffsetDateTime.now();
    List<String> castIds = casts.stream().map(Cast::getId).toList();

    Map<String, List<CastInvitation>> invitationsByCast = new HashMap<>();
    for (CastInvitation invitation : castInvitationRepository.findByCastIdIn(castIds)) {
      invitationsByCast
          .computeIfAbsent(invitation.getCastId(), key -> new ArrayList<>())
          .add(invitation);
    }

    Map<String, CastInvitationStatus> statuses = new HashMap<>();
    for (Cast cast : casts) {
      statuses.put(
          cast.getId(),
          deriveStatus(cast, invitationsByCast.getOrDefault(cast.getId(), List.of()), now));
    }
    return statuses;
  }

  private CastInvitationStatus deriveStatus(
      Cast cast, List<CastInvitation> invitations, OffsetDateTime now) {
    if (cast.getPlatformUserId() != null) {
      return CastInvitationStatus.LINKED;
    }
    List<CastInvitation> pending =
        invitations.stream()
            .filter(invitation -> invitation.getStatus() == CastInvitation.Status.PENDING)
            .toList();
    if (pending.isEmpty()) {
      return CastInvitationStatus.NOT_INVITED;
    }
    boolean anyActive = pending.stream().anyMatch(invitation -> !invitation.isExpired(now));
    return anyActive ? CastInvitationStatus.INVITED : CastInvitationStatus.EXPIRED;
  }

  private String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
