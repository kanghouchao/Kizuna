'use client';

import { useState, useEffect, use } from 'react';
import { useRouter } from 'next/navigation';
import { CastForm, CastFormData } from '../../_components/CastForm';
import { castApi } from '@/services/tenant/api';
import { CastResponse, CastUpdateRequest } from '@/types/api';
import { toast } from 'react-hot-toast';

/** キャスト編集ページ */
export default function CastEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const [cast, setCast] = useState<CastResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchCast = async () => {
      try {
        const data = await castApi.get(id);
        setCast(data);
      } catch {
        toast.error('キャスト情報の取得に失敗しました');
        router.push('/tenant/casts');
      } finally {
        setIsLoading(false);
      }
    };
    fetchCast();
  }, [id, router]);

  const handleSubmit = async (data: CastFormData) => {
    try {
      setIsSubmitting(true);
      const requestData: CastUpdateRequest = {
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
      await castApi.update(id, requestData);
      toast.success('キャスト情報を更新しました');
      router.push('/tenant/casts');
    } catch {
      toast.error('キャスト情報の更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-gray-500">読み込み中...</div>;
  }

  if (!cast) return null;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">キャスト編集</h1>
        <p className="text-sm text-gray-500 mt-1">「{cast.name}」の情報を編集します。</p>
      </div>
      <CastForm
        initialData={{
          name: cast.name,
          status: cast.status,
          photo_url: cast.photo_url || '',
          introduction: cast.introduction || '',
          age: cast.age ?? null,
          height: cast.height ?? null,
          bust: cast.bust ?? null,
          waist: cast.waist ?? null,
          hip: cast.hip ?? null,
          display_order: cast.display_order ?? 0,
        }}
        onSubmit={handleSubmit}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}
