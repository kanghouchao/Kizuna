'use client';

import { useState } from 'react';
import { notify } from '@/shared/notify';
import { StoreProfileUpdateRequest, storeProfileApi } from '@/entities/store-profile';
import { useResource } from '@/shared/lib';
import { RegionError } from '@/shared/ui';
import { StoreProfileForm } from './StoreProfileForm';

export default function StoreProfilePage() {
  const {
    data: config,
    setData: setConfig,
    isLoading,
    failure,
    reload: loadConfig,
  } = useResource(() => storeProfileApi.get());
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: StoreProfileUpdateRequest) => {
    setIsSubmitting(true);
    try {
      const updated = await storeProfileApi.update(data);
      setConfig(updated);
      notify.success('設定を保存しました');
    } catch (error) {
      notify.error('設定の保存に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
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
      <div className="max-w-4xl mx-auto space-y-6">
        <h1 className="text-2xl font-bold text-foreground">店舗情報</h1>
        <RegionError message="店舗情報の取得に失敗しました" onRetry={() => void loadConfig()} />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-foreground">店舗情報</h1>
        <p className="text-sm text-muted-foreground mt-1">店舗サイトの外観をカスタマイズします。</p>
      </div>
      <StoreProfileForm initialData={config} onSubmit={handleSubmit} isSubmitting={isSubmitting} />
    </div>
  );
}
