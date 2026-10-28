package com.kizuna.cast.domain;

/** 在籍の物理削除で DB の連鎖により消える行の件数。内容は持たない。 */
public record CastCascadeCounts(long statusHistories, long snapshots, long invitations) {}
