package com.kizuna.order.api.dto;

import com.kizuna.order.domain.ContactSnapshot;
import jakarta.validation.constraints.Size;

public record ContactSnapshotRequest(
    @Size(max = 255) String name,
    @Size(max = 50) String phoneNumber,
    @Size(max = 254) String email,
    @Size(max = 255) String lineId) {
  public ContactSnapshot normalized() {
    return ContactSnapshot.normalize(name, phoneNumber, email, lineId);
  }

  public ContactSnapshot original() {
    return new ContactSnapshot(name, phoneNumber, email, lineId);
  }
}
