package com.kizuna.reporting.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ReportDownload {
  private ReportDownload() {}

  public static ResponseEntity<byte[]> response(String format, byte[] bytes) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaType.parseMediaType(
                format.equals("csv")
                    ? "text/csv;charset=UTF-8"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"operational-report." + format + "\"")
        .body(bytes);
  }
}
