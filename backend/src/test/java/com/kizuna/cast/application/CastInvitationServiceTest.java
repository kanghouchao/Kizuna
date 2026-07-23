package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.api.dto.CastInvitationResponse;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CastInvitationServiceTest {

  @Mock private CastRepository castRepository;
  @Mock private CastInvitationRepository castInvitationRepository;

  @InjectMocks private CastInvitationService castInvitationService;

  private Cast cast(String id, Long platformUserId) {
    Cast cast = Cast.builder().name("キャスト").platformUserId(platformUserId).build();
    cast.setId(id);
    cast.setStoreId(1L);
    return cast;
  }

  private CastInvitation invitation(
      String castId, CastInvitation.Status status, OffsetDateTime expiresAt) {
    return CastInvitation.builder()
        .castId(castId)
        .token("token-" + castId)
        .status(status)
        .expiresAt(expiresAt)
        .build();
  }

  @Test
  void issue_createsPendingInvitationWith72hExpiry() {
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", null)));
    when(castInvitationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    OffsetDateTime before = OffsetDateTime.now();
    CastInvitationResponse response = castInvitationService.issue("c1");

    assertThat(response.token()).isNotBlank();
    assertThat(response.expiresAt())
        .isBetween(
            before.plus(Duration.ofHours(72)).minusMinutes(1),
            OffsetDateTime.now().plus(Duration.ofHours(72)).plusMinutes(1));
  }

  @Test
  void issue_rejectsWhenCastNotFound() {
    when(castRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> castInvitationService.issue("missing"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("キャストが見つかりません");
    verify(castInvitationRepository, never()).saveAndFlush(any());
  }

  @Test
  void issue_rejectsWhenAlreadyLinked() {
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", 99L)));

    assertThatThrownBy(() -> castInvitationService.issue("c1"))
        .isInstanceOf(CastInvitationStateException.class);
    verify(castInvitationRepository, never()).saveAndFlush(any());
  }

  @Test
  void issue_invalidatesPendingViaConditionalUpdateBeforeReissuing() {
    // 旧 PENDING の失効は管理エンティティの invalidate() ではなく条件付き一括 UPDATE で行い、
    // それが新規 PENDING の INSERT より前に実行されることを確認する（受諾との直列化）。
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", null)));
    when(castInvitationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    castInvitationService.issue("c1");

    InOrder inOrder = inOrder(castInvitationRepository);
    inOrder
        .verify(castInvitationRepository)
        .invalidatePending("c1", CastInvitation.Status.PENDING, CastInvitation.Status.INVALIDATED);
    inOrder.verify(castInvitationRepository).saveAndFlush(any());
  }

  @Test
  void issue_convertsPendingUniqueViolationToStateException() {
    // 真の並行発行で他トランザクションが同一档案の PENDING を先に確定した場合、部分ユニーク
    // インデックス違反（DataIntegrityViolationException）となる。これを意味のある 400 に変換する。
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", null)));
    when(castInvitationRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uq_t_cast_invitations_pending_cast"));

    assertThatThrownBy(() -> castInvitationService.issue("c1"))
        .isInstanceOf(CastInvitationStateException.class);
  }

  @Test
  void issue_abortsWhenCastGetsLinkedConcurrentlyBeforeReissuing() {
    // 初回の連携チェック通過後、旧 PENDING 失効までの間に並行受諾が档案を紐づけた状況を模す。
    // 失効後に档案の紐づけを DB 再読込で再確認し、紐づき済みなら新規発行を中止する。
    when(castRepository.findById("c1")).thenReturn(Optional.of(cast("c1", null)));
    when(castRepository.findPlatformUserIdById("c1")).thenReturn(Optional.of(77L));

    assertThatThrownBy(() -> castInvitationService.issue("c1"))
        .isInstanceOf(CastInvitationStateException.class);
    verify(castInvitationRepository, never()).saveAndFlush(any());
  }

  @Test
  void deriveStatuses_returnsFourStates() {
    Cast linked = cast("linked", 5L);
    Cast invited = cast("invited", null);
    Cast expired = cast("expired", null);
    Cast notInvited = cast("not-invited", null);

    when(castInvitationRepository.findByCastIdIn(any()))
        .thenReturn(
            List.of(
                invitation(
                    "invited", CastInvitation.Status.PENDING, OffsetDateTime.now().plusHours(10)),
                invitation(
                    "expired", CastInvitation.Status.PENDING, OffsetDateTime.now().minusHours(1))));

    Map<String, CastInvitationStatus> statuses =
        castInvitationService.deriveStatuses(List.of(linked, invited, expired, notInvited));

    assertThat(statuses.get("linked")).isEqualTo(CastInvitationStatus.LINKED);
    assertThat(statuses.get("invited")).isEqualTo(CastInvitationStatus.INVITED);
    assertThat(statuses.get("expired")).isEqualTo(CastInvitationStatus.EXPIRED);
    assertThat(statuses.get("not-invited")).isEqualTo(CastInvitationStatus.NOT_INVITED);
  }

  @Test
  void deriveStatuses_emptyInputSkipsQuery() {
    Map<String, CastInvitationStatus> statuses = castInvitationService.deriveStatuses(List.of());

    assertThat(statuses).isEmpty();
    verify(castInvitationRepository, never()).findByCastIdIn(any());
  }
}
