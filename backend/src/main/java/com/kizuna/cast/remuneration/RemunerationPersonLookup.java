package com.kizuna.cast.remuneration;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemunerationPersonLookup {
  private final EntityManager em;
  private final CastRepository people;

  public record Person(Long id, String name, String storeName) {}

  public Long self(Long userId) {
    return people
        .findByPlatformUserId(userId)
        .orElseThrow(RemunerationPersonLookup::missing)
        .getId();
  }

  public Person require(Long storeId, Long personId) {
    return em.createQuery(
            """
     select new com.kizuna.cast.remuneration.RemunerationPersonLookup$Person(e.castId, p.name, s.name)
     from com.kizuna.cast.domain.CastEnrollment e
     join com.kizuna.cast.domain.CastProfile p on p.enrollmentId = e.id and p.storeId = e.storeId
     join com.kizuna.store.domain.Store s on s.id = e.storeId
     where e.storeId = :store and e.castId = :person
     order by e.createdAt desc, e.id desc
     """,
            Person.class)
        .setParameter("store", storeId)
        .setParameter("person", personId)
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElseThrow(RemunerationPersonLookup::missing);
  }

  private static NotFoundException missing() {
    return new NotFoundException("報酬明細のキャスト本人が見つかりません");
  }
}
