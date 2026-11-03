package com.kizuna.storeprofile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.storeprofile.domain.PartnerLink;
import com.kizuna.storeprofile.domain.SnsLink;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfilePatch;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreProfileServiceTest {

  @Mock private StoreProfileRepository storeProfileRepository;
  @Mock private BusinessAudit businessAudit;
  @InjectMocks private StoreProfileService storeProfileService;

  private static final Long STORE_ID = 1L;

  @Test
  void get_returnsExistingConfig() {
    StoreProfile config = createTestConfig();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));

    assertThat(storeProfileService.get(STORE_ID)).isSameAs(config);
  }

  @Test
  void get_throwsExceptionIfNotExists() {
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeProfileService.get(STORE_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("店舗設定が見つかりません");
  }

  @Test
  void update_modifiesFields() {
    StoreProfile config = createTestConfig();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));
    when(storeProfileRepository.saveAndFlush(any())).thenReturn(config);

    StoreProfile saved =
        storeProfileService.update(
            STORE_ID, patch("https://example.com/new-logo.png", "Updated description", null, null));

    assertThat(saved.getLogoUrl()).isEqualTo("https://example.com/new-logo.png");
    assertThat(saved.getDescription()).isEqualTo("Updated description");
    assertThat(saved.getTemplateKey()).isEqualTo("default");
  }

  @Test
  void update_modifiesSnsLinks() {
    StoreProfile config = createTestConfig();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));
    when(storeProfileRepository.saveAndFlush(any())).thenReturn(config);

    List<SnsLink> newSnsLinks =
        List.of(SnsLink.builder().platform("twitter").url("https://twitter.com/test").build());

    StoreProfile saved = storeProfileService.update(STORE_ID, patch(null, null, newSnsLinks, null));

    assertThat(saved.getSnsLinks()).hasSize(1);
    assertThat(saved.getSnsLinks().get(0).getPlatform()).isEqualTo("twitter");
  }

  @Test
  void update_modifiesPartnerLinks() {
    StoreProfile config = createTestConfig();
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(config));
    when(storeProfileRepository.saveAndFlush(any())).thenReturn(config);

    List<PartnerLink> newPartnerLinks =
        List.of(PartnerLink.builder().name("Partner1").url("https://partner.com").build());

    StoreProfile saved =
        storeProfileService.update(STORE_ID, patch(null, null, null, newPartnerLinks));

    assertThat(saved.getPartnerLinks()).hasSize(1);
    assertThat(saved.getPartnerLinks().get(0).getName()).isEqualTo("Partner1");
  }

  @Test
  void update_throwsExceptionIfNotExists() {
    when(storeProfileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeProfileService.update(STORE_ID, patch(null, null, null, null)))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("店舗設定が見つかりません");
  }

  private StoreProfile createTestConfig() {
    StoreProfile config = new StoreProfile();
    config.setId("1");
    config.setTemplateKey("default");
    config.setLogoUrl("https://example.com/logo.png");
    config.setMvType("image");
    config.setSnsLinks(new ArrayList<>());
    config.setPartnerLinks(new ArrayList<>());
    config.setCreatedAt(OffsetDateTime.now());
    config.setUpdatedAt(OffsetDateTime.now());
    return config;
  }

  private static StoreProfilePatch patch(
      String logoUrl, String description, List<SnsLink> snsLinks, List<PartnerLink> partnerLinks) {
    return new StoreProfilePatch(
        null,
        logoUrl,
        null,
        null,
        null,
        description,
        null,
        null,
        null,
        null,
        null,
        null,
        snsLinks,
        partnerLinks);
  }
}
