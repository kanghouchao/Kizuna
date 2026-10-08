package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.domain.MemberIdentityView;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerMemberLinkAuditTest {
  private final CustomerRepository customers = mock(CustomerRepository.class);
  private final CustomerMemberLinkRepository links = mock(CustomerMemberLinkRepository.class);
  private final MemberRepository members = mock(MemberRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> audit = new ArrayList<>();
  private CustomerMemberLinkService service;

  @BeforeEach
  void prepare() {
    var actor =
        PlatformUser.builder()
            .email("operator@example.test")
            .displayName("監査担当")
            .password(UUID.randomUUID().toString())
            .userType(UserType.STAFF)
            .enabled(true)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    when(users.findById(9L)).thenReturn(Optional.of(actor));
    when(customers.findByIdForUpdate("customer"))
        .thenReturn(Optional.of(Customer.builder().build()));
    when(members.findIdentityByMemberCode("秘密の会員コード"))
        .thenReturn(
            Optional.of(
                new MemberIdentityView() {
                  public Long getId() {
                    return 7L;
                  }

                  public String getMemberCode() {
                    return "秘密の会員コード";
                  }
                }));
    when(links.saveAndFlush(any(CustomerMemberLink.class)))
        .thenAnswer(
            call -> {
              CustomerMemberLink link = call.getArgument(0);
              if (link.getId() == null) link.setId("link");
              link.setStoreId(1L);
              return link;
            });
    when(events.saveAndFlush(any(AuditEvent.class)))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              audit.add(event);
              return event;
            });
    service =
        new CustomerMemberLinkService(
            customers,
            links,
            new MemberLookupService(members),
            users,
            new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC())));
  }

  @Test
  void replacementRecordsBothReleasedAndNewIntervals() {
    var old = activeLink();
    when(links.findByCustomerIdAndStatus("customer", LinkStatus.ACTIVE))
        .thenReturn(Optional.of(old));
    service.link("customer", "秘密の会員コード", old.getId(), "秘密の変更理由", "operator@example.test");
    assertThat(audit)
        .extracting(AuditEvent::getAction)
        .containsExactly("CUSTOMER_MEMBER_LINK_RELEASED", "CUSTOMER_MEMBER_LINK_CREATED");
    var released = audit.getFirst();
    assertThat(released.getTargetId()).isEqualTo("old-link");
    assertThat(released.getBeforeValues())
        .containsEntry("status", "ACTIVE")
        .containsEntry("member_id", "8")
        .containsEntry("released_at", "");
    assertThat(released.getAfterValues())
        .containsEntry("status", "RELEASED")
        .containsEntry("released_by", "9");
    assertThat(released.getAfterValues().get("released_at")).isNotBlank();
    assertThat(audit.getLast().getTargetId()).isEqualTo("link");
    assertThat(audit.getLast().getAfterValues()).containsEntry("member_id", "7");
    assertThat(audit)
        .allSatisfy(
            event -> {
              assertThat(event.getSourceId()).isEqualTo("customer");
              assertThat(event.getBeforeValues().toString() + event.getAfterValues())
                  .doesNotContain("秘密");
            });
  }

  private CustomerMemberLink activeLink() {
    var link =
        CustomerMemberLink.builder()
            .customerId("customer")
            .memberId(8L)
            .memberCode("秘密の旧会員コード")
            .reason(LinkReason.MEMBER_CODE)
            .operationReason("秘密の旧理由")
            .linkedBy(4L)
            .linkedAt(OffsetDateTime.parse("2026-07-01T10:00:00Z"))
            .build();
    link.setId("old-link");
    link.setStoreId(1L);
    return link;
  }

  @Test
  void explicitReleasePreservesItsOriginalInterval() {
    var old = activeLink();
    when(links.findByCustomerIdAndStatus("customer", LinkStatus.ACTIVE))
        .thenReturn(Optional.of(old));
    service.unlink("customer", old.getId(), "秘密の解除理由", "operator@example.test");
    assertThat(audit).hasSize(1);
    var event = audit.getFirst();
    assertThat(event.getTargetId()).isEqualTo(old.getId());
    assertThat(event.getAction()).isEqualTo("CUSTOMER_MEMBER_LINK_RELEASED");
    assertThat(event.getBeforeValues())
        .containsEntry("status", "ACTIVE")
        .containsEntry("linked_by", "4");
    assertThat(event.getAfterValues())
        .containsEntry("status", "RELEASED")
        .containsEntry("member_id", "8")
        .containsEntry("linked_by", "4")
        .containsEntry("released_by", "9");
    assertThat(event.getAfterValues().toString()).doesNotContain("秘密");
  }

  @Test
  void staleExpectedIdOccupiedMemberAndMergedCustomerDoNotProduceSuccessfulChanges() {
    var old = activeLink();
    when(links.findByCustomerIdAndStatus("customer", LinkStatus.ACTIVE))
        .thenReturn(Optional.of(old));
    assertThatThrownBy(() -> service.unlink("customer", "stale", "理由", "operator@example.test"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () -> service.link("customer", "秘密の会員コード", "stale", "理由", "operator@example.test"))
        .isInstanceOf(ConflictException.class);
    when(links.existsByMemberIdAndStatus(7L, LinkStatus.ACTIVE)).thenReturn(true);
    assertThatThrownBy(
            () -> service.link("customer", "秘密の会員コード", old.getId(), "理由", "operator@example.test"))
        .isInstanceOf(ConflictException.class);
    when(customers.isMerged("customer")).thenReturn(true);
    assertThatThrownBy(() -> service.unlink("customer", old.getId(), "理由", "operator@example.test"))
        .isInstanceOf(ConflictException.class);
    assertThat(audit).isEmpty();
  }

  @Test
  void creationRecordsTheIntervalWithoutCopyingItsCodeOrReason() {
    var result = service.link("customer", "秘密の会員コード", null, "秘密の理由", "operator@example.test");
    assertThat(audit).hasSize(1);
    var event = audit.getFirst();
    assertThat(event.getAction()).isEqualTo("CUSTOMER_MEMBER_LINK_CREATED");
    assertThat(event.getTargetType()).isEqualTo("CUSTOMER_MEMBER_LINK");
    assertThat(event.getTargetId()).isEqualTo(result.id());
    assertThat(event.getSourceType()).isEqualTo("CUSTOMER");
    assertThat(event.getSourceId()).isEqualTo("customer");
    assertThat(event.getActorId()).isEqualTo(9L);
    assertThat(event.getStoreId()).isEqualTo(1L);
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues())
        .containsOnlyKeys(
            "customer_id",
            "member_id",
            "status",
            "reason",
            "version",
            "linked_by",
            "linked_at",
            "released_by",
            "released_at")
        .containsEntry("customer_id", "customer")
        .containsEntry("member_id", "7")
        .containsEntry("status", "ACTIVE")
        .containsEntry("reason", "MEMBER_CODE")
        .containsEntry("linked_by", "9")
        .containsEntry("released_by", "")
        .containsEntry("released_at", "");
    assertThat(event.getAfterValues().toString()).doesNotContain("秘密", "operator@example.test");
  }
}
