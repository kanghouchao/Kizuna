'use client';

import { useCallback, useRef, useState, useEffect } from 'react';
import { notify } from '@/shared/notify';
import {
  StoreProfileResponse,
  StoreProfileUpdateRequest,
  storeProfileApi,
} from '@/entities/store-profile';
import { RegionError } from '@/shared/ui';
import { StoreProfileForm } from './StoreProfileForm';

export default function StoreProfilePage() {
  const [config, setConfig] = useState<StoreProfileResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する。失敗が
  // 設定をクリアするようになったので、在途の古い失敗が新しい成功を消し得る
  const requestIdRef = useRef(0);

  // 再試行から呼び直せるよう effect の外に置く。先頭で読み込み中へ戻すのは、同じ姿のまま
  // 二度目の失敗を迎えると RegionError が mount し直されず読み上げに何も届かないため
  const loadConfig = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const data = await storeProfileApi.get();
      if (requestId === requestIdRef.current) setConfig(data);
    } catch {
      // 取れなかった設定でフォームを描くと、保存がその古い値を本当にしてしまう
      if (requestId === requestIdRef.current) setConfig(null);
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConfig();
  }, [loadConfig]);

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

  // 取得に失敗したときだけ config が null のまま読み込みを終える。出口の無い赤字ではなく、
  // 頁自身が失敗を名乗って再試行を持つ
  if (!config) {
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
