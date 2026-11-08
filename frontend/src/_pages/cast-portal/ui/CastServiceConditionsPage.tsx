'use client';

import { useState } from 'react';
import {
  ownServiceApi,
  OwnServiceConditionSummary,
  ConsentDecision,
  ConsentStatus,
  serviceKindLabels,
} from '@/entities/service';
import { shiftApi } from '@/entities/shift';
import {
  formatMinutes,
  isForbidden,
  isNotFound,
  useListPage,
  useResource,
  useVersionedWrite,
} from '@/shared/lib';
import {
  BottomSheet,
  BottomSheetClose,
  Button,
  EmptyState,
  KeyValueList,
  KeyValueRow,
  ListRowsSkeleton,
  Money,
  Pager,
  RegionError,
  StatusBadge,
  StoreChips,
  type StatusWord,
} from '@/shared/ui';
import { PANEL } from './RemunerationDetail';

const STATUS_WORDS: Record<ConsentStatus, StatusWord> = {
  NOT_ACCEPTED: '受諾待ち',
  RECONFIRMATION_REQUIRED: '受諾待ち',
  ACCEPTED: '受諾済み',
  REJECTED: '拒否済み',
};

const DECISIONS: Record<ConsentDecision, { verb: string; title: string }> = {
  ACCEPTED: { verb: '受諾する', title: 'サービス条件を受諾する' },
  REJECTED: { verb: '拒否する', title: 'サービス条件を拒否する' },
};

/** 受諾・拒否は今の意思と違う方だけを出す。受諾待ちはどちらも選べる。 */
function decisionsFor(status: ConsentStatus): ConsentDecision[] {
  if (status === 'ACCEPTED') return ['REJECTED'];
  if (status === 'REJECTED') return ['ACCEPTED'];
  return ['ACCEPTED', 'REJECTED'];
}

function CustomerPrice({ item }: { item: OwnServiceConditionSummary }) {
  return item.charge_type === 'FREE' ? <>無料</> : <Money amount={item.price} />;
}

function summary(item: OwnServiceConditionSummary): string {
  return [
    serviceKindLabels[item.kind],
    item.duration_minutes !== undefined && formatMinutes(item.duration_minutes),
    item.charge_type && (item.charge_type === 'FREE' ? '無料' : '有料'),
  ]
    .filter(Boolean)
    .join(' · ');
}

/** 在籍店舗ごとのサービス条件。受諾・拒否は下部シートで同意内容を復唱してから送る。 */
export function CastServiceConditionsPage() {
  const [storesDenied, setStoresDenied] = useState(false);
  const stores = useResource(async () => {
    try {
      return await shiftApi.myStores();
    } catch (error) {
      setStoresDenied(isForbidden(error));
      throw error;
    }
  });
  const [picked, setPicked] = useState<string | null>(null);
  const chips = (stores.data ?? []).map(store => ({
    id: String(store.store_id),
    name: store.store_name ?? '',
  }));
  const current = chips.find(store => store.id === picked) ?? chips[0];

  if (stores.isLoading) return <ListRowsSkeleton />;
  if (storesDenied) return <p role="alert">サービス条件を確認する権限がありません。</p>;
  if (stores.failure)
    return (
      <RegionError message="所属店舗の取得に失敗しました" onRetry={() => void stores.reload()} />
    );
  if (!current) return <EmptyState message="在籍中の店舗がありません" />;
  return (
    <div className="space-y-4">
      <StoreChips stores={chips} value={current.id} onValueChange={setPicked} />
      <Conditions key={current.id} storeId={current.id} storeName={current.name} />
    </div>
  );
}

