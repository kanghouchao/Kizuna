package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastInvitationAcceptRequest;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 招待の 3 入口を実 BusinessAudit / AuditWriter に接続し、記録契約を固定する。 */
class CastInvitationAuditTest {
  private static final Long STORE_ID = 1L;
  private static final String SECRET = "secret-token";
  private final CastEnrollmentRepository enrollments = mock(CastEnrollmentRepository.class);
  private final CastInvitationRepository invitations = mock(CastInvitationRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final StoreRepository stores = mock(StoreRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private CastInvitationService issuing;
  private CastInvitationAcceptanceService accepting;
  private CastEnrollment enrollment;
  private CastInvitation invitation;

  @BeforeEach
  void setup() {
    var audit = new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC()));
    issuing = new CastInvitationService(enrollments, invitations, stores, audit);
    var people = mock(CastRepository.class);
    accepting =
        new CastInvitationAcceptanceService(
            invitations,
            enrollments,
            people,
            mock(CastProfileRepository.class),
            users,
            stores,
            encoder,
            audit);
    when(encoder.matches(any(), any())).thenReturn(true);
    when(people.save(any()))
        .thenAnswer(
            call -> {
              Cast person = call.getArgument(0);
              person.setId(person.getUserId());
              return person;
            });
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
    enrollment = CastEnrollment.builder().build();
    enrollment.setId("enrollment-1");
    enrollment.setStoreId(STORE_ID);
    when(enrollments.findByIdAndStoreIdForUpdate("enrollment-1", STORE_ID))
        .thenReturn(Optional.of(enrollment));
    when(enrollments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    invitation =
        CastInvitation.builder()
            .castId("enrollment-1")
            .token(SECRET)
            .expiresAt(OffsetDateTime.now().plusHours(1))
            .build();
    invitation.setId("invitation-1");
    invitation.setStoreId(STORE_ID);
    when(invitations.findByToken(SECRET)).thenReturn(Optional.of(invitation));
    when(invitations.claimPending(any(), anyLong(), any(), any(), any())).thenReturn(1);
    when(encoder.encode(any())).thenReturn("encoded-password");
  }

  private User actor(long id, StoreScopeType scope, Set<Long> storeIds) {
    var user =
        User.builder()
            .email("cast@example.test")
            .password("x")
            .displayName("既存の本人")
            .enabled(true)
            .userType(UserType.CAST)
            .storeScopeType(scope)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    return user;
  }

  private void staffActs() {
    var staff =
        User.builder()
            .email("staff@example.test")
            .password("x")
            .displayName("担当")
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    staff.setId(9L);
    when(users.findByEmail("staff@example.test")).thenReturn(Optional.of(staff));
  }

  @Test
  void issueAuditsStateAndEnrollmentWithoutTheSecret() {
    staffActs();
    when(invitations.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              CastInvitation saved = call.getArgument(0);
              saved.setId("invitation-2");
              return saved;
            });

    var response = issuing.issue(STORE_ID, "enrollment-1", "staff@example.test");

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("CAST_INVITATION_ISSUED");
    assertThat(event.getTargetType()).isEqualTo("CAST_INVITATION");
    assertThat(event.getTargetId()).isEqualTo("invitation-2");
    assertThat(event.getSourceType()).isEqualTo("CAST_ENROLLMENT");
    assertThat(event.getSourceId()).isEqualTo("enrollment-1");
    assertThat(event.getAfterValues()).containsEntry("status", "PENDING").containsKey("expires_at");
    assertThat(event.getAfterValues().keySet())
        .containsExactlyInAnyOrder("status", "expires_at", "version");
    assertThat(event.toString() + event.getAfterValues()).doesNotContain(response.token());
  }

  @Test
  void rejectedIssueRecordsNothing() {
    staffActs();
    enrollment.linkCast(5L);
    assertThatThrownBy(() -> issuing.issue(STORE_ID, "enrollment-1", "staff@example.test"))
        .isInstanceOf(CastInvitationStateException.class);
    assertThat(recorded).isEmpty();
  }

