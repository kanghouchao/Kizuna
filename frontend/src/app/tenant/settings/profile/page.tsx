'use client';

import { useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
import { siteConfigApi } from '@/services/tenant/api';
import { SiteConfigForm } from './_components/SiteConfigForm';
import { SiteConfigResponse, SiteConfigUpdateRequest } from '@/types/api';

export default function SiteConfigPage() {
  const [config, setConfig] = useState<SiteConfigResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    try {
      const data = await siteConfigApi.get();
      setConfig(data);
    } catch (error) {
      toast.error('設定の読み込みに失敗しました');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = async (data: SiteConfigUpdateRequest) => {
    setIsSubmitting(true);
    try {
      const updated = await siteConfigApi.update(data);
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
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">読み込み中...</div>
      </div>
    );
  }

  if (!config) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-red-500">設定の読み込みに失敗しました</div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">店舗情報</h1>
        <p className="text-sm text-gray-500 mt-1">店舗サイトの外観をカスタマイズします。</p>
      </div>
      <SiteConfigForm initialData={config} onSubmit={handleSubmit} isSubmitting={isSubmitting} />
    </div>
  );
}
