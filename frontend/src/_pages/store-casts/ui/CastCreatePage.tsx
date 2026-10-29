'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { CastForm, type CastFormData, toCastCreateRequest } from '@/features/cast-form';
import { castApi } from '@/entities/cast';
import { notify } from '@/shared/notify';
import { storePath } from '@/shared/lib';

/** 新規キャスト登録ページ */
export default function CastCreatePage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: CastFormData) => {
    try {
      setIsSubmitting(true);
      await castApi.create(toCastCreateRequest(data));
      notify.success('キャストを登録しました');
      router.push(storePath('/casts'));
    } catch {
      notify.error('キャストの登録に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">新規キャスト登録</h1>
        <p className="text-sm text-muted-foreground mt-1">新しいキャスト情報を入力してください。</p>
      </div>
      <CastForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
    </div>
  );
}
