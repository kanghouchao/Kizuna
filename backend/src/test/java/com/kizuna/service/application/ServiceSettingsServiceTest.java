package com.kizuna.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.service.api.dto.ServiceMapper;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class ServiceSettingsServiceTest {
  private final ServiceItemRepository items = mock(ServiceItemRepository.class);
  private final ServiceSettingsService settings =
      new ServiceSettingsService(
          items,
          mock(ServiceRevisionRepository.class),
          Mappers.getMapper(ServiceMapper.class),
          mock(ActorIdentityService.class),
          mock(StoreRepository.class),
          mock(BusinessAudit.class));

  @Test
  @SuppressWarnings("unchecked")
  void listsNewestServicesFirst() {
    when(items.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

    var newestFirst = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    settings.list(7L, null, false, newestFirst);

    var pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(items).findAll(any(Specification.class), pageable.capture());
    assertThat(pageable.getValue()).isEqualTo(newestFirst);
  }
}
