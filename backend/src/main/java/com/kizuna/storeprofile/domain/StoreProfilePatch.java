package com.kizuna.storeprofile.domain;

import java.util.List;
import java.util.Map;

/** 店舗設定の部分更新。null の項目は「変更しない」、空の集合は「空にする」を意味する。 */
public record StoreProfilePatch(
    String templateKey,
    String logoUrl,
    String bannerUrl,
    String mvUrl,
    String mvType,
    String description,
    String catchCopy,
    String address,
    String phone,
    String businessHours,
    String pricingDescription,
    Map<String, String> customTexts,
    List<SnsLink> snsLinks,
    List<PartnerLink> partnerLinks) {}
