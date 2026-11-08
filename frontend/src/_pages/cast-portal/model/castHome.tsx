'use client';

import { createContext, useContext } from 'react';
import { usePathname } from 'next/navigation';
import { ownServiceApi } from '@/entities/service';
import { shiftApi, type CastShiftRequestItem, type CastStoreItem } from '@/entities/shift';
import type { PageResult } from '@/shared/api';
import { tokyoDate, useResource } from '@/shared/lib';
import { remunerationApi, type Statement } from '@/widgets/remuneration-statement';
import {
  acceptanceTodos,
  consentTodos,
  requestResultTodos,
  resultSince,
  type TodoItem,
} from '../lib/todo';

async function allRows<T>(fetchPage: (page: number) => Promise<PageResult<T>>): Promise<T[]> {
  const first = await fetchPage(0);
  const rows = [...first.rows];
  for (let page = 1; page < first.pageCount; page++) rows.push(...(await fetchPage(page)).rows);
  return rows;
}

async function pendingConsents(stores: CastStoreItem[]): Promise<TodoItem[]> {
  const perStore = await Promise.all(
    stores.map(async store => ({
      storeName: store.store_name ?? '',
      conditions: await allRows(page => ownServiceApi.list(String(store.store_id), page)),
    }))
  );
  return consentTodos(perStore);
}

async function recentRequests(since: Date) {
  const rows: CastShiftRequestItem[] = [];
  for (let page = 0; ; page++) {
    const result = await shiftApi.myShiftRequests(page);
    rows.push(...result.rows);
    const last = result.rows.at(-1)?.created_at;
    if (page + 1 >= result.pageCount || !last || new Date(last) < since) return rows;
  }
}

// 承諾待ちの日と今月の累計は同じ当月の明細から読む。前月以前の承諾待ちは月次の画面で扱う。
function monthStatements(stores: CastStoreItem[], month: string): Promise<Statement[]> {
  return Promise.all(
    stores.map(store =>
      remunerationApi.statement({ scope: 'self', storeId: store.store_id ?? 0 }, month, 0, 0)
    )
  );
}

interface Region<T> {
  /** 取得できていない間（読み込み中・失敗）は null。 */
  data: T | null;
  failed: boolean;
  retry: () => void;
}

export interface MonthTotal {
  storeId: number;
  storeName: string;
  /** 日額保証の未確定な日があると計算できず undefined。 */
  total?: number;
}

export interface CastHome {
  todos: Region<TodoItem[]>;
  monthTotals: Region<MonthTotal[]>;
  /** ホームのタブの件数。処理しない限り残る受諾待ち・承諾待ちだけを数える（既読は持たない）。 */
  pendingCount: number;
}

type Source = Pick<ReturnType<typeof useResource>, 'failure' | 'reload'>;

function region<T>(data: T | null, sources: Source[]): Region<T> {
  const failed = sources.some(source => source.failure !== null);
  return {
    data: failed ? null : data,
    failed,
    retry: () => {
      for (const source of sources) if (source.failure !== null) void source.reload();
    },
  };
}

const CastHomeContext = createContext<CastHome | null>(null);

/**
 * ホームの区画とタブの件数の取得元。外枠はリンク遷移で作り直されないので、
 * 画面を移るたびに取り直す — 下位画面で受諾・承諾した結果を戻った先の件数に映すため。
 */
export function useCastHomeSource(): CastHome {
  const pathname = usePathname();
  const now = new Date();
  const today = tokyoDate(now);
  const since = resultSince(now);
  const month = today.slice(0, 7);
  const stores = useResource(() => shiftApi.myStores(), [pathname]);
  const storeList = stores.data;
  const consents = useResource(storeList ? () => pendingConsents(storeList) : null, [storeList]);
  const statements = useResource(storeList ? () => monthStatements(storeList, month) : null, [
    storeList,
    month,
  ]);
  const requests = useResource(() => recentRequests(since), [pathname]);

  const acceptances = statements.data ? acceptanceTodos(statements.data, today) : null;
  const todos =
    consents.data && acceptances && requests.data
      ? [...consents.data, ...acceptances, ...requestResultTodos(requests.data, since, today)]
      : null;
  return {
    todos: region(todos, [stores, consents, statements, requests]),
    monthTotals: region(
      statements.data?.map(s => ({
        storeId: s.store_id,
        storeName: s.store_name,
        total: s.total,
      })) ?? null,
      [stores, statements]
    ),
    pendingCount: (consents.data?.length ?? 0) + (acceptances?.length ?? 0),
  };
}

export const CastHomeProvider = CastHomeContext.Provider;

export function useCastHome(): CastHome {
  const home = useContext(CastHomeContext);
  if (home === null) throw new Error('useCastHome は CastPortalLayout の中で使う');
  return home;
}
