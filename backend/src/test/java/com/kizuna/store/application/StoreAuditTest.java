package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.domain.AttendanceRecordCheck;
import com.kizuna.store.domain.CompletedOrderCheck;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class StoreAuditTest {
  private static final Long STORE_ID = 7L;
  private final StoreRepository stores = mock(StoreRepository.class);
  private final CompletedOrderCheck orders = mock(CompletedOrderCheck.class);
  private final AttendanceRecordCheck attendance = mock(AttendanceRecordCheck.class);
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final List<AuditEvent> recorded = new ArrayList<>();
  private StoreRegistryService registry;
  private StoreActivationService activation;
  private Store store;

  @BeforeEach
  void setup() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
    var users = mock(UserRepository.class);
    var actor =
        User.builder()
            .email("operator@example.test")
            .password(UUID.randomUUID().toString())
            .displayName("監査担当")
            .userType(UserType.STAFF)
            .roleIds(Set.of(1L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .build();
    actor.setId(9L);
    when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
    when(events.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              recorded.add(event);
              return event;
            });
    var audit = new BusinessAudit(users, new AuditWriter(events, Clock.systemUTC()));
    registry =
        new StoreRegistryService(
            stores,
            mock(StoreProfileRepository.class),
            orders,
            mock(PointLedgerService.class),
            attendance,
            audit);
    activation = new StoreActivationService(stores, mock(CacheManager.class), audit);
    store = new Store("旧名称", "old.example.test");
    store.setId(STORE_ID);
    ReflectionTestUtils.setField(store, "version", 3L);
    when(stores.findById(STORE_ID)).thenReturn(Optional.of(store));
    when(stores.findByIdForUpdate(STORE_ID)).thenReturn(Optional.of(store));
    when(stores.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              Store saved = call.getArgument(0);
              if (saved.getId() == null) {
                saved.setId(STORE_ID);
                ReflectionTestUtils.setField(saved, "version", 0L);
              } else {
                ReflectionTestUtils.setField(saved, "version", saved.getVersion() + 1);
              }
              return saved;
            });
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createRecordsStateVersionAndDomainUnderTheNewStoreId() {
    var request = new StoreCreateDTO();
    request.setName("新店舗");
    request.setDomain("new.example.test");

    registry.create(request);

    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("STORE_CREATED");
    assertThat(event.getTargetType()).isEqualTo("STORE");
    assertThat(event.getTargetId()).isEqualTo("7");
    assertThat(event.getStoreId()).isEqualTo(STORE_ID);
    assertThat(event.getBeforeValues()).isEmpty();
    assertThat(event.getAfterValues())
        .containsOnly(
            Map.entry("exists", "true"),
            Map.entry("status", "PREPARING"),
            Map.entry("version", "0"),
            Map.entry("domain", "new.example.test"),
            Map.entry("changed_fields", "name,domain"));
    assertThat(event.toString()).doesNotContain("新店舗");
  }

  @Test
  void duplicateDomainCreationRecordsNothing() {
    when(stores.findByDomain("old.example.test")).thenReturn(Optional.of(store));
    var request = new StoreCreateDTO();
    request.setName("重複");
    request.setDomain("old.example.test");

    assertThatThrownBy(() -> registry.create(request)).isInstanceOf(ServiceException.class);

    assertThat(recorded).isEmpty();
  }

  @Test
  void renameRecordsFieldNameAndVersionsButNeverNames() {
    var request = new StoreUpdateDTO();
    request.setName("新名称");

    registry.update("7", request);

    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("STORE_UPDATED");
    assertThat(event.getStoreId()).isEqualTo(STORE_ID);
    assertThat(event.getBeforeValues()).containsOnly(Map.entry("version", "3"));
    assertThat(event.getAfterValues())
        .containsOnly(Map.entry("version", "4"), Map.entry("changed_fields", "name"));
    assertThat(event.toString()).doesNotContain("旧名称", "新名称");
  }

  @Test
  void sameNameUpdateRecordsNothing() {
    var request = new StoreUpdateDTO();
    request.setName("旧名称");

    registry.update("7", request);

    assertThat(recorded).isEmpty();
  }

  @Test
  void deletionRecordsBeforeSnapshotAndCascadeKindsWithCountsOnly() {
    var profiles = cascade("t_store_profiles", 1);
    var shifts = cascade("t_shifts", 0);
    when(stores.countCascade(STORE_ID)).thenReturn(List.of(profiles, shifts));

    registry.delete("7");

    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("STORE_DELETED");
    assertThat(event.getStoreId()).isEqualTo(STORE_ID);
    assertThat(event.getBeforeValues())
        .containsOnly(
            Map.entry("exists", "true"),
            Map.entry("status", "PREPARING"),
            Map.entry("version", "3"),
            Map.entry("domain", "old.example.test"));
    assertThat(event.getAfterValues())
        .containsOnly(
            Map.entry("exists", "false"), Map.entry("cascade_deleted", "t_store_profiles=1"));
    assertThat(event.toString()).doesNotContain("旧名称");
  }

  @Test
  void rejectedOrConstraintFailedDeletionRecordsNothing() {
    when(orders.existsForStore(STORE_ID)).thenReturn(true);
    assertThatThrownBy(() -> registry.delete("7")).isInstanceOf(ServiceException.class);

    when(orders.existsForStore(STORE_ID)).thenReturn(false);
    doThrow(new DataIntegrityViolationException("fk")).when(stores).flush();
    assertThatThrownBy(() -> registry.delete("7")).isInstanceOf(RuntimeException.class);

    store.activate();
    assertThatThrownBy(() -> registry.delete("7")).isInstanceOf(ServiceException.class);

    assertThat(recorded).isEmpty();
  }

  @Test
  void activationRecordsStateAndVersionTransition() {
    activation.activate(STORE_ID);

    var event = recorded.getFirst();
    assertThat(event.getAction()).isEqualTo("STORE_ACTIVATED");
    assertThat(event.getTargetId()).isEqualTo("7");
    assertThat(event.getStoreId()).isEqualTo(STORE_ID);
    assertThat(event.getBeforeValues())
        .containsOnly(Map.entry("status", "PREPARING"), Map.entry("version", "3"));
    assertThat(event.getAfterValues())
        .containsOnly(Map.entry("status", "ACTIVE"), Map.entry("version", "4"));
  }

  @Test
  void repeatedActivationRecordsNothing() {
    activation.activate(STORE_ID);
    recorded.clear();

    assertThatThrownBy(() -> activation.activate(STORE_ID)).isInstanceOf(ServiceException.class);

    assertThat(recorded).isEmpty();
    verify(stores).saveAndFlush(any());
  }

  private static StoreRepository.CascadeCount cascade(String kind, long rows) {
    var count = mock(StoreRepository.CascadeCount.class);
    when(count.getKind()).thenReturn(kind);
    when(count.getRows()).thenReturn(rows);
    return count;
  }
}
