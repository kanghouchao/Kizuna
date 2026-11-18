'use client';

import { useId, useState } from 'react';
import { notify } from '@/shared/notify';
import { StoreProfileUpdateRequest, storeProfileApi } from '@/entities/store-profile';
import { useStoreContext } from '@/entities/user';
import { useResource, useWrite } from '@/shared/lib';
import { Badge, Button, ConfirmDialog, RegionError } from '@/shared/ui';
import { StoreProfileForm } from './StoreProfileForm';

/** 現在店舗の稼働状態。準備中の店舗だけ稼働中へ移す操作を持つ（逆向きの操作は無い）。 */
function StoreActivationSection() {
  const headingId = useId();
  const { currentStore, reload } = useStoreContext();
  const [confirming, setConfirming] = useState(false);
  const { busy, write } = useWrite();
  // 店舗コンテキストの取り直しは完了を返さない。稼働中が届いてボタンが消えるまで、成立した操作を送り直させない
  const [activated, setActivated] = useState(false);
  const status = currentStore?.status;

  const activate = () =>
    write(
      async () => {
        await storeProfileApi.activate();
        // 稼働状態は店舗コンテキストが持つ授権店舗一覧の値で、他の画面も読む。
        // この画面を離れた後でも取り直すよう、反映ではなく送信に含める
        reload();
      },
      {
        success: '店舗を稼働中にしました',
        failure: '稼働状態の変更に失敗しました',
        onSuccess: () => setActivated(true),
      }
    );

  return (
    <section
      aria-labelledby={headingId}
      className="mb-8 bg-card text-card-foreground p-8 rounded-xl shadow-sm border"
    >
      <h2
        id={headingId}
        className="text-lg font-semibold text-foreground mb-4 border-l-4 border-primary pl-3"
      >
        稼働状態
      </h2>
      <div className="flex items-center gap-4">
        {/* 準備中は素の outline、稼働中は肯定側の tint（DESIGN.md のステータスピル） */}
        {status === 'PREPARING' && <Badge variant="outline">準備中</Badge>}
        {status === 'ACTIVE' && (
          <Badge variant="outline" className="border-transparent bg-success/10 text-success-strong">
            稼働中
          </Badge>
        )}
        {status === 'PREPARING' && (
          <Button type="button" onClick={() => setConfirming(true)} disabled={busy || activated}>
            稼働中にする
          </Button>
        )}
      </div>
      <ConfirmDialog
        open={confirming}
        title="店舗を稼働中にしますか？"
        description="稼働中にした店舗は準備中へ戻せません。"
        confirmLabel="稼働中にする"
        onConfirm={() => void activate()}
        onClose={() => setConfirming(false)}
      />
    </section>
  );
}

export default function StoreProfilePage() {
  const {
    data: config,
    setData: setConfig,
    isLoading,
    failure,
    reload: loadConfig,
  } = useResource(() => storeProfileApi.get());
  const { busy, write } = useWrite();

  const handleSubmit = async (data: StoreProfileUpdateRequest) => {
    await write(() => storeProfileApi.update(data), {
      success: '設定を保存しました',
      failure: () => notify.error('設定の保存に失敗しました'),
      onSuccess: setConfig,
    });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-100">
        <div className="text-muted-foreground">読み込み中...</div>
      </div>
    );
  }

  // 取れなかった設定でフォームを描くと、保存がその古い値を本当にしてしまう。出口の無い
  // 赤字ではなく、頁自身が失敗を名乗って再試行を持つ。`!config` は型の絞り込み — 中身の
  // 無い応答はフックが失敗へ倒すので、ここには失敗の姿として届く
  if (failure !== null || !config) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">店舗情報</h1>
        <RegionError message="店舗情報の取得に失敗しました" onRetry={() => void loadConfig()} />
      </div>
    );
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-foreground">店舗情報</h1>
        <p className="text-sm text-muted-foreground mt-1">店舗サイトの外観をカスタマイズします。</p>
      </div>
      <StoreActivationSection />
      <StoreProfileForm initialData={config} onSubmit={handleSubmit} busy={busy} />
    </div>
  );
}
