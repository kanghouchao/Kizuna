'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { CastForm, CastFormData } from './CastForm';
import { CastResponse, CastUpdateRequest, castApi } from '@/entities/cast';
import { notify } from '@/shared/notify';
import { isNotFound, storePath } from '@/shared/lib';
import { RegionError } from '@/shared/ui';

/** キャスト編集ページ */
export default function CastEditPage() {
  const params = useParams();
  const id = params.id as string;
  const storeId = params.storeId as string;
  const router = useRouter();
  const [cast, setCast] = useState<CastResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadFailure, setLoadFailure] = useState<'notFound' | 'error' | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する。失敗が
  // キャストをクリアするので、在途の古い失敗が新しい成功を消し得る
  const requestIdRef = useRef(0);

  // 再試行から呼び直せるよう effect の外に置く。取得できなかったこの頁自身が失敗を名乗るので、
  // 一覧へ送り返さない — 離脱すると説明責任が着地先へ移り、開いていた頁で再試行できなくなる
  const fetchCast = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const data = await castApi.get(id);
      if (requestId === requestIdRef.current) {
        setCast(data);
        setLoadFailure(null);
      }
    } catch (error) {
      if (requestId === requestIdRef.current) {
        setCast(null);
        // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
        setLoadFailure(isNotFound(error) ? 'notFound' : 'error');
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void fetchCast();
  }, [fetchCast]);

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
        custom_fields: data.custom_fields,
      };
      await castApi.update(id, requestData);
      notify.success('キャスト情報を更新しました');
      router.push(storePath(storeId, '/casts'));
    } catch {
      notify.error('キャスト情報の更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  if (loadFailure !== null) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">キャスト編集</h1>
        {loadFailure === 'notFound' ? (
          <RegionError
            message="このキャストは見つかりませんでした"
            fallback={{ href: storePath(storeId, '/casts'), label: 'キャスト一覧へ' }}
          />
        ) : (
          <RegionError
            message="キャスト情報の取得に失敗しました"
            onRetry={() => void fetchCast()}
          />
        )}
      </div>
    );
  }

  if (!cast) return null;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">キャスト編集</h1>
        <p className="text-sm text-muted-foreground mt-1">「{cast.name}」の情報を編集します。</p>
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
        existingCustomFields={cast.custom_fields}
        onSubmit={handleSubmit}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}
