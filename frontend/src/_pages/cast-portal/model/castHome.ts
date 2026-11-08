'use client';

import { createContext, useContext } from 'react';
import { usePathname } from 'next/navigation';
import { selfMonthlyRemunerationApi } from '@/entities/order';
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

type Store = Required<CastStoreItem>;

async function myStores(): Promise<Store[]> {
  return (await shiftApi.myStores()).filter(
    (store): store is Store => store.store_id !== undefined && store.store_name !== undefined
  );
}

/** 最後のページまで、または読み進める必要が無くなるまでページを読む。 */
async function collectPages<T>(
  fetchPage: (page: number) => Promise<PageResult<T>>,
  needsMore: (pageRows: T[]) => boolean = () => true
): Promise<T[]> {
  const rows: T[] = [];
  for (let page = 0; ; page++) {
    const result = await fetchPage(page);
    rows.push(...result.rows);
    if (page + 1 >= result.pageCount || !needsMore(result.rows)) return rows;
  }
}

async function pendingConsents(stores: Store[]): Promise<TodoItem[]> {
  const perStore = await Promise.all(
    stores.map(async store => ({
      storeName: store.store_name,
      conditions: await collectPages(page => ownServiceApi.list(String(store.store_id), page)),
    }))
  );
  return consentTodos(perStore);
}

function recentRequests(since: Date): Promise<CastShiftRequestItem[]> {
  return collectPages(shiftApi.myShiftRequests, pageRows => {
    const last = pageRows.at(-1)?.created_at;
    return last !== undefined && new Date(last) >= since;
  });
}

/** 'yyyy-MM' の前の月。 */
function previousMonth(month: string): string {
  const [year, m] = month.split('-').map(Number);
  return m === 1 ? `${year - 1}-12` : `${year}-${String(m - 1).padStart(2, '0')}`;
}

function statementsOf(storeIds: number[], month: string): Promise<Statement[]> {
  return Promise.all(
    storeIds.map(storeId => remunerationApi.statement({ scope: 'self', storeId }, month, 0, 0))
  );
}

/**
 * 承諾待ちの日は先月と今月の明細から拾う（月初に前月末の日が消えないように）。それより前の月は
 * 月次の画面で扱う。退店後も明細は照会・承諾できるので、在籍中に限らず在籍したことのある店舗を読む。
 */
async function recentStatements(month: string) {
  const stores = await collectPages(page => selfMonthlyRemunerationApi.stores(page));
  const storeIds = stores.map(store => store.store_id);
  const [previous, current] = await Promise.all([
    statementsOf(storeIds, previousMonth(month)),
    statementsOf(storeIds, month),
  ]);
  return { previous, current };
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
  const stores = useResource(myStores, [pathname]);
  const storeList = stores.data;
  const consents = useResource(storeList ? () => pendingConsents(storeList) : null, [storeList]);
  const statements = useResource(() => recentStatements(month), [pathname, month]);
  const requests = useResource(() => recentRequests(since), [pathname]);

  const acceptances = statements.data
    ? acceptanceTodos([...statements.data.previous, ...statements.data.current], today)
    : null;
  const todos =
    consents.data && acceptances && requests.data
      ? [...consents.data, ...acceptances, ...requestResultTodos(requests.data, since, today)]
      : null;
  return {
    todos: region(todos, [stores, consents, statements, requests]),
    monthTotals: region(
      storeList && statements.data
        ? statements.data.current
            // 退店した店舗は今月の動きがあるときだけ出す。明細は動きが無くても全日分あるので合計で見る
            // （動きが無い日は ¥0 で確定し、未確定の undefined は出勤があった印）。
            .filter(s => storeList.some(store => store.store_id === s.store_id) || s.total !== 0)
            .map(s => ({ storeId: s.store_id, storeName: s.store_name, total: s.total }))
        : null,
      [stores, statements]
    ),
    // 片方だけで数えると欠けた件数を正しい件数のように見せるので、両方そろうまで出さない。
    pendingCount: consents.data && acceptances ? consents.data.length + acceptances.length : 0,
  };
}

export const CastHomeProvider = CastHomeContext.Provider;

export function useCastHome(): CastHome {
  const home = useContext(CastHomeContext);
  if (home === null) throw new Error('useCastHome は CastPortalLayout の中で使う');
  return home;
}
