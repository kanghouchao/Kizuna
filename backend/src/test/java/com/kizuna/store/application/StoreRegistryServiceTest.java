package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreStatusVO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

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
    assertThat(storeRegistryService.list(1, 10, "test").data()).hasSize(1);
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
  void list_handlesNullSearch() {
    Page<Store> page = new PageImpl<>(List.of());
    when(storeRepository.findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
            anyString(), anyString(), any()))
        .thenReturn(page);

    assertThat(storeRegistryService.list(1, 10, null).data()).isEmpty();
  }

  @Test
  void getById_returnsStoreVO() {
    Store t = createStore(1L, "Store1", "store1.com", "a@b.com");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(t));

    Optional<StoreVO> result = storeRegistryService.getById("1");

    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("Store1");
    assertThat(result.get().getDomain()).isEqualTo("store1.com");
  }

  @Test
  void getById_returnsEmptyWhenNotFound() {
    when(storeRepository.findById(99L)).thenReturn(Optional.empty());

    assertThat(storeRegistryService.getById("99")).isEmpty();
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

    storeRegistryService.update("1", req);

    assertThat(t.getName()).isEqualTo("New");
    verify(storeRepository).save(t);
  }

  @Test
  void update_throwsWhenNotFound() {
    when(storeRepository.findById(99L)).thenReturn(Optional.empty());

    StoreUpdateDTO req = new StoreUpdateDTO();
    req.setName("New");

    assertThatThrownBy(() -> storeRegistryService.update("99", req))
        .isInstanceOf(ServiceException.class)
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
    assertThat(result.active()).isEqualTo(5L);
  }

  private Store createStore(Long id, String name, String domain, String email) {
    Store t = new Store();
    t.setId(id);
    t.setName(name);
    t.setDomain(domain);
    t.setEmail(email);
    return t;
  }
}
