package com.kizuna.audit.recording;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditWriter {
  private final AuditEventRepository events;
  private final Clock clock;

  // 成功監査を業務更新と同時に確定し、監査だけが遅延・欠落する窓を作らない。
  @Transactional(propagation = Propagation.MANDATORY)
  public Long append(AuditChange change) {
    return events.saveAndFlush(AuditEvent.record(change, OffsetDateTime.now(clock))).getId();
  }
}
