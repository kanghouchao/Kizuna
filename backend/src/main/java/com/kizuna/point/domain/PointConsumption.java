package com.kizuna.point.domain;

/** 加算ロットごとの消費済み量の読み側 projection。 */
public interface PointConsumption {

  Long getSourceEntryId();

  Long getConsumed();
}
