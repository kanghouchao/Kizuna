package com.kizuna.order.api.store;

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
public class StoreMonthlyPdfController {
  private final MonthlyPdfService pdf;
  private final MonthlyPdfSnapshotService snapshots;

  @GetMapping("/store/monthly-remunerations/pdf")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<byte[]> pdf(
      @RequestParam(name = "person_id") Long personId, @RequestParam String month)
      throws IOException {
    return MonthlyPdfResponse.create(
        month, pdf.generate(budget -> snapshots.store(personId, month, budget)));
  }
}
