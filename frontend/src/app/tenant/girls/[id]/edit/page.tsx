'use client';

import { useState, useEffect, use } from 'react';
import { useRouter } from 'next/navigation';
import { GirlForm, GirlFormData } from '../../_components/GirlForm';
import { girlApi } from '@/services/tenant/api';
import { GirlResponse, GirlUpdateRequest } from '@/types/api';
import { toast } from 'react-hot-toast';

/** キャスト編集ページ */
export default function GirlEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const [girl, setGirl] = useState<GirlResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchGirl = async () => {
      try {
        const data = await girlApi.get(id);
        setGirl(data);
      } catch {
        toast.error('キャスト情報の取得に失敗しました');
        router.push('/tenant/girls');
      } finally {
        setIsLoading(false);
      }
    };
    fetchGirl();
  }, [id, router]);

  const handleSubmit = async (data: GirlFormData) => {
    try {
      setIsSubmitting(true);
      const requestData: GirlUpdateRequest = {
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
      await girlApi.update(id, requestData);
      toast.success('キャスト情報を更新しました');
      router.push('/tenant/girls');
    } catch {
      toast.error('キャスト情報の更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-gray-500">読み込み中...</div>;
  }

  if (!girl) return null;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">キャスト編集</h1>
        <p className="text-sm text-gray-500 mt-1">「{girl.name}」の情報を編集します。</p>
      </div>
      <GirlForm
        initialData={{
          name: girl.name,
          status: girl.status,
          photo_url: girl.photo_url || '',
          introduction: girl.introduction || '',
          age: girl.age ?? null,
          height: girl.height ?? null,
          bust: girl.bust ?? null,
          waist: girl.waist ?? null,
          hip: girl.hip ?? null,
          display_order: girl.display_order ?? 0,
        }}
        onSubmit={handleSubmit}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}
