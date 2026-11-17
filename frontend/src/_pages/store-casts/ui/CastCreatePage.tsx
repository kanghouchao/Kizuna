'use client';

import { useRouter } from 'next/navigation';
import { CastForm, type CastFormData, toCastCreateRequest } from '@/features/cast-form';
import { castApi } from '@/entities/cast';
import { notify } from '@/shared/notify';
import { storePath, useWrite } from '@/shared/lib';
import { useCurrentStoreId } from '@/entities/user';

/** 新規キャスト登録ページ */
export default function CastCreatePage() {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const { busy, write } = useWrite();

  const handleSubmit = (data: CastFormData) =>
    write(() => castApi.create(toCastCreateRequest(data)), {
      success: 'キャストを登録しました',
      failure: () => notify.error('キャストの登録に失敗しました'),
      onSuccess: () => router.push(storePath(storeId, '/casts')),
    });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">新規キャスト登録</h1>
        <p className="text-sm text-muted-foreground mt-1">新しいキャスト情報を入力してください。</p>
      </div>
      <CastForm onSubmit={handleSubmit} isSubmitting={busy} />
    </div>
  );
}
