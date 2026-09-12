import type { Order, OrderArchiveRow, OrderWorkQueueRow } from '@/entities/order';

type AssertFalse<T extends false> = T;

// 一覧の応答を詳細として使う誤りは、実行前に型検査で拒否する。
export type WorkQueueIsNotDetail = AssertFalse<OrderWorkQueueRow extends Order ? true : false>;
export type ArchiveIsNotDetail = AssertFalse<OrderArchiveRow extends Order ? true : false>;
