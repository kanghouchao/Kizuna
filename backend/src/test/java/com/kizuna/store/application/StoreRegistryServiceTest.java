package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.domain.AttendanceRecordCheck;
import com.kizuna.store.domain.CompletedOrderCheck;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StorePatch;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.CreatedStoreGrant;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class StoreRegistryServiceTest {

  @Mock private StoreRepository storeRepository;
  @Mock private StoreProfileRepository storeProfileRepository;
  @Mock private CompletedOrderCheck completedOrderCheck;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private AttendanceRecordCheck attendanceRecordCheck;
  @Mock private BusinessAudit businessAudit;
  @Mock private CreatedStoreGrant createdStoreGrant;
  @InjectMocks private StoreRegistryService storeRegistryService;

  private static final StoreScope ALL = new StoreScope(true, Set.of());
  private static final StoreScope ONLY_2 = new StoreScope(false, Set.of(2L));

  @Test
  void list_returnsPage() {
    Page<Store> page = new PageImpl<>(List.of(new Store()));
    when(storeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    assertThat(storeRegistryService.list(ALL, "test", PageRequest.of(0, 10)).getContent())
        .hasSize(1);
  }

  @Test
  void create_savesStoreAndDefaultStoreProfileAndReturnsTheNewId() {
    var command = new StoreCreateCommand("T1", "新宿西", "d1.com");

    Store t = new Store("T1", "新", "d1.com");
    t.setId(1L);
    when(storeRepository.saveAndFlush(any())).thenReturn(t);

    assertThat(storeRegistryService.create(command)).isEqualTo(1L);

    ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
    verify(storeRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getShortName()).isEqualTo("新宿西");
    verify(storeProfileRepository).save(any());
    verify(createdStoreGrant).grantToCurrent(1L);
  }

  @Test
  void create_duplicateDomain_rejectedBeforeInsert() {
    // 重複判定は一意制約違反の捕捉ではなく事前照会で行う（制約違反は競合に敗れた場合の兜底）。
    var command = new StoreCreateCommand("T2", null, "taken.example.com");
    when(storeRepository.findByDomain("taken.example.com"))
        .thenReturn(Optional.of(createStore(9L, "既存", "taken.example.com")));

    assertThatThrownBy(() -> storeRegistryService.create(command))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既に登録されています");

    verify(storeRepository, never()).saveAndFlush(any());
    verify(storeProfileRepository, never()).save(any());
  }

  @Test
  void create_duplicateShortName_mapsUniqueViolationToConflict() {
    var command = new StoreCreateCommand("T3", "新宿", "fresh.example.com");
    when(storeRepository.saveAndFlush(any())).thenThrow(shortNameViolation());

    assertThatThrownBy(() -> storeRegistryService.create(command))
        .isInstanceOf(ConflictException.class)
        .hasMessage("この略称は既に使われています");

    verify(storeProfileRepository, never()).save(any());
  }

  @Test
  void update_duplicateShortName_mapsUniqueViolationToConflict() {
    Store t = createStore(1L, "Old", "old.com");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(t));
    when(storeRepository.saveAndFlush(t)).thenThrow(shortNameViolation());
    var patch = new StorePatch("New", "新宿");

    assertThatThrownBy(() -> storeRegistryService.update(ALL, "1", patch))
        .isInstanceOf(ConflictException.class)
        .hasMessage("この略称は既に使われています");
  }

  private static DataIntegrityViolationException shortNameViolation() {
    return new DataIntegrityViolationException(
        "save failed",
        new ConstraintViolationException(
            "duplicate",
            new SQLException("duplicate"),
            DbConstraint.UQ_T_STORES_SHORT_NAME.sqlName()));
  }

  @Test
  void list_handlesNullSearch() {
    Page<Store> page = new PageImpl<>(List.of());
    when(storeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    assertThat(storeRegistryService.list(ALL, null, PageRequest.of(0, 10)).getContent()).isEmpty();
  }

  @Test
  void getById_returnsStoreView() {
    Store t = createStore(1L, "Store1", "store1.com");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(t));

    StoreView result = storeRegistryService.getById(ALL, "1");

    assertThat(result.name()).isEqualTo("Store1");
    assertThat(result.domain()).isEqualTo("store1.com");
    assertThat(result.createdAt()).isEqualTo(t.getCreatedAt());
  }

  @Test
  void getById_throwsNotFoundWhenAbsent() {
    when(storeRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeRegistryService.getById(ALL, "99"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void getById_throwsOnInvalidId() {
    assertThatThrownBy(() -> storeRegistryService.getById(ALL, "abc"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("店舗 ID の形式が不正です");
  }

  @Test
  void getByDomain_returnsStoreView() {
    Store t = createStore(2L, "Store2", "store2.com");
    when(storeRepository.findByDomain("store2.com")).thenReturn(Optional.of(t));

    Optional<StoreView> result = storeRegistryService.getByDomain("store2.com");

    assertThat(result).isPresent();
    assertThat(result.get().domain()).isEqualTo("store2.com");
  }

  @Test
  void getByDomain_returnsEmptyWhenNotFound() {
    when(storeRepository.findByDomain("unknown.com")).thenReturn(Optional.empty());

    assertThat(storeRegistryService.getByDomain("unknown.com")).isEmpty();
  }

  @Test
  void update_modifiesName() {
    Store t = createStore(1L, "Old", "old.com");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(t));

    var patch = new StorePatch("New", "新");

    storeRegistryService.update(ALL, "1", patch);

    assertThat(t.getName()).isEqualTo("New");
    assertThat(t.getShortName()).isEqualTo("新");
    verify(storeRepository).saveAndFlush(t);
  }

  @Test
  void update_throwsWhenNotFound() {
    when(storeRepository.findById(99L)).thenReturn(Optional.empty());

    var patch = new StorePatch("New", null);

    assertThatThrownBy(() -> storeRegistryService.update(ALL, "99", patch))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("店舗が見つかりません");
  }

  @Test
  void delete_preparingStoreWithoutRecords_deletesById() {
    when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(false);
    when(pointLedgerService.hasEntriesForStore(1L)).thenReturn(false);

    storeRegistryService.delete(ALL, "1");

    verify(storeRepository).deleteById(1L);
  }

  // 稼働中の店舗は、確定した記録の有無に関わらず消せない。開店した事実そのものが削除を止める。
  @Test
  void delete_activeStore_isRejected() {
    Store active = preparingStore(1L);
    active.activate();
    when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("稼働中の店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  @Test
  void delete_storeWithCompletedOrder_isRejected() {
    when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(true);

    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  // 台帳は会員が持つが、発生店舗の帰属が残る限りその店舗は消せない（消すと帰属だけが静かに外れる）。
  @Test
  void delete_storeWithPointEntry_isRejected() {
    when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(false);
    when(pointLedgerService.hasEntriesForStore(1L)).thenReturn(true);

    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  @Test
  void delete_storeWithAttendance_isRejected() {
    when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(false);
    when(pointLedgerService.hasEntriesForStore(1L)).thenReturn(false);
    when(attendanceRecordCheck.existsForStore(1L)).thenReturn(true);

    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("当日実績が記録されている店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  @ParameterizedTest
  @CsvSource({
    "fk_t_services_store, サービス設定が存在する店舗は削除できません",
    "fk_t_service_revisions_store, サービス変更履歴が存在する店舗は削除できません",
    "fk_t_guarantee_terms_store_id, 保証条件の記録が存在する店舗は削除できません",
    "fk_t_bonus_awards_store_id, ボーナスの記録が存在する店舗は削除できません"
  })
  void delete_storeWithRetainedRecords_translatesConstraint(String constraint, String message) {
    when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(preparingStore(1L)));
    doThrow(
            new DataIntegrityViolationException(
                "削除拒否",
                new ConstraintViolationException("違反", new SQLException("外部キー違反"), constraint)))
        .when(storeRepository)
        .flush();

    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage(message);
  }

  @ParameterizedTest
  @ValueSource(strings = {"fk_t_applicant_uploads_store", "fk_t_applicant_uploads_applicant_store"})
  void delete_storeWithPrivateAttachmentOwnership_isConflict(String constraint) {
    when(storeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(preparingStore(1L)));
    doThrow(
            new DataIntegrityViolationException(
                "削除拒否",
                new ConstraintViolationException("違反", new SQLException("外部キー違反"), constraint)))
        .when(storeRepository)
        .flush();
    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "1"))
        .isInstanceOf(ConflictException.class)
        .hasMessage("非公開添付の記録が存在する店舗は削除できません");
  }

  @Test
  void delete_missingStore_throwsNotFound() {
    when(storeRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "99"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("店舗が見つかりません");
  }

  @Test
  void delete_throwsOnInvalidId() {
    assertThatThrownBy(() -> storeRegistryService.delete(ALL, "invalid"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("店舗 ID の形式が不正です");
  }

  private Store preparingStore(Long id) {
    Store store = new Store("削除検証店舗", "削", "delete-check.example.com");
    store.setId(id);
    return store;
  }

  @Test
  void count_returnsStoreCount() {
    when(storeRepository.count(any(Specification.class))).thenReturn(5L);

    assertThat(storeRegistryService.count(ALL)).isEqualTo(5L);
  }

  @Test
  void count_emptyScopeIsZeroWithoutQuerying() {
    assertThat(storeRegistryService.count(new StoreScope(false, Set.of()))).isZero();
    verify(storeRepository, never()).count(any(Specification.class));
  }

  @Test
  void list_emptyScopeIsEmptyWithoutQuerying() {
    assertThat(
            storeRegistryService.list(new StoreScope(false, Set.of()), null, PageRequest.of(0, 10)))
        .isEmpty();
    verify(storeRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  // 集合外の店舗は、稼働中かどうか・略称が重複するかを確かめる前に 404 にする。先に業務検証すると応答から存在が漏れる。
  @Test
  void outsideScope_isNotFoundBeforeAnyLookup() {
    var patch = new StorePatch("New", "新");

    assertThatThrownBy(() -> storeRegistryService.getById(ONLY_2, "1"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> storeRegistryService.update(ONLY_2, "1", patch))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> storeRegistryService.delete(ONLY_2, "1"))
        .isInstanceOf(NotFoundException.class);

    verify(storeRepository, never()).findById(any());
    verify(storeRepository, never()).findByIdForUpdate(any());
  }

  private Store createStore(Long id, String name, String domain) {
    Store t = new Store();
    t.setId(id);
    t.setName(name);
    t.setDomain(domain);
    t.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    return t;
  }
}
