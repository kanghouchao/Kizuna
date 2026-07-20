package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastInvitationResponse;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
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
  private static final String ALREADY_LINKED_MESSAGE = "この档案は既に平台身分と連携済みのため招待を発行できません";

  private final CastRepository castRepository;
  private final CastInvitationRepository castInvitationRepository;

  /**
   * 招待を発行する。紐づき済みの档案は拒否し、既存の PENDING 招待を全て失効させてから最新 1 枚を新規発行する。 storeFilter により他店舗の档案は見えず
   * ServiceException となる（越権はインターセプタが先に 403）。
   *
   * <p>档案あたりの有効な招待は最大 1 枚であることを部分ユニークインデックス {@code uq_t_cast_invitations_pending_cast}（{@code WHERE
   * status = 'PENDING'}）が DB レベルで保証する。 これにより店長の二重クリック等で issue() が並行しても、複数の有効トークンが同時に発行される事態を塞ぐ
   * （token 一意制約だけでは異なるトークンの二重 PENDING を防げないため — #327）。
   *
   * <p>並行する受諾（{@link CastInvitationAcceptanceService}）との競合は次の 2 段で直列化する（#327 codex 指摘）。 ①旧 PENDING
   * の失効を、管理エンティティの {@code invalidate()} ではなく条件付き一括 UPDATE（{@code WHERE status =
   * PENDING}）で行う。これにより受諾が先に ACCEPTED へ遷移させた行は対象外となり、受諾確定済みの招待を INVALIDATED へ巻き戻さない。 ②失効 UPDATE
   * は受諾側の行ロック解放 （＝受諾トランザクションのコミット）を待って完了するため、その後に档案の紐づけを DB 再読込で再確認し、
   * 並行受諾が档案を紐づけていれば新規発行を中止する（連携済み档案に有効トークンが残る矛盾を塞ぐ）。
   */
  @StoreScoped
  @Transactional
  public CastInvitationResponse issue(String castId) {
    Cast cast =
        castRepository
            .findById(castId)
            .orElseThrow(() -> new ServiceException("キャストが見つかりません: " + castId));
    if (cast.getPlatformUserId() != null) {
      throw new CastInvitationStateException(ALREADY_LINKED_MESSAGE);
    }

    // 旧 PENDING を条件付き一括 UPDATE（WHERE status = PENDING）で失効させる。並行受諾が先に ACCEPTED へ
    // 遷移させた行は条件に一致せず対象外となるため、受諾確定済みの招待を INVALIDATED へ巻き戻さない。
    // 一括 UPDATE は即時 SQL のため管理エンティティを介さず、旧 PENDING が新 PENDING の INSERT より前に確定する。
    castInvitationRepository.invalidatePending(
        castId, CastInvitation.Status.PENDING, CastInvitation.Status.INVALIDATED);

    // 失効 UPDATE は受諾側の行ロック解放（＝受諾トランザクションのコミット）を待って完了するため、この時点で
    // 並行受諾は確定済み。档案が紐づいていないかを DB から再読込（スカラ投影で一次キャッシュを回避）して再確認し、
    // 紐づき済みなら新規発行を中止する。これで連携済み档案に有効トークンが残る矛盾を塞ぐ。
    if (castRepository.findPlatformUserIdById(castId).isPresent()) {
      throw new CastInvitationStateException(ALREADY_LINKED_MESSAGE);
    }

    CastInvitation invitation =
        CastInvitation.builder()
            .castId(castId)
            .token(generateToken())
            .expiresAt(OffsetDateTime.now().plus(CastInvitation.VALIDITY))
            .build();
    invitation.setStoreId(cast.getStoreId());
    try {
      CastInvitation saved = castInvitationRepository.saveAndFlush(invitation);
      return new CastInvitationResponse(saved.getToken(), saved.getExpiresAt());
    } catch (DataIntegrityViolationException ex) {
      // 真の並行発行で他トランザクションが同一档案の PENDING を先に確定していた場合、部分ユニーク
      // インデックス違反となる。意味のある 400（CastInvitationStateException）へ変換する。
      throw new CastInvitationStateException("招待の発行が他の操作と競合しました。時間をおいて再度お試しください");
    }
  }

  /** ページ内の档案について招待状態（四態）を一括導出する。呼び出し元の storeFilter 有効なトランザクション内で使う。 */
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
