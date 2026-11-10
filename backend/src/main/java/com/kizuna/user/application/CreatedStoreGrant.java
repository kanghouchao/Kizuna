package com.kizuna.user.application;

import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 店舗の作成者を、その店舗の店舗集合へ作成と同じ取引で加える。新店舗が作成者に届く経路はここだけ。 店舗集合は要求ごとに DB から解決されるため、版は進めず次の要求から効く。 */
@Service
@RequiredArgsConstructor
@NamedInterface("created-store-grant")
@Transactional(propagation = Propagation.MANDATORY)
public class CreatedStoreGrant {

  private final UserRepository users;
  private final BusinessAudit businessAudit;

  public void grantToCurrent(Long storeId) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) throw missingCreator();
    // 同じ利用者の並行する作成や授権編集との取りこぼしを防ぐため、行を押さえてから読み直す。
    User creator =
        users.findByEmailForUpdate(auth.getName()).orElseThrow(CreatedStoreGrant::missingCreator);
    if (creator.getStoreScopeType() == StoreScopeType.ALL_STORES) {
      return;
    }
    var before = BusinessAudit.grants(creator);
    Set<Long> storeIds = new HashSet<>(creator.getStoreIds());
    storeIds.add(storeId);
    creator.reassignStores(StoreScopeType.SPECIFIC_STORES, storeIds);
    users.save(creator);
    businessAudit.recordCurrent(
        null,
        "STAFF_GRANTS_CHANGED",
        "USER_GRANTS",
        creator.getId().toString(),
        before,
        BusinessAudit.grants(creator));
  }

  private static StaleSessionException missingCreator() {
    return new StaleSessionException("店舗の作成者を確認できません");
  }
}
