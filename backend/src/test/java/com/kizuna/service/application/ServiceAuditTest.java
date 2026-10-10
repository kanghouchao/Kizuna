package com.kizuna.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.order.application.OrderSpecialServices;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderSpecialServiceEventRepository;
import com.kizuna.service.api.dto.OwnConsentRequest;
import com.kizuna.service.api.dto.ServiceCreateRequest;
import com.kizuna.service.api.dto.ServiceMapper;
import com.kizuna.service.api.dto.ServiceUpdateRequest;
import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ConsentDecision;
import com.kizuna.service.domain.ServiceConsent;
import com.kizuna.service.domain.ServiceConsentEvent;
import com.kizuna.service.domain.ServiceConsentEventRepository;
import com.kizuna.service.domain.ServiceConsentRepository;
import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.service.domain.ServiceTerms;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceAuditTest {
  private final ServiceItemRepository items = mock(ServiceItemRepository.class);
  private final ServiceRevisionRepository revisions = mock(ServiceRevisionRepository.class);
  private final PlatformUserRepository users = mock(PlatformUserRepository.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private final BusinessAudit audit =
      new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC()));
  private ServiceSettingsService settings;

  @BeforeEach
  void setup() {
    var store = new StoreContext();
    store.setStoreId(1L);
    settings =
        new ServiceSettingsService(
            items,
            revisions,
            Mappers.getMapper(ServiceMapper.class),
            new ActorIdentityService(users),
            mock(StoreRepository.class),
            store,
            audit);
    var actor =
        PlatformUser.builder()
            .email("operator@example.test")
            .password(UUID.randomUUID().toString())
            .displayName("監査担当")
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail("operator@example.test")).thenReturn(Optional.of(actor));
    when(users.findById(9L)).thenReturn(Optional.of(actor));
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
    when(items.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ServiceItem item = call.getArgument(0);
              item.setId("service");
              item.setStoreId(1L);
              ReflectionTestUtils.setField(item, "version", 0L);
              return item;
            });
    when(revisions.save(any()))
        .thenAnswer(
            call -> {
              ServiceRevision revision = call.getArgument(0);
              revision.setId("revision");
              return revision;
            });
  }

  @Test
  void creationReferencesExistingRevisionWithoutCopyingServiceContent() {
    assertThat(
            settings.create(
                new ServiceCreateRequest(ServiceKind.COURSE, "秘密の名称", 60, null, 12000, 6000),
                "operator@example.test"))
        .isEqualTo("service");
    assertThat(recorded)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getAction()).isEqualTo("SERVICE_CREATED");
              assertThat(event.getTargetType()).isEqualTo("SERVICE");
              assertThat(event.getTargetId()).isEqualTo("service");
              assertThat(event.getSourceType()).isEqualTo("SERVICE_REVISION");
              assertThat(event.getSourceId()).isEqualTo("revision");
              assertThat(event.getActorId()).isEqualTo(9L);
              assertThat(event.getBeforeValues()).isEmpty();
              assertThat(event.getAfterValues())
                  .containsOnlyKeys(
                      "exists", "deleted", "version", "revision_number", "terms_version")
                  .containsEntry("exists", "true")
                  .containsEntry("deleted", "false")
                  .containsEntry("version", "0")
                  .containsEntry("revision_number", "1")
                  .containsEntry("terms_version", "1");
            });
  }

  @Test
  void editAndLogicalDeletionReferenceHistoryWhileSameValuesStaySilent() {
    var item =
        ServiceItem.create(new ServiceTerms(ServiceKind.COURSE, "旧い秘密", 60, null, 12000, 6000));
    item.setId("service");
    item.setStoreId(1L);
    ReflectionTestUtils.setField(item, "version", 4L);
    when(items.findForUpdate("service")).thenReturn(Optional.of(item));
    settings.update(
        "service",
        new ServiceUpdateRequest("旧い秘密", 60, null, 12000, 6000, 1L),
        "operator@example.test");
    assertThat(recorded).isEmpty();
    settings.update(
        "service",
        new ServiceUpdateRequest("新しい秘密", 60, null, 12000, 6000, 1L),
        "operator@example.test");
    assertThat(recorded)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getAction()).isEqualTo("SERVICE_UPDATED");
              assertThat(event.getBeforeValues()).containsEntry("revision_number", "1");
              assertThat(event.getAfterValues())
                  .containsOnlyKeys(
                      "exists",
                      "deleted",
                      "version",
                      "revision_number",
                      "terms_version",
                      "redacted_fields_changed")
                  .containsEntry("redacted_fields_changed", "name")
                  .containsEntry("revision_number", "2")
                  .containsEntry("terms_version", "1");
              assertThat(event.getSourceId()).isEqualTo("revision");
            });
    settings.delete("service", 2L, "operator@example.test");
    assertThat(recorded).hasSize(2);
    assertThat(recorded.getLast().getAction()).isEqualTo("SERVICE_DELETED");
    assertThat(recorded.getLast().getAfterValues())
        .containsOnlyKeys("exists", "deleted", "version", "revision_number", "terms_version")
        .containsEntry("exists", "true")
        .containsEntry("deleted", "true")
        .containsEntry("revision_number", "3");
  }

  @Test
  void ownDecisionReferencesRealConsentEventAndSuppressesDuplicateChoice() {
    var store = new StoreContext();
    store.setStoreId(1L);
    var stores = mock(StoreRepository.class);
    var enrollments = mock(CastEnrollmentRepository.class);
    var consents = mock(ServiceConsentRepository.class);
    var consentEvents = mock(ServiceConsentEventRepository.class);
    var actors = new ActorIdentityService(users);
    var rejection =
        new OrderSpecialServices(
            new OrderSpecialServiceCatalog(items, revisions, consents, consentEvents, enrollments),
            mock(OrderSpecialServiceEventRepository.class),
            mock(OrderRepository.class),
            stores,
            store,
            actors);
    var own =
        new OwnServiceConditionService(
            items,
            revisions,
            consents,
            consentEvents,
            enrollments,
            actors,
            stores,
            store,
            rejection,
            audit);
    var item =
        ServiceItem.create(
            new ServiceTerms(
                ServiceKind.SPECIAL_SERVICE, "秘密の内容", null, ChargeType.PAID, 2000, 1000));
    item.setId("service");
    item.setStoreId(1L);
    var revision = ServiceRevision.record(item, null, 9L);
    revision.setId("revision");
    when(items.findForUpdate("service")).thenReturn(Optional.of(item));
    when(revisions.findByServiceIdAndRevisionNumber("service", 1L))
        .thenReturn(Optional.of(revision));
    when(enrollments.findIdsByPlatformUserIdAndStoreId(9L, 1L)).thenReturn(List.of("enrollment"));
    var consent = ServiceConsent.create("enrollment", "service");
    when(consents.findByEnrollmentIdAndServiceId("enrollment", "service"))
        .thenReturn(Optional.of(consent));
    when(consents.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ServiceConsent saved = call.getArgument(0);
              saved.setId("consent");
              saved.setStoreId(1L);
              ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });
    when(consentEvents.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ServiceConsentEvent event = call.getArgument(0);
              event.setId("decision-event");
              return event;
            });
    own.decide(
        "operator@example.test",
        "service",
        new OwnConsentRequest(1L, 0L, ConsentDecision.ACCEPTED));
    assertThat(recorded)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getAction()).isEqualTo("SERVICE_CONSENT_CHANGED");
              assertThat(event.getTargetType()).isEqualTo("SERVICE_CONSENT");
              assertThat(event.getTargetId()).isEqualTo("consent");
              assertThat(event.getSourceType()).isEqualTo("SERVICE_CONSENT_EVENT");
              assertThat(event.getSourceId()).isEqualTo("decision-event");
              assertThat(event.getBeforeValues()).isEmpty();
              assertThat(event.getAfterValues())
                  .containsOnlyKeys(
                      "exists",
                      "version",
                      "revision_number",
                      "terms_version",
                      "decision",
                      "service_id",
                      "enrollment_id",
                      "service_revision_id")
                  .containsEntry("decision", "ACCEPTED")
                  .containsEntry("service_revision_id", "revision");
            });
    own.decide(
        "operator@example.test",
        "service",
        new OwnConsentRequest(1L, 1L, ConsentDecision.ACCEPTED));
    assertThat(recorded).hasSize(1);
    own.decide(
        "operator@example.test",
        "service",
        new OwnConsentRequest(1L, 1L, ConsentDecision.REJECTED));
    assertThat(recorded).hasSize(2);
    assertThat(recorded.getLast().getBeforeValues()).containsEntry("decision", "ACCEPTED");
    assertThat(recorded.getLast().getAfterValues()).containsEntry("decision", "REJECTED");
  }
}
