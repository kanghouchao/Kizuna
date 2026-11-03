package com.kizuna.advertising.domain;

import com.kizuna.receipt.once.Fingerprint;
import java.util.UUID;

/** 広告費の書き込み命令。指紋は受領済みの行と照合が続くので、項目の並びと区切りを変えない。 */
public sealed interface AdvertisingCommand {
  UUID requestId();

  String fingerprint();

  record Create(
      String month,
      AdvertisingCategory category,
      String mediumId,
      String agencyName,
      String planName,
      Integer inquiryCount,
      Integer amount,
      UUID requestId)
      implements AdvertisingCommand {
    public AdvertisingValues values() {
      return new AdvertisingValues(category, mediumId, agencyName, planName, inquiryCount, amount);
    }

    public String fingerprint() {
      return Fingerprint.of(
          "CREATE", month, category, mediumId, agencyName, planName, inquiryCount, amount);
    }
  }

  record Replace(
      String id,
      AdvertisingCategory category,
      String mediumId,
      String agencyName,
      String planName,
      Integer inquiryCount,
      Integer amount,
      Long version,
      String reason,
      UUID requestId)
      implements AdvertisingCommand {
    public AdvertisingValues values() {
      return new AdvertisingValues(category, mediumId, agencyName, planName, inquiryCount, amount);
    }

    public String fingerprint() {
      return Fingerprint.of(
          "REPLACE",
          id,
          version,
          category,
          mediumId,
          agencyName,
          planName,
          inquiryCount,
          amount,
          reason);
    }
  }

  record Delete(String id, Long version, String reason, UUID requestId)
      implements AdvertisingCommand {
    public String fingerprint() {
      return Fingerprint.of("DELETE", id, version, reason);
    }
  }

  record Copy(
      String targetMonth, Long sourceVersion, Long targetVersion, String reason, UUID requestId)
      implements AdvertisingCommand {
    public String fingerprint() {
      return Fingerprint.of("COPY", targetMonth, sourceVersion, targetVersion, reason);
    }
  }
}
