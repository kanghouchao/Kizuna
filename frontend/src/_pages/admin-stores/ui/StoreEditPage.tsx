'use client';

import { startTransition, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Store, UpdateStoreRequest, adminStoreApi, storeShortNameError } from '@/entities/store';
import { useStoreContext } from '@/entities/user';
import { useResource, useWrite } from '@/shared/lib';
import { Button, Card, CardContent, Input, Label, RegionError } from '@/shared/ui';

export default function EditStorePage() {
  const id = useParams<{ id: string }>()?.id;
  const router = useRouter();
  const { reload: reloadStores } = useStoreContext();

  const { busy: saving, write } = useWrite();
  const {
    data: store,
    isLoading: loading,
    failure,
    reload: reloadStore,
  } = useResource(id ? () => adminStoreApi.getById(id) : null, [id]);
  // 欄の起点は取得した店舗で、編集を始めたらその下書きが優先する。効果で写すと、店舗が
  // 届いたレンダーで欄が空のまま一度描かれる。下書きは書き始めた時点の店舗に結び付ける —
  // 別の店舗が届いたら、その店舗の値で描き直す
  const [draft, setDraft] = useState<{ store: Store | null; values: UpdateStoreRequest } | null>(
    null
  );
  const formData: UpdateStoreRequest =
    draft !== null && draft.store === store
      ? draft.values
      : { name: store?.name ?? '', short_name: store?.short_name ?? '' };
  const edit = (values: UpdateStoreRequest) => setDraft({ store, values });
  const [errors, setErrors] = useState<Partial<UpdateStoreRequest>>({});
  // id を持たない URL で開かれた＝指せる店舗が無い。取りに行けないので再試行も置けない
  const loadFailure = id ? failure : 'notFound';

  const validate = (): boolean => {
    const next: Partial<UpdateStoreRequest> = {};
    if (!formData.name.trim()) next.name = '店舗名は必須です';
    const shortNameError = storeShortNameError(formData.short_name);
    if (shortNameError) next.short_name = shortNameError;
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!store) return;
    if (!validate()) return;
    void write(
      async () => {
        await adminStoreApi.update(store.id, {
          ...formData,
          short_name: formData.short_name.trim(),
        });
        // 店舗一覧は画面をまたいで共有される。反映は画面を離れると呼ばれないので、応答の直後に取り直す
        reloadStores();
      },
      {
        success: '店舗情報を更新しました',
        failure: '更新に失敗しました。入力内容をご確認ください',
        onSuccess: () => router.push('/admin/stores'),
        onFailure: error => {
          if (
            typeof error === 'object' &&
            error !== null &&
            'response' in error &&
            typeof error.response === 'object' &&
            error.response !== null &&
            'data' in error.response &&
            typeof error.response.data === 'object' &&
            error.response.data !== null &&
            'errors' in error.response.data
          ) {
            const fieldErrors = error.response.data.errors as Partial<UpdateStoreRequest>;
            startTransition(() => setErrors(fieldErrors));
          }
        },
      }
    );
  };

  if (loading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  // 取得できなかったこの頁自身が失敗を名乗るので、一覧へは送り返さない
  if (loadFailure !== null) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">店舗編集</h1>
        {loadFailure === 'notFound' ? (
          // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
          <RegionError
            message="この店舗は見つかりませんでした"
            fallback={{ href: '/admin/stores', label: '店舗一覧へ' }}
          />
        ) : (
          <RegionError message="店舗情報の取得に失敗しました" onRetry={() => void reloadStore()} />
        )}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">店舗編集</h1>
        <p className="text-sm text-muted-foreground mt-1">
          店舗の基本情報を編集します。ドメインは現在変更できません。
        </p>
      </div>

      <Card>
        <CardContent>
          <form onSubmit={handleSave} className="space-y-6">
            {/* 店舗名 */}
            <div className="grid gap-2">
              <Label htmlFor="name">
                店舗名 <span className="text-destructive-strong">*</span>
              </Label>
              <Input
                id="name"
                type="text"
                value={formData.name}
                onChange={e => edit({ ...formData, name: e.target.value })}
                aria-invalid={!!errors.name}
              />
              {errors.name && <p className="text-sm text-destructive-strong">{errors.name}</p>}
            </div>

            {/* 略称 */}
            <div className="grid gap-2">
              <Label htmlFor="short_name">
                略称 <span className="text-destructive-strong">*</span>
              </Label>
              <Input
                id="short_name"
                type="text"
                value={formData.short_name}
                onChange={e => edit({ ...formData, short_name: e.target.value })}
                aria-invalid={!!errors.short_name}
              />
              {errors.short_name && (
                <p className="text-sm text-destructive-strong">{errors.short_name}</p>
              )}
              <p className="text-sm text-muted-foreground">
                1〜3 文字で入力してください。店舗の切替ボタンに表示されます。
              </p>
            </div>

            {/* ドメイン（読み取り専用） */}
            {store && (
              <div className="rounded-lg border p-4">
                <h2 className="text-sm font-medium text-foreground mb-2">ドメイン</h2>
                <p className="text-sm text-foreground break-all">{store.domain}</p>
              </div>
            )}

            {/* 操作 */}
            <div className="flex justify-end space-x-3">
              <Button type="button" variant="outline" onClick={() => router.push('/admin/stores')}>
                キャンセル
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? '保存中...' : '保存'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
