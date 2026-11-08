'use client';

import { createContext, useContext } from 'react';
import { usePathname } from 'next/navigation';
import { selfMonthlyRemunerationApi } from '@/entities/order';
import { ownServiceApi } from '@/entities/service';
import { shiftApi, type CastShiftRequestItem, type CastStoreItem } from '@/entities/shift';
import { shiftMonth, tokyoDate, useResource } from '@/shared/lib';
import { remunerationApi, type Statement } from '@/widgets/remuneration-statement';
import { collectPages } from '../lib/collectPages';
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

function statementsOf(storeIds: number[], month: string): Promise<Statement[]> {
  return Promise.all(
    storeIds.map(storeId => remunerationApi.statement({ scope: 'self', storeId }, month, 0, 0))
  );
}

// 退店後も明細は照会・承諾できるので、在籍中に限らず在籍したことのある店舗を読む。
async function remunerationStoreIds(): Promise<number[]> {
  const stores = await collectPages(page => selfMonthlyRemunerationApi.stores(page));
  return stores.map(store => store.store_id);
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
  const remunerationStores = useResource(remunerationStoreIds, [pathname]);
  const storeIds = remunerationStores.data;
  // 承諾待ちの日は先月と今月の明細から拾う（月初に前月末の日が消えないように）。それより前の月は
  // 月次の画面で扱う。今月の累計は今月の明細だけを使うので、先月の失敗に巻き込まないよう別に取る。
  const current = useResource(storeIds ? () => statementsOf(storeIds, month) : null, [
    storeIds,
    month,
  ]);
  const previous = useResource(
    storeIds ? () => statementsOf(storeIds, shiftMonth(month, -1)) : null,
    [storeIds, month]
  );
  const requests = useResource(() => recentRequests(since), [pathname]);

  const acceptances =
    previous.data && current.data
      ? acceptanceTodos([...previous.data, ...current.data], today)
      : null;
  const todos =
    consents.data && acceptances && requests.data
      ? [...consents.data, ...acceptances, ...requestResultTodos(requests.data, since, today)]
      : null;
  return {
    todos: region(todos, [stores, consents, remunerationStores, previous, current, requests]),
    monthTotals: region(
      storeList && current.data
        ? current.data
            // 退店した店舗は今月の動きがあるときだけ出す。明細は動きが無くても全日分あるので合計で見る
            // （動きが無い日は ¥0 で確定し、未確定の undefined は出勤があった印）。
            .filter(s => storeList.some(store => store.store_id === s.store_id) || s.total !== 0)
            .map(s => ({ storeId: s.store_id, storeName: s.store_name, total: s.total }))
        : null,
      [stores, remunerationStores, current]
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
