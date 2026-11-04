package com.kizuna.remuneration.application;

import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.remuneration.application.RemunerationStatement.AcceptanceSummary;
import com.kizuna.remuneration.domain.NotAcceptableReason;
import com.kizuna.remuneration.domain.StatementAcceptance;
import com.kizuna.remuneration.domain.StatementAcceptanceRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.Timestamps;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 明細の承諾（ADR 0041）。書けるのは本人だけで、店舗側の書き口は無い。 */
@Service
@RequiredArgsConstructor
public class StatementAcceptanceService {
  private final StatementContents contents;
  private final StatementAcceptanceRepository acceptances;
  private final RemunerationPersonLookup people;
  private final ActorIdentityService actors;
  private final BusinessDateService businessDates;
  private final BusinessAudit audit;

  private static final String CHANGED = "明細の内容が変わっています。最新の明細を確認してください";

  public record Acceptance(AcceptanceSummary summary, boolean created) {}

  /** 承諾不可を要約の照合より先に判定する — 内容が定まらない日に写しを比べない。 */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Acceptance accept(String actor, AcceptanceCommand command) {
    Long store = command.storeId(), person = self(actor, store);
    var day = command.businessDate();
    var content = contents.between(store, person, day, day).getFirst().content();
    var reason = content.notAcceptableReason(businessDates.hasEnded(day));
    if (reason != null) throw new ServiceException(message(reason));
    var digest = content.digest();
    if (!digest.equals(command.contentDigest())) throw new ConflictException(CHANGED);
    var existing =
        acceptances.findByStoreIdAndPersonIdAndBusinessDateAndContentDigest(
            store, person, day, digest);
    if (existing.isPresent()) return new Acceptance(AcceptanceSummary.of(existing.get()), false);
    var row = StatementAcceptance.accept(store, person, day, content, Timestamps.now());
    acceptances.saveAndFlush(row);
    audit.record(
        actor,
        store,
        "STATEMENT_ACCEPTED",
        "STATEMENT_ACCEPTANCE",
        row.getId(),
        null,
        null,
        Map.of(),
        Map.of(
            "order_amount", String.valueOf(row.getOrderAmount()),
            "guarantee_amount", String.valueOf(row.getGuaranteeAmount()),
            "bonus_amount", String.valueOf(row.getBonusAmount()),
            "total", String.valueOf(row.total())));
    return new Acceptance(AcceptanceSummary.of(row), true);
  }

  /** 同じ内容の並行承諾で一意制約に敗れた要求へ、先に確定した行を返す。 */
  @Transactional(readOnly = true)
  public Acceptance existing(String actor, AcceptanceCommand command) {
    Long store = command.storeId(), person = self(actor, store);
    return acceptances
        .findByStoreIdAndPersonIdAndBusinessDateAndContentDigest(
            store, person, command.businessDate(), command.contentDigest())
        .map(row -> new Acceptance(AcceptanceSummary.of(row), false))
        .orElseThrow(() -> new ConflictException(CHANGED));
  }

  private Long self(String actor, Long store) {
    RemunerationInput.store(store);
    var person = people.self(actors.requireUserId(actor));
    people.require(store, person);
    return person;
  }

  private static String message(NotAcceptableReason reason) {
    return switch (reason) {
      case BUSINESS_DAY_NOT_ENDED -> "営業日が終了していない日は承諾できません";
      case GUARANTEE_UNDETERMINED -> "退勤記録待ち・日額未設定の日は承諾できません";
      case ALL_ZERO -> "報酬の無い日は承諾できません";
    };
  }
}
