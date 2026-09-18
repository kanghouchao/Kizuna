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
import { notify } from '@/shared/notify';
import { ListPage } from '@/widgets/list-page';
import {
  getApiErrorMessage,
  isConflict,
  isForbidden,
  isNotFound,
  useListPage,
  useResource,
} from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  RegionError,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/shared/ui';

const labels: Record<ConsentStatus, string> = {
  NOT_ACCEPTED: '未受諾',
  ACCEPTED: '受諾済み',
  REJECTED: '本人が拒否',
  RECONFIRMATION_REQUIRED: '条件改定・再受諾待ち',
};

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
  const [selectedStore, setSelectedStore] = useState<string | null>(null);
  const storeId = selectedStore ?? stores.data?.[0]?.store_id?.toString();
  return (
    <div className="space-y-4 p-4">
      {(stores.isLoading || stores.failure || storesDenied || !stores.data?.length) && (
        <h1 className="text-2xl font-bold">サービス条件</h1>
      )}
      {stores.isLoading ? (
        <p>読み込み中...</p>
      ) : storesDenied ? (
        <p role="alert">サービス条件を確認する権限がありません。</p>
      ) : stores.failure ? (
        <RegionError message="所属店舗の取得に失敗しました" onRetry={() => void stores.reload()} />
      ) : !stores.data?.length ? (
        <p>有効な在籍店舗がありません</p>
      ) : (
        <>
          <Select
            value={storeId ?? ''}
            onValueChange={value => setSelectedStore(value)}
            items={stores.data.map(store => ({
              value: String(store.store_id),
              label: store.store_name,
            }))}
          >
            <SelectTrigger aria-label="店舗" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {stores.data.map(store => (
                <SelectItem key={store.store_id} value={String(store.store_id)}>
                  {store.store_name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {storeId && <Conditions key={storeId} storeId={storeId} />}
        </>
      )}
    </div>
  );
}

function Conditions({ storeId }: { storeId: string }) {
  const [saveDenied, setSaveDenied] = useState(false);
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const [targetOpen, setTargetOpen] = useState(false);
  const [target, setTarget] = useState<{
    item: OwnServiceConditionSummary;
    decision: ConsentDecision;
  } | null>(null);
  const list = useListPage(page => ownServiceApi.list(storeId, page));
  const denied = saveDenied || isForbidden(list.error);
  const unavailable = isNotFound(list.error);
  const save = async () => {
    if (!target || saving || target.item.consent_version === undefined) return;
    const { item, decision } = target;
    setSaving(true);
    setTargetOpen(false);
    setMessage('');
    try {
      await ownServiceApi.decide(storeId, item.id, {
        terms_version: item.terms_version,
        consent_version: item.consent_version!,
        decision,
      });
      notify.success('意思を保存しました');
      await list.reload();
    } catch (error) {
      if (isForbidden(error)) {
        setSaveDenied(true);
        notify.warning('権限がないため保存されませんでした');
      } else if (isNotFound(error)) {
        notify.warning('対象が変更されたため保存されませんでした');
        setMessage('サービスまたは在籍が変更されています。最新の一覧を確認してください。');
        await list.reload();
      } else if (isConflict(error)) {
        notify.warning('表示後に変更があり、保存されませんでした');
        setMessage('条件または意思が変更されています。最新の内容を確認してから操作してください。');
        await list.reload();
      } else
        notify.error(
          getApiErrorMessage(error, '意思の保存に失敗しました。もう一度操作してください。')
        );
    } finally {
      setSaving(false);
    }
  };
  if (denied) return <p role="alert">サービス条件を確認する権限がありません。</p>;
  if (unavailable)
    return <p role="alert">有効な在籍またはサービスが見つかりません。店舗を選び直してください。</p>;
  return (
    <>
      {message && (
        <p role="alert" className="rounded-lg border p-3 text-foreground">
          {message}
        </p>
      )}
      <ListPage
        title="サービス条件"
        state={{
          ...list,
          onPageChange: page => {
            if (!saving) void list.onPageChange(page);
          },
        }}
        emptyMessage="サービス条件はありません"
        errorMessage="サービス条件の取得に失敗しました"
        onRetry={() => void list.reload()}
      >
        <div className="space-y-3">
          {list.rows.map(item => (
            <Card key={item.id}>
              <CardContent className="space-y-2 p-4">
                <h2 className="font-semibold">{item.name}</h2>
                <p>
                  {serviceKindLabels[item.kind]}
                  {item.duration_minutes !== undefined && ` · ${item.duration_minutes} 分`}
                  {item.charge_type && ` · ${item.charge_type === 'FREE' ? '無料' : '有料'}`}
                </p>
                <p>顧客価格: {item.price.toLocaleString()} 円</p>
                <p>固定報酬: {item.remuneration.toLocaleString()} 円</p>
                {item.consent_status && (
                  <>
                    <p className="font-medium">{labels[item.consent_status]}</p>
                    <div className="flex gap-2">
                      <Button
                        disabled={saving || item.consent_status === 'ACCEPTED'}
                        onClick={() => {
                          setTarget({ item, decision: 'ACCEPTED' });
                          setTargetOpen(true);
                        }}
                      >
                        受諾する
                      </Button>
                      <Button
                        variant="outline"
                        disabled={saving || item.consent_status === 'REJECTED'}
                        onClick={() => {
                          setTarget({ item, decision: 'REJECTED' });
                          setTargetOpen(true);
                        }}
                      >
                        拒否する
                      </Button>
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      </ListPage>
      <Dialog
        open={targetOpen}
        onOpenChange={open => {
          if (!open) setTargetOpen(false);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {target?.decision === 'ACCEPTED' ? 'サービス条件を確認' : '特殊サービスを拒否'}
            </DialogTitle>
            <DialogDescription>
              {target &&
                `${target.item.name} · 顧客価格 ${target.item.price.toLocaleString()} 円 · 固定報酬 ${target.item.remuneration.toLocaleString()} 円`}
            </DialogDescription>
          </DialogHeader>
          <Button onClick={() => void save()} disabled={saving}>
            {target?.decision === 'ACCEPTED' ? '確認して受諾する' : '確認して拒否する'}
          </Button>
          <Button variant="outline" onClick={() => setTargetOpen(false)}>
            キャンセル
          </Button>
        </DialogContent>
      </Dialog>
    </>
  );
}
