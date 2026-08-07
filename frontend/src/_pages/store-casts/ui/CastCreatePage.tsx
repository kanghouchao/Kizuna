'use client';

import { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { CastForm, CastFormData } from './CastForm';
import { CastCreateRequest, castApi } from '@/entities/cast';
import { notify } from '@/shared/notify';
import { storePath } from '@/shared/lib';

/** 新規キャスト登録ページ */
export default function CastCreatePage() {
  const router = useRouter();
  const params = useParams();
  const storeId = params.storeId as string;
  const [isSubmitting, setIsSubmitting] = useState(false);

  /** フォーム送信処理 */
  const handleSubmit = async (data: CastFormData) => {
    try {
      setIsSubmitting(true);
      const requestData: CastCreateRequest = {
        name: data.name,
        status: data.status,
        photo_url: data.photo_url,
        introduction: data.introduction,
        age: data.age ?? undefined,
        height: data.height ?? undefined,
        bust: data.bust ?? undefined,
        waist: data.waist ?? undefined,
        hip: data.hip ?? undefined,
        display_order: data.display_order ?? undefined,
      };
      await castApi.create(requestData);
      notify.success('キャストを登録しました');
      router.push(storePath(storeId, '/casts'));
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
