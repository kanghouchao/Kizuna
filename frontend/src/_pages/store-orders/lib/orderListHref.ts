import { storePath } from '@/shared/lib';

/**
 * 受注一覧の検索条件（query）を持った一覧の URL。作業画面は一覧の query をそのまま受け取って返す。
 * 行き先の path は固定で query だけを運ぶので、外部へ飛ぶ URL にはならない。条件の検証は一覧が読むときに行う。
 */
export function orderListHref(storeId: string, query: string): string {
  return withQuery(storePath(storeId, '/orders'), query);
}

/** 予約申請の確定画面の URL。戻り先のために一覧の query を付けて渡す。 */
export function applicationConfirmHref(storeId: string, id: string, listQuery: string): string {
  return withQuery(
    storePath(storeId, `/orders/applications/${encodeURIComponent(id)}/confirm`),
    listQuery
  );
}

/** 受注の完了画面の URL。戻り先のために一覧の query を付けて渡す。 */
export function orderCompletionHref(storeId: string, id: string, listQuery: string): string {
  return withQuery(storePath(storeId, `/orders/${encodeURIComponent(id)}/completion`), listQuery);
}

const withQuery = (path: string, query: string) => (query ? `${path}?${query}` : path);
