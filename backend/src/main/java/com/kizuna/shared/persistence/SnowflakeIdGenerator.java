package com.kizuna.shared.persistence;

import java.io.Serializable;
import java.time.Instant;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * 64bit の snowflake ID を 10 進文字列として払い出す {@link IdentifierGenerator}。 クラスタ間の調整は持たないため、多重起動する場合は
 * workerId を重複させないこと。
 */
public class SnowflakeIdGenerator implements IdentifierGenerator {

  // 起点は 2020-01-01
  private static final long EPOCH = 1577836800000L;
  private static final long WORKER_ID_BITS = 10L;
  private static final long SEQUENCE_BITS = 12L;

  private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
  private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

  private final long workerId;
  private long lastTimestamp = -1L;
  private long sequence = 0L;

  public SnowflakeIdGenerator() {
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
      // 時計の巻き戻り。前回時刻まで待って ID の重複を防ぐ
      timestamp = waitUntil(lastTimestamp);
    }
    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) & SEQUENCE_MASK;
      if (sequence == 0) {
        // 同一ミリ秒内で連番が尽きたため、次のミリ秒まで待つ
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
