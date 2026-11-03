package com.kizuna.storeprofile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreProfileTest {

  @Test
  void nullFieldsKeepTheCurrentValues() {
    var profile = profile();

    profile.apply(
        new StoreProfilePatch(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null));

    assertThat(profile.getTemplateKey()).isEqualTo("default");
    assertThat(profile.getLogoUrl()).isEqualTo("https://example.test/logo.png");
    assertThat(profile.getDescription()).isEqualTo("元の紹介文");
    assertThat(profile.getCustomTexts()).containsOnly(Map.entry("greeting", "こんにちは"));
    assertThat(profile.getSnsLinks()).hasSize(1);
    assertThat(profile.getPartnerLinks()).hasSize(1);
  }

  @Test
  void givenFieldsReplaceEveryValue() {
    var profile = profile();
    var sns = new SnsLink("x", "https://x.example.test/b", "X");
    var partner = new PartnerLink("新しい提携先", "https://partner.example.test/b", null);

    profile.apply(
        new StoreProfilePatch(
            "modern",
            "https://example.test/new-logo.png",
            "https://example.test/banner.png",
            "https://example.test/mv.mp4",
            "video",
            "新しい紹介文",
            "キャッチコピー",
            "東京都",
            "03-0000-0000",
            "10:00-20:00",
            "料金の説明",
            Map.of("greeting", "ようこそ"),
            List.of(sns),
            List.of(partner)));

    assertThat(profile)
        .returns("modern", StoreProfile::getTemplateKey)
        .returns("https://example.test/new-logo.png", StoreProfile::getLogoUrl)
        .returns("https://example.test/banner.png", StoreProfile::getBannerUrl)
        .returns("https://example.test/mv.mp4", StoreProfile::getMvUrl)
        .returns("video", StoreProfile::getMvType)
        .returns("新しい紹介文", StoreProfile::getDescription)
        .returns("キャッチコピー", StoreProfile::getCatchCopy)
        .returns("東京都", StoreProfile::getAddress)
        .returns("03-0000-0000", StoreProfile::getPhone)
        .returns("10:00-20:00", StoreProfile::getBusinessHours)
        .returns("料金の説明", StoreProfile::getPricingDescription);
    assertThat(profile.getCustomTexts()).containsOnly(Map.entry("greeting", "ようこそ"));
    assertThat(profile.getSnsLinks()).containsExactly(sns);
    assertThat(profile.getPartnerLinks()).containsExactly(partner);
  }

  @Test
  void emptyCollectionsClearAndTheOldCollectionsAreLeftUntouched() {
    var profile = profile();
    var texts = profile.getCustomTexts();
    var sns = profile.getSnsLinks();
    var partners = profile.getPartnerLinks();

    profile.apply(
        new StoreProfilePatch(
            null, null, null, null, null, null, null, null, null, null, null, Map.of(), List.of(),
            List.of()));

    assertThat(profile.getCustomTexts()).isEmpty();
    assertThat(profile.getSnsLinks()).isEmpty();
    assertThat(profile.getPartnerLinks()).isEmpty();
    assertThat(texts).containsOnly(Map.entry("greeting", "こんにちは"));
    assertThat(sns).hasSize(1);
    assertThat(partners).hasSize(1);
  }

  private static StoreProfile profile() {
    var profile = StoreProfile.createDefault(7L);
    profile.setLogoUrl("https://example.test/logo.png");
    profile.setDescription("元の紹介文");
    profile.setCustomTexts(new HashMap<>(Map.of("greeting", "こんにちは")));
    profile.setSnsLinks(
        new ArrayList<>(List.of(new SnsLink("x", "https://x.example.test/a", null))));
    profile.setPartnerLinks(
        new ArrayList<>(List.of(new PartnerLink("提携先", "https://partner.example.test/a", null))));
    return profile;
  }
}
