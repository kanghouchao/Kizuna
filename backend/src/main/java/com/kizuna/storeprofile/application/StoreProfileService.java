package com.kizuna.storeprofile.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.storeprofile.api.dto.StoreProfileMapper;
import com.kizuna.storeprofile.api.dto.StoreProfileResponse;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;
import com.kizuna.storeprofile.domain.PartnerLink;
import com.kizuna.storeprofile.domain.SnsLink;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreProfileService {

  private final StoreProfileRepository storeProfileRepository;
  private final StoreProfileMapper storeProfileMapper;
  private final BusinessAudit businessAudit;

  @Transactional(readOnly = true)
  public StoreProfileResponse get(Long storeId) {
    StoreProfile config =
        storeProfileRepository
            .findByStoreId(storeId)
            .orElseThrow(() -> new NotFoundException("店舗設定が見つかりません"));
    return storeProfileMapper.toResponse(config);
  }

  @Transactional
  public StoreProfileResponse update(Long storeId, StoreProfileUpdateRequest request) {
    StoreProfile config =
        storeProfileRepository
            .findByStoreId(storeId)
            .orElseThrow(() -> new NotFoundException("店舗設定が見つかりません"));
    var before = snapshot(config);
    Long versionBefore = config.getVersion();
    storeProfileMapper.updateEntityFromRequest(request, config);
    var saved = storeProfileRepository.saveAndFlush(config);
    var changed = changedFields(before, snapshot(saved));
    if (!changed.isEmpty()) {
      businessAudit.recordCurrent(
          storeId,
          "STORE_PROFILE_UPDATED",
          "STORE_PROFILE",
          String.valueOf(storeId),
          Map.of("version", String.valueOf(versionBefore)),
          Map.of(
              "version", String.valueOf(saved.getVersion()),
              "changed_fields", String.join(",", changed)));
    }
    return storeProfileMapper.toResponse(saved);
  }

  /** 値は変更の有無を判定するためだけに持ち、監査には項目名しか渡さない。入れ子の値はマッパーの更新で書き換わるので複製する。 */
  private static Map<String, Object> snapshot(StoreProfile profile) {
    var values = new LinkedHashMap<String, Object>();
    values.put("template_key", profile.getTemplateKey());
    values.put("logo_url", profile.getLogoUrl());
    values.put("banner_url", profile.getBannerUrl());
    values.put("mv_url", profile.getMvUrl());
    values.put("mv_type", profile.getMvType());
    values.put("description", profile.getDescription());
    values.put("catch_copy", profile.getCatchCopy());
    values.put("address", profile.getAddress());
    values.put("phone", profile.getPhone());
    values.put("business_hours", profile.getBusinessHours());
    values.put("pricing_description", profile.getPricingDescription());
    values.put(
        "custom_texts",
        profile.getCustomTexts() == null ? null : new HashMap<>(profile.getCustomTexts()));
    values.put(
        "sns_links",
        profile.getSnsLinks() == null
            ? null
            : profile.getSnsLinks().stream()
                .map(l -> new SnsLink(l.getPlatform(), l.getUrl(), l.getLabel()))
                .toList());
    values.put(
        "partner_links",
        profile.getPartnerLinks() == null
            ? null
            : profile.getPartnerLinks().stream()
                .map(l -> new PartnerLink(l.getName(), l.getUrl(), l.getLogoUrl()))
                .toList());
    return values;
  }

  private static List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
    var changed = new ArrayList<String>();
    after.forEach(
        (name, value) -> {
          if (!Objects.equals(before.get(name), value)) {
            changed.add(name);
          }
        });
    return changed;
  }
}
