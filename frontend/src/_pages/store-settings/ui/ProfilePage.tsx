'use client';

import { useCallback, useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
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

  // 再試行から呼び直せるよう effect の外に置く。先頭で読み込み中へ戻すのは、同じ姿のまま
  // 二度目の失敗を迎えると RegionError が mount し直されず読み上げに何も届かないため
  const loadConfig = useCallback(async () => {
    setIsLoading(true);
    try {
      setConfig(await storeProfileApi.get());
    } catch {
      // 取れなかった設定でフォームを描くと、保存がその古い値を本当にしてしまう
      setConfig(null);
    } finally {
      setIsLoading(false);
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
      toast.success('設定を保存しました');
    } catch (error) {
      toast.error('設定の保存に失敗しました');
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
