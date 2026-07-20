package com.kizuna.storeprofile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.storeprofile.api.dto.StoreProfileMapper;
import com.kizuna.storeprofile.api.dto.StoreProfileResponse;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;
import com.kizuna.storeprofile.domain.PartnerLink;
import com.kizuna.storeprofile.domain.SnsLink;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreProfileServiceTest {

  @Mock private StoreProfileRepository storeProfileRepository;
  @Mock private StoreContext storeContext;
  @Mock private StoreProfileMapper storeProfileMapper;
  @InjectMocks private StoreProfileService storeProfileService;

  private static final Long STORE_ID = 1L;

  @BeforeEach
  void setUp() {
    when(storeContext.getStoreId()).thenReturn(STORE_ID);
  }

  @Test
  void get_returnsExistingConfig() {
    StoreProfile config = createTestConfig();
    StoreProfileResponse expected = createTestResponse();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));
    when(storeProfileMapper.toResponse(config)).thenReturn(expected);

    StoreProfileResponse response = storeProfileService.get();

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTemplateKey()).isEqualTo("default");
    assertThat(response.getLogoUrl()).isEqualTo("https://example.com/logo.png");
  }

  @Test
  void get_throwsExceptionIfNotExists() {
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeProfileService.get())
        .isInstanceOf(ServiceException.class)
        .hasMessage("店舗設定が見つかりません");
  }

  @Test
  void update_modifiesFields() {
    StoreProfile config = createTestConfig();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));
    when(storeProfileRepository.saveAndFlush(any())).thenReturn(config);

    StoreProfileUpdateRequest request = new StoreProfileUpdateRequest();
    request.setLogoUrl("https://example.com/new-logo.png");
    request.setDescription("Updated description");

    doAnswer(
            invocation -> {
              StoreProfileUpdateRequest req = invocation.getArgument(0);
              StoreProfile target = invocation.getArgument(1);
              if (req.getLogoUrl() != null) target.setLogoUrl(req.getLogoUrl());
              if (req.getDescription() != null) target.setDescription(req.getDescription());
              return null;
            })
        .when(storeProfileMapper)
        .updateEntityFromRequest(eq(request), eq(config));

    StoreProfileResponse expected = createTestResponse();
    expected.setLogoUrl("https://example.com/new-logo.png");
    expected.setDescription("Updated description");
    when(storeProfileMapper.toResponse(config)).thenReturn(expected);

    StoreProfileResponse response = storeProfileService.update(request);

    assertThat(response.getLogoUrl()).isEqualTo("https://example.com/new-logo.png");
    assertThat(response.getDescription()).isEqualTo("Updated description");
    verify(storeProfileMapper).updateEntityFromRequest(request, config);
  }

  @Test
  void update_modifiesSnsLinks() {
    StoreProfile config = createTestConfig();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));
    when(storeProfileRepository.saveAndFlush(any())).thenReturn(config);

    StoreProfileUpdateRequest request = new StoreProfileUpdateRequest();
    List<SnsLink> newSnsLinks =
        List.of(SnsLink.builder().platform("twitter").url("https://twitter.com/test").build());
    request.setSnsLinks(newSnsLinks);

    StoreProfileResponse expected = createTestResponse();
    expected.setSnsLinks(newSnsLinks);
    when(storeProfileMapper.toResponse(config)).thenReturn(expected);

    StoreProfileResponse response = storeProfileService.update(request);

    assertThat(response.getSnsLinks()).hasSize(1);
    assertThat(response.getSnsLinks().get(0).getPlatform()).isEqualTo("twitter");
    verify(storeProfileMapper).updateEntityFromRequest(request, config);
  }

  @Test
  void update_modifiesPartnerLinks() {
    StoreProfile config = createTestConfig();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));
    when(storeProfileRepository.saveAndFlush(any())).thenReturn(config);

    StoreProfileUpdateRequest request = new StoreProfileUpdateRequest();
    List<PartnerLink> newPartnerLinks =
        List.of(PartnerLink.builder().name("Partner1").url("https://partner.com").build());
    request.setPartnerLinks(newPartnerLinks);

    StoreProfileResponse expected = createTestResponse();
    expected.setPartnerLinks(newPartnerLinks);
    when(storeProfileMapper.toResponse(config)).thenReturn(expected);

    StoreProfileResponse response = storeProfileService.update(request);

    assertThat(response.getPartnerLinks()).hasSize(1);
    assertThat(response.getPartnerLinks().get(0).getName()).isEqualTo("Partner1");
    verify(storeProfileMapper).updateEntityFromRequest(request, config);
  }

  @Test
  void update_throwsExceptionIfNotExists() {
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.empty());

    StoreProfileUpdateRequest request = new StoreProfileUpdateRequest();

    assertThatThrownBy(() -> storeProfileService.update(request))
        .isInstanceOf(ServiceException.class)
        .hasMessage("店舗設定が見つかりません");
  }

  private StoreProfile createTestConfig() {
    StoreProfile config = new StoreProfile();
    config.setId(1L);
    config.setTemplateKey("default");
    config.setLogoUrl("https://example.com/logo.png");
    config.setMvType("image");
    config.setSnsLinks(new ArrayList<>());
    config.setPartnerLinks(new ArrayList<>());
    config.setCreatedAt(OffsetDateTime.now());
    config.setUpdatedAt(OffsetDateTime.now());
    return config;
  }

  private StoreProfileResponse createTestResponse() {
    return StoreProfileResponse.builder()
        .id(1L)
        .templateKey("default")
        .logoUrl("https://example.com/logo.png")
        .mvType("image")
        .snsLinks(List.of())
        .partnerLinks(List.of())
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }
}
