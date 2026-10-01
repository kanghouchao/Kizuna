package com.kizuna.order.api.platform;

import com.kizuna.order.api.MonthlyPdfResponse;
import com.kizuna.order.application.MonthlyPdfService;
import com.kizuna.order.application.MonthlyPdfSnapshotService;
import java.io.IOException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SelfMonthlyPdfController {
  private final MonthlyPdfService pdf;
  private final MonthlyPdfSnapshotService snapshots;

  @GetMapping("/platform/me/monthly-remunerations/pdf")
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<byte[]> pdf(
      Principal principal,
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam String month)
      throws IOException {
    return MonthlyPdfResponse.create(
        month, pdf.generate(budget -> snapshots.self(principal.getName(), storeId, month, budget)));
  }
}