function Conditions({ storeId, storeName }: { storeId: string; storeName: string }) {
  const [message, setMessage] = useState('');
  const [sheetOpen, setSheetOpen] = useState(false);
  const [target, setTarget] = useState<{
    item: OwnServiceConditionSummary;
    decision: ConsentDecision;
  } | null>(null);
  const list = useListPage(page => ownServiceApi.list(storeId, page));
  const { busy, write, reload } = useVersionedWrite(() => list.reload());

  const open = (item: OwnServiceConditionSummary, decision: ConsentDecision) => {
    setTarget({ item, decision });
    setSheetOpen(true);
  };

  const save = async () => {
    if (!target || target.item.consent_version === undefined) return;
    const { item, decision } = target;
    setMessage('');
    const outcome = await write(
      () =>
        ownServiceApi.decide(storeId, item.id, {
          terms_version: item.terms_version,
          consent_version: item.consent_version!,
          decision,
        }),
      {
        success: '意思を保存しました',
        failure: '意思の保存に失敗しました。もう一度操作してください。',
      }
    );
    if (outcome === 'refused') return;
    setSheetOpen(false);
    if (outcome === 'success') await reload();
    else if (outcome === 'notFound')
      setMessage('サービスまたは在籍が変更されています。最新の一覧を確認してください。');
    else if (outcome === 'conflict') {
      setMessage('条件または意思が変更されています。最新の内容を確認してから操作してください。');
      await reload();
    }
  };

  const failure = list.failed ? list.error : null;
  return (
    <>
      {message && (
        <p role="alert" className="rounded-xl border p-3 text-foreground">
          {message}
        </p>
      )}
      {failure !== null && isForbidden(failure) ? (
        <p role="alert">サービス条件を確認する権限がありません。</p>
      ) : failure !== null && isNotFound(failure) ? (
        <p role="alert">有効な在籍またはサービスが見つかりません。店舗を選び直してください。</p>
      ) : list.failed ? (
        <RegionError message="サービス条件の取得に失敗しました" onRetry={() => void reload()} />
      ) : list.isLoading ? (
        <ListRowsSkeleton />
      ) : list.rows.length === 0 ? (
        <EmptyState message="サービス条件はありません" />
      ) : (
        <div className="space-y-3">
          {list.rows.map(item => (
            <ConditionCard
              key={item.id}
              item={item}
              storeName={storeName}
              disabled={busy}
              onDecide={decision => open(item, decision)}
            />
          ))}
        </div>
      )}
      <Pager
        page={list.page}
        pageCount={list.pageCount}
        total={list.total}
        rowCount={list.rows.length}
        disabled={list.isLoading || busy}
        onPageChange={page => void list.onPageChange(page)}
      />
      <BottomSheet
        open={sheetOpen}
        onOpenChange={next => {
          if (!next) setSheetOpen(false);
        }}
        title={target ? DECISIONS[target.decision].title : ''}
        footer={
          <>
            <Button type="button" onClick={() => void save()} disabled={busy}>
              {busy
                ? '保存中...'
                : target?.decision === 'REJECTED'
                  ? '確認して拒否する'
                  : '確認して受諾する'}
            </Button>
            <BottomSheetClose render={<Button type="button" variant="outline" />}>
              やめる
            </BottomSheetClose>
          </>
        }
      >
        {target && (
          <KeyValueList className="text-base">
            <KeyValueRow label="店舗">
              <span className="wrap-anywhere">{storeName}</span>
            </KeyValueRow>
            <KeyValueRow label="サービス">
              <span className="wrap-anywhere">{target.item.name}</span>
            </KeyValueRow>
            <KeyValueRow label="お客様料金" money>
              <CustomerPrice item={target.item} />
            </KeyValueRow>
            <KeyValueRow label="あなたの報酬" money>
              <Money amount={target.item.remuneration} className="font-semibold" />
            </KeyValueRow>
          </KeyValueList>
        )}
      </BottomSheet>
    </>
  );
}

function ConditionCard({
  item,
  storeName,
  disabled,
  onDecide,
}: {
  item: OwnServiceConditionSummary;
  storeName: string;
  disabled: boolean;
  onDecide: (decision: ConsentDecision) => void;
}) {
  return (
    <section aria-label={item.name} className={PANEL}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 space-y-0.5">
          <h2 className="font-semibold wrap-anywhere">{item.name}</h2>
          <p className="text-sm text-muted-foreground wrap-anywhere">{storeName}</p>
        </div>
        {item.consent_status && (
          <StatusBadge status={STATUS_WORDS[item.consent_status]} className="shrink-0" />
        )}
      </div>
      <p className="text-sm text-muted-foreground">{summary(item)}</p>
      <dl className="grid grid-cols-2 gap-3 rounded-lg bg-muted/50 p-3">
        <div className="space-y-0.5">
          <dt className="text-sm text-muted-foreground">お客様料金</dt>
          <dd className="tabular-nums">
            <CustomerPrice item={item} />
          </dd>
        </div>
        <div className="space-y-0.5">
          <dt className="text-sm text-muted-foreground">あなたの報酬</dt>
          <dd>
            <Money amount={item.remuneration} className="text-2xl font-bold" />
          </dd>
        </div>
      </dl>
      {item.consent_status === 'RECONFIRMATION_REQUIRED' && (
        <p className="text-sm text-foreground">条件が変わりました。内容を確かめてください。</p>
      )}
      {item.consent_status && (
        <div className="grid grid-cols-2 gap-2 pt-1">
          {decisionsFor(item.consent_status).map(decision => (
            <Button
              key={decision}
              type="button"
              variant={decision === 'ACCEPTED' ? 'default' : 'outline'}
              className="min-h-11 only:col-span-2"
              disabled={disabled}
              onClick={() => onDecide(decision)}
            >
              {DECISIONS[decision].verb}
            </Button>
          ))}
        </div>
      )}
    </section>
  );
}
