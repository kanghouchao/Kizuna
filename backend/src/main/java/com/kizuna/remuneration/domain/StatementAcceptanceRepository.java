package com.kizuna.remuneration.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface StatementAcceptanceRepository extends StoreScopedRepository<StatementAcceptance> {

  /** 行は内容が変わるたびにしか増えないので、一本人・一店舗・一月の範囲では頁を切らない。 */
  @Query(
      "select a from StatementAcceptance a where a.storeId = :storeId and a.personId = :personId"
          + " and a.businessDate between :start and :end order by a.businessDate, a.acceptedAt, a.id")
  List<StatementAcceptance> findInPeriod(
      Long storeId, Long personId, LocalDate start, LocalDate end);

  Optional<StatementAcceptance> findByStoreIdAndPersonIdAndBusinessDateAndContentDigest(
      Long storeId, Long personId, LocalDate businessDate, String contentDigest);
}