  @Test
  void newUserAcceptanceIsAuditedAsTheGeneratedUser() {
    when(users.findByEmail("new@example.test")).thenReturn(Optional.empty());
    when(users.save(any()))
        .thenAnswer(
            call -> {
              User saved = call.getArgument(0);
              saved.setId(42L);
              when(users.findById(42L)).thenReturn(Optional.of(saved));
              return saved;
            });
    var request = new CastInvitationAcceptRequest();
    request.setToken(SECRET);
    request.setEmail("New@Example.test");
    request.setPassword("password-123");
    request.setDisplayName("新規の本人");

    accepting.acceptAsNewUser(SECRET, request);

    assertThat(recorded).hasSize(1);
    var event = recorded.getFirst();
    assertThat(event.getActorId()).isEqualTo(42L);
    assertThat(event.getAction()).isEqualTo("CAST_INVITATION_ACCEPTED");
    assertThat(event.getTargetId()).isEqualTo("invitation-1");
    assertThat(event.getSourceId()).isEqualTo("enrollment-1");
    assertThat(event.getBeforeValues()).containsEntry("status", "PENDING");
    assertThat(event.getAfterValues())
        .containsEntry("status", "ACCEPTED")
        .containsEntry("acceptance", "NEW_USER")
        .containsEntry("user_id", "42")
        .containsEntry("store_id", "1")
        .containsEntry("cast_enrollment_id", "enrollment-1");
    assertThat(event.getAfterValues().toString())
        .doesNotContain(SECRET, "example.test", "新規の本人", "password");
  }

  @Test
  void existingUserAcceptanceRecordsWhetherMembershipWasAdded() {
    var actor = actor(7L, StoreScopeType.SPECIFIC_STORES, Set.of(2L));
    when(users.findByEmailForUpdate("cast@example.test")).thenReturn(Optional.of(actor));
    when(users.findById(7L)).thenReturn(Optional.of(actor));
    when(enrollments.findIdsByUserIdAndStoreId(7L, STORE_ID)).thenReturn(List.of());

    accepting.acceptAsExistingUser(SECRET, "cast@example.test", "pw");

    var event = recorded.getFirst();
    assertThat(event.getActorId()).isEqualTo(7L);
    assertThat(event.getAfterValues())
        .containsEntry("acceptance", "EXISTING_USER")
        .containsEntry("store_membership_added", "true");
  }

  @Test
  void allStoresUserAcceptanceAddsNoMembership() {
    var actor = actor(8L, StoreScopeType.ALL_STORES, Set.of());
    when(users.findByEmailForUpdate("cast@example.test")).thenReturn(Optional.of(actor));
    when(users.findById(8L)).thenReturn(Optional.of(actor));
    when(enrollments.findIdsByUserIdAndStoreId(8L, STORE_ID)).thenReturn(List.of());

    accepting.acceptAsExistingUser(SECRET, "cast@example.test", "pw");

    assertThat(recorded.getFirst().getAfterValues())
        .containsEntry("store_membership_added", "false");
  }

  @Test
  void rejectedAcceptancesRecordNothing() {
    var actor = actor(7L, StoreScopeType.SPECIFIC_STORES, Set.of(2L));
    when(users.findByEmailForUpdate("cast@example.test")).thenReturn(Optional.of(actor));
    when(enrollments.findIdsByUserIdAndStoreId(7L, STORE_ID)).thenReturn(List.of("other"));
    assertThatThrownBy(() -> accepting.acceptAsExistingUser(SECRET, "cast@example.test", "pw"))
        .isInstanceOf(ConflictException.class);

    when(invitations.claimPending(any(), anyLong(), any(), any(), any())).thenReturn(0);
    assertThatThrownBy(() -> accepting.acceptAsExistingUser(SECRET, "cast@example.test", "pw"))
        .isInstanceOf(CastInvitationStateException.class);

    invitation =
        CastInvitation.builder()
            .castId("enrollment-1")
            .token("expired")
            .expiresAt(OffsetDateTime.now().minusHours(1))
            .build();
    invitation.setStoreId(STORE_ID);
    when(invitations.findByToken("expired")).thenReturn(Optional.of(invitation));
    assertThatThrownBy(() -> accepting.acceptAsExistingUser("expired", "cast@example.test", "pw"))
        .isInstanceOf(CastInvitationStateException.class);
    assertThat(recorded).isEmpty();
  }
}
