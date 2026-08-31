package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreStatusVO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.domain.AttendanceRecordCheck;
import com.kizuna.store.domain.CompletedOrderCheck;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class StoreRegistryServiceTest {

  @Mock private StoreRepository storeRepository;
  @Mock private StoreProfileRepository storeProfileRepository;
  @Mock private CompletedOrderCheck completedOrderCheck;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private AttendanceRecordCheck attendanceRecordCheck;
  @InjectMocks private StoreRegistryService storeRegistryService;

  @Test
  void list_returnsPage() {
    Page<Store> page = new PageImpl<>(List.of(new Store()));
    when(storeRepository.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
            anyString(), anyString(), any()))
        .thenReturn(page);
    assertThat(storeRegistryService.list("test", PageRequest.of(0, 10)).getContent()).hasSize(1);
  }

  @Test
  void create_savesStoreAndDefaultStoreProfileAndReturnsTheNewId() {
    StoreCreateDTO req = new StoreCreateDTO();
    req.setName("T1");
    req.setDomain("d1.com");

    Store t = new Store();
    t.setId(1L);
    when(storeRepository.save(any())).thenReturn(t);

    assertThat(storeRegistryService.create(req)).isEqualTo(1L);

    verify(storeRepository).save(any());
    verify(storeProfileRepository).save(any());
  }

  @Test
  void create_duplicateDomain_rejectedBeforeInsert() {
    // 重複判定は一意制約違反の捕捉ではなく事前照会で行う（制約違反は競合に敗れた場合の兜底）。
    StoreCreateDTO req = new StoreCreateDTO();
    req.setName("T2");
    req.setDomain("taken.example.com");
    when(storeRepository.findByDomain("taken.example.com"))
        .thenReturn(Optional.of(createStore(9L, "既存", "taken.example.com", "x@y.com")));

    assertThatThrownBy(() -> storeRegistryService.create(req))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既に登録されています");

    verify(storeRepository, never()).save(any());
    verify(storeProfileRepository, never()).save(any());
  }

  @Test
  void list_handlesNullSearch() {
    Page<Store> page = new PageImpl<>(List.of());
    when(storeRepository.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
            anyString(), anyString(), any()))
        .thenReturn(page);

    assertThat(storeRegistryService.list(null, PageRequest.of(0, 10)).getContent()).isEmpty();
  }

  @Test
  void getById_returnsStoreVO() {
    Store t = createStore(1L, "Store1", "store1.com", "a@b.com");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(t));

    StoreVO result = storeRegistryService.getById("1");

    assertThat(result.getName()).isEqualTo("Store1");
    assertThat(result.getDomain()).isEqualTo("store1.com");
    assertThat(result.getCreatedAt()).isEqualTo(t.getCreatedAt());
  }

  @Test
  void getById_throwsNotFoundWhenAbsent() {
    when(storeRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeRegistryService.getById("99"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void getById_throwsOnInvalidId() {
    assertThatThrownBy(() -> storeRegistryService.getById("abc"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("店舗 ID の形式が不正です");
  }

  @Test
  void getByDomain_returnsStoreVO() {
    Store t = createStore(2L, "Store2", "store2.com", "b@c.com");
    when(storeRepository.findByDomain("store2.com")).thenReturn(Optional.of(t));

    Optional<StoreVO> result = storeRegistryService.getByDomain("store2.com");

    assertThat(result).isPresent();
    assertThat(result.get().getDomain()).isEqualTo("store2.com");
  }

  @Test
  void getByDomain_returnsEmptyWhenNotFound() {
    when(storeRepository.findByDomain("unknown.com")).thenReturn(Optional.empty());

    assertThat(storeRegistryService.getByDomain("unknown.com")).isEmpty();
  }

  @Test
  void update_modifiesName() {
    Store t = createStore(1L, "Old", "old.com", "o@o.com");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(t));

    StoreUpdateDTO req = new StoreUpdateDTO();
    req.setName("New");
    req.setEmail("o@o.com");

    storeRegistryService.update("1", req);

    assertThat(t.getName()).isEqualTo("New");
    verify(storeRepository).save(t);
  }

  // 編集画面は name と email を送る。email が取り込まれなければ、成功応答だけ返って値が変わらない。
  @Test
  void update_modifiesEmail() {
    Store t = createStore(1L, "Old", "old.com", "before@example.com");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(t));

    StoreUpdateDTO req = new StoreUpdateDTO();
    req.setName("Old");
    req.setEmail("after@example.com");

    storeRegistryService.update("1", req);

    assertThat(t.getEmail()).isEqualTo("after@example.com");
    assertThat(t.getName()).as("name は据え置きのまま").isEqualTo("Old");
    verify(storeRepository).save(t);
  }

  @Test
  void update_throwsWhenNotFound() {
    when(storeRepository.findById(99L)).thenReturn(Optional.empty());

    StoreUpdateDTO req = new StoreUpdateDTO();
    req.setName("New");
    req.setEmail("n@n.com");

    assertThatThrownBy(() -> storeRegistryService.update("99", req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("店舗が見つかりません");
  }

  @Test
  void delete_preparingStoreWithoutRecords_deletesById() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(false);
    when(pointLedgerService.hasEntriesForStore(1L)).thenReturn(false);

    storeRegistryService.delete("1");

    verify(storeRepository).deleteById(1L);
  }

  // 稼働中の店舗は、確定した記録の有無に関わらず消せない。開店した事実そのものが削除を止める。
  @Test
  void delete_activeStore_isRejected() {
    Store active = preparingStore(1L);
    active.activate();
    when(storeRepository.findById(1L)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> storeRegistryService.delete("1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("稼働中の店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  @Test
  void delete_storeWithCompletedOrder_isRejected() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(true);

    assertThatThrownBy(() -> storeRegistryService.delete("1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  // 台帳は会員が持つが、発生店舗の帰属が残る限りその店舗は消せない（消すと帰属だけが静かに外れる）。
  @Test
  void delete_storeWithPointEntry_isRejected() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(false);
    when(pointLedgerService.hasEntriesForStore(1L)).thenReturn(true);

    assertThatThrownBy(() -> storeRegistryService.delete("1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  @Test
  void delete_storeWithAttendance_isRejected() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(false);
    when(pointLedgerService.hasEntriesForStore(1L)).thenReturn(false);
    when(attendanceRecordCheck.existsForStore(1L)).thenReturn(true);

    assertThatThrownBy(() -> storeRegistryService.delete("1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("当日実績が記録されている店舗は削除できません");
    verify(storeRepository, never()).deleteById(any());
  }

  // 発動記録は応用層で数えず、外部キー（NO ACTION）の違反を flush でこの場に顕在化させて写像する。
  @Test
  void delete_storeWithEmergencyElevation_isRejectedViaConstraintTranslation() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(preparingStore(1L)));
    when(completedOrderCheck.existsForStore(1L)).thenReturn(false);
    when(pointLedgerService.hasEntriesForStore(1L)).thenReturn(false);
    when(attendanceRecordCheck.existsForStore(1L)).thenReturn(false);
    doThrow(
            new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                    "違反", new SQLException("外部キー違反"), "fk_t_emergency_elevations_store")))
        .when(storeRepository)
        .flush();

    assertThatThrownBy(() -> storeRegistryService.delete("1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("緊急昇格の記録が存在する店舗は削除できません");
  }

  @Test
  void delete_missingStore_throwsNotFound() {
    when(storeRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeRegistryService.delete("99"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("店舗が見つかりません");
  }

  @Test
  void delete_throwsOnInvalidId() {
    assertThatThrownBy(() -> storeRegistryService.delete("invalid"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("店舗 ID の形式が不正です");
  }

  private Store preparingStore(Long id) {
    Store store = new Store("削除検証店舗", "delete-check.example.com", null);
    store.setId(id);
    return store;
  }

  @Test
  void stats_returnsStatusVO() {
    when(storeRepository.count()).thenReturn(5L);

    StoreStatusVO result = storeRegistryService.stats();

    assertThat(result.total()).isEqualTo(5L);
  }

  private Store createStore(Long id, String name, String domain, String email) {
    Store t = new Store();
    t.setId(id);
    t.setName(name);
    t.setDomain(domain);
    t.setEmail(email);
    t.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    return t;
  }
}
