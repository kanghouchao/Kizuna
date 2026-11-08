'use client';

import { useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { memberOrderApplicationApi } from '@/entities/order';
import { adminStoreApi } from '@/entities/store';
import { useResource } from '@/shared/lib';
import { Button, RegionError, Skeleton } from '@/shared/ui';
import { MemberReservationForm } from './MemberReservationForm';

interface PastStore {
  id: number;
  name: string;
}

function Loading() {
  return (
    <div role="status" aria-label="読み込み中" className="space-y-4">
      <Skeleton className="h-7 w-1/2" />
      {Array.from({ length: 4 }, (_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}

function Guidance() {
  return (
    <p className="text-muted-foreground">
      店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。
    </p>
  );
}

/** 公式サイトの予約ボタンから来たとき。引き継いだドメインをサーバ側の公開照会に突き合わせる。 */
function StoreFromDomain({ domain }: { domain: string }) {
  const { data, isLoading, failure, reload } = useResource(
    () => adminStoreApi.lookupByDomain(domain),
    [domain]
  );
  if (isLoading) return <Loading />;
  if (failure === 'error') {
    // 「そのドメインの店舗が無い」（404）と照会自体の失敗を区別する。後者は再試行で
    // 復帰しうるので、案内と同じ行き止まりに見せない。
    return <RegionError message="店舗情報を取得できませんでした" onRetry={() => void reload()} />;
  }
  if (!data?.id) return <Guidance />;
  return (
    <MemberReservationForm key={data.id} storeId={Number(data.id)} storeName={data.name ?? ''} />
  );
}

/** 過去の予約申請に現れた店舗を、新しい申請の順に重複なく集める。 */
async function loadPastStores(): Promise<PastStore[]> {
  const stores = new Map<number, string>();
  for (let page = 0, pageCount = 1; page < pageCount; page++) {
    const result = await memberOrderApplicationApi.list(page);
    pageCount = result.pageCount;
    for (const row of result.rows) {
      if (row.store_id != null && !stores.has(row.store_id)) {
        stores.set(row.store_id, row.store_name ?? '');
      }
    }
  }
  return [...stores].map(([id, name]) => ({ id, name }));
}

/**
 * 一覧の「新しく予約」から来たとき。会員は現在店舗を持たないので、過去に予約した店舗から選ばせる。
 * 店舗の ID は本人の予約申請の応答から取り、URL では受け取らない。
 */
function StoreFromHistory() {
  const { data, isLoading, failure, reload } = useResource(loadPastStores, []);
  const [pickedId, setPickedId] = useState<number | null>(null);

  if (isLoading) return <Loading />;
  if (failure !== null) {
    return (
      <RegionError message="予約した店舗を取得できませんでした" onRetry={() => void reload()} />
    );
  }
  const stores = data ?? [];
  if (stores.length === 0) return <Guidance />;
  if (stores.length === 1) {
    return <MemberReservationForm storeId={stores[0].id} storeName={stores[0].name} />;
  }

  const picked = stores.find(store => store.id === pickedId);
  if (picked) {
    return (
      <MemberReservationForm
        key={picked.id}
        storeId={picked.id}
        storeName={picked.name}
        onChangeStore={() => setPickedId(null)}
      />
    );
  }
  return (
    <section aria-labelledby="pick-store" className="space-y-4">
      <h2 id="pick-store" className="text-xl font-semibold">
        予約する店舗
      </h2>
      <p className="text-muted-foreground">
        これまでに予約した店舗から選んでください。初めての店舗は店舗公式サイトの予約ボタンからお進みください。
      </p>
      <div className="grid gap-3">
        {stores.map(store => (
          <Button
            key={store.id}
            type="button"
            variant="outline"
            className="h-auto min-h-12 justify-start py-3 text-left text-lg whitespace-normal wrap-anywhere"
            onClick={() => setPickedId(store.id)}
          >
            {store.name}
          </Button>
        ))}
      </div>
    </section>
  );
}

/**
 * 会員ポータルの予約申請。宛先の店舗は公式サイトから引き継いだドメイン（?store=）か、本人の過去の
 * 予約から決める。どちらもブラウザから渡された値を店舗 ID として信用せず、サーバの応答だけを使う。
 */
export function MemberReservationNewPage() {
  const domain = useSearchParams().get('store');
  return domain ? <StoreFromDomain domain={domain} /> : <StoreFromHistory />;
}
