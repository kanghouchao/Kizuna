package com.kizuna.storeprofile.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfilePatch;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.ArrayList;
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
  private final BusinessAudit businessAudit;

  @Transactional(readOnly = true)
  public StoreProfile get(Long storeId) {
    return storeProfileRepository
        .findByStoreId(storeId)
        .orElseThrow(() -> new NotFoundException("店舗設定が見つかりません"));
  }

  @Transactional
  public StoreProfile update(Long storeId, StoreProfilePatch patch) {
    StoreProfile config =
        storeProfileRepository
            .findByStoreId(storeId)
            .orElseThrow(() -> new NotFoundException("店舗設定が見つかりません"));
    var before = snapshot(config);
    Long versionBefore = config.getVersion();
    config.apply(patch);
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
    return saved;
  }

  /** 値は変更の有無を判定するためだけに持ち、監査には項目名しか渡さない。 */
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
    values.put("custom_texts", profile.getCustomTexts());
    values.put("sns_links", profile.getSnsLinks());
    values.put("partner_links", profile.getPartnerLinks());
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
