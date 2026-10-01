package com.kizuna.order.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class MonthlyPdfResponse {
  private MonthlyPdfResponse() {}

  public static ResponseEntity<byte[]> create(String month, byte[] bytes) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"monthly-remuneration-" + month + ".pdf\"")
        .body(bytes);
  }
}
