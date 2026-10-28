package com.kizuna.order.application;

import com.kizuna.order.domain.ContactSnapshot;

/** 受付時の連絡先の入力。 */
public record ContactSnapshotInput(String name, String phoneNumber, String email, String lineId) {

  public ContactSnapshot normalized() {
    return ContactSnapshot.normalize(name, phoneNumber, email, lineId);
  }
}
