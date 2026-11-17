'use client';

import { useRouter, useParams } from 'next/navigation';
import { CastForm, type CastFormData } from '@/features/cast-form';
import { CastEnrollmentPanel } from './CastEnrollmentPanel';
import { CastUpdateRequest, castApi } from '@/entities/cast';
import { notify } from '@/shared/notify';
import { storePath, useResource, useWrite } from '@/shared/lib';
import { RegionError, Button } from '@/shared/ui';
import { useCurrentStoreId } from '@/entities/user';

/** キャスト編集ページ */
export default function CastEditPage() {
  const storeId = useCurrentStoreId();
  const params = useParams();
  const id = params.id as string;
  const router = useRouter();
  const {
    data: cast,
    setData: setCast,
    isLoading,
    failure,
    reload: reloadCast,
  } = useResource(() => castApi.get(id), [id]);
  // 公開の切替と保存は別の操作で、互いを待たせない
  const saving = useWrite();
  const publishing = useWrite();
  const publication = cast?.publication_status;
  const changePublication = () => {
    if (!cast) return;
    return publishing.write(
      () =>
        castApi.changePublication(id, publication === 'PUBLISHED' ? 'UNPUBLISHED' : 'PUBLISHED'),
      {
        success: '公開状態を更新しました',
        failure: () => notify.error('公開状態の更新に失敗しました'),
        onSuccess: result =>
          setCast(current =>
            current?.id === id
              ? { ...current, publication_status: result.publication_status }
              : current
          ),
      }
    );
  };

  const handleSubmit = (data: CastFormData) => {
    const requestData: CastUpdateRequest = {
      name: data.name,
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
    return saving.write(() => castApi.update(id, requestData), {
      success: 'キャスト情報を更新しました',
      failure: () => notify.error('キャスト情報の更新に失敗しました'),
      onSuccess: () => router.push(storePath(storeId, '/casts')),
    });
  };

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  // 取得できなかったこの頁自身が失敗を名乗るので、一覧へ送り返さない — 離脱すると説明責任が
  // 着地先へ移り、開いていた頁で再試行できなくなる。`!cast` は型の絞り込み — 中身の無い
  // 応答はフックが失敗へ倒すので、ここには失敗の姿として届く
  if (failure !== null || !cast) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">キャスト編集</h1>
        {failure === 'notFound' ? (
          // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
          <RegionError
            message="このキャストは見つかりませんでした"
            fallback={{ href: storePath(storeId, '/casts'), label: 'キャスト一覧へ' }}
          />
        ) : (
          <RegionError
            message="キャスト情報の取得に失敗しました"
            onRetry={() => void reloadCast()}
          />
        )}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">キャスト編集</h1>
        <p className="text-sm text-muted-foreground mt-1">「{cast.name}」の情報を編集します。</p>
      </div>
      <section aria-label="公開状態" className="space-y-3 rounded-xl border bg-card p-6">
        <h2 className="text-lg font-semibold">公開状態</h2>
        <p className="text-sm text-muted-foreground">
          公開中かつ在籍中の場合のみ、公開サイトに表示されます。在籍停止・退店では表示されません。
        </p>
        <div className="flex items-center gap-3">
          <span>公開状態: {publication === 'PUBLISHED' ? '公開' : '非公開'}</span>
          <Button
            type="button"
            variant="outline"
            disabled={publishing.busy}
            onClick={() => void changePublication()}
          >
            {publication === 'PUBLISHED' ? '非公開にする' : '公開する'}
          </Button>
        </div>
      </section>
      <CastEnrollmentPanel
        key={id}
        cast={cast}
        onChanged={result => setCast(current => (current ? { ...current, ...result } : current))}
      />
      <CastForm
        initialData={{
          name: cast.name,
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
        isSubmitting={saving.busy}
      />
    </div>
  );
}
