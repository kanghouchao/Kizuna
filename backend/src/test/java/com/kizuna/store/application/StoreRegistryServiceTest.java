package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreStatusVO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class StoreRegistryServiceTest {

  @Mock private StoreRepository storeRepository;
  @Mock private StoreProfileRepository storeProfileRepository;
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
  void create_savesStoreAndDefaultStoreProfile() {
    StoreCreateDTO req = new StoreCreateDTO();
    req.setName("T1");
    req.setDomain("d1.com");

    Store t = new Store();
    t.setId(1L);
    when(storeRepository.save(any())).thenReturn(t);

    storeRegistryService.create(req);

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
  void delete_deletesById() {
    storeRegistryService.delete("1");
    verify(storeRepository).deleteById(1L);
  }

  @Test
  void delete_throwsOnInvalidId() {
    assertThatThrownBy(() -> storeRegistryService.delete("invalid"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("店舗 ID の形式が不正です");
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
