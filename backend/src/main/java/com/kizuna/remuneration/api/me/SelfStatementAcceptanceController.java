package com.kizuna.remuneration.api.me;

import com.kizuna.remuneration.api.dto.StatementAcceptanceRequest;
import com.kizuna.remuneration.api.dto.StatementAcceptanceResponse;
import com.kizuna.remuneration.application.StatementAcceptanceService;
import com.kizuna.remuneration.application.StatementAcceptanceService.Acceptance;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.web.Replays;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/statement-acceptances")
@RequiredArgsConstructor
public class SelfStatementAcceptanceController {
  private final StatementAcceptanceService service;

  /** 新しい行は 201、同じ内容の行が既にあれば（再送・並行）その行を 200 で返す。 */
  @PostMapping
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<StatementAcceptanceResponse> accept(
      Principal principal, @Valid @RequestBody StatementAcceptanceRequest request) {
    var command = request.command();
    Acceptance result =
        Replays.write(
            DbConstraint.UQ_T_STATEMENT_ACCEPTANCES_CONTENT,
            () -> service.accept(principal.getName(), command),
            () -> service.existing(principal.getName(), command));
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(StatementAcceptanceResponse.of(result.summary()));
  }
}
