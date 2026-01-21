package com.kizuna.model.entity.tenant;

import java.io.Serializable;
import java.time.Instant;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * Simple snowflake ID generator that produces 64-bit numeric IDs encoded as decimal strings. This
 * implementation is deterministic and not cluster-coordinated; set workerId appropriately.
 */
public class SnowflakeIdGenerator implements IdentifierGenerator {

  // epoch: 2020-01-01
  private static final long EPOCH = 1577836800000L;
  private static final long WORKER_ID_BITS = 10L;
  private static final long SEQUENCE_BITS = 12L;

  private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
  private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

  private final long workerId;
  private long lastTimestamp = -1L;
  private long sequence = 0L;

  public SnowflakeIdGenerator() {
    // default worker id 1; consider injecting via property or environment
    this(1L);
  }

  public SnowflakeIdGenerator(long workerId) {
    if (workerId < 0 || workerId > MAX_WORKER_ID) {
      throw new IllegalArgumentException(
          String.format("workerId must be between 0 and %d", MAX_WORKER_ID));
    }
    this.workerId = workerId;
  }

  private synchronized long nextId() {
    long timestamp = Instant.now().toEpochMilli();
    if (timestamp < lastTimestamp) {
      // clock moved backwards, wait until lastTimestamp
      timestamp = waitUntil(lastTimestamp);
    }
    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) & SEQUENCE_MASK;
      if (sequence == 0) {
        // sequence overflow on the same millisecond
        timestamp = waitUntil(lastTimestamp + 1);
      }
    } else {
      sequence = 0L;
    }
    lastTimestamp = timestamp;

    long shiftedTimestamp = (timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS);
    long shiftedWorkerId = (workerId << SEQUENCE_BITS);
    return shiftedTimestamp | shiftedWorkerId | sequence;
  }

  private long waitUntil(long ts) {
    long now = Instant.now().toEpochMilli();
    while (now < ts) {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      now = Instant.now().toEpochMilli();
    }
    return now;
  }

  @Override
  public Serializable generate(SharedSessionContractImplementor session, Object object)
      throws HibernateException {
    return String.valueOf(nextId());
  }
}
