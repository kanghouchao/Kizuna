'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { CastForm, CastFormData } from '../_components/CastForm';
import { castApi } from '@/services/tenant/api';
import { CastCreateRequest } from '@/types/api';
import { toast } from 'react-hot-toast';

/** 新規キャスト登録ページ */
export default function CastCreatePage() {
  const router = useRouter();
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
      toast.success('キャストを登録しました');
      router.push('/tenant/casts');
    } catch {
      toast.error('キャストの登録に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">新規キャスト登録</h1>
        <p className="text-sm text-gray-500 mt-1">新しいキャスト情報を入力してください。</p>
      </div>
      <CastForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
    </div>
  );
}
