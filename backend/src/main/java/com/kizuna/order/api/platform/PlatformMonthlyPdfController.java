package com.kizuna.order.api.platform;

import com.kizuna.order.api.MonthlyPdfResponse;
import com.kizuna.order.application.MonthlyPdfService;
import com.kizuna.order.application.MonthlyPdfSnapshotService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlatformMonthlyPdfController {
  private final MonthlyPdfService pdf;
  private final MonthlyPdfSnapshotService snapshots;

  @GetMapping("/platform/monthly-remunerations/pdf")
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE')")
  public ResponseEntity<byte[]> pdf(
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month)
      throws IOException {
    return MonthlyPdfResponse.create(
        month, pdf.generate(budget -> snapshots.platform(storeId, personId, month, budget)));
  }
}
