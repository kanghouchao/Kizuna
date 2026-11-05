'use client';

import { useParams, useRouter } from 'next/navigation';
import { serviceIdentityApi } from '@/entities/user';
import {
  GrantForm,
  grantsOf,
  toGrantRequest,
  type GrantFormValues,
} from '@/features/staff-management';
import { useResource, useVersionedWrite } from '@/shared/lib';
import { Button, RegionError, Skeleton } from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import { LIST_HREF, SERVICE_IDENTITY_GRANT_FORM } from './serviceIdentityForm';

export default function ServiceIdentityEditPage() {
  const rawId = useParams<{ id: string }>()?.id;
  // 数字でない id は指せるサービスIDが無いので、取りに行かず notFound として扱う
  const id = rawId !== undefined && /^\d+$/.test(rawId) ? Number(rawId) : null;
  const router = useRouter();
  const detail = useResource(id !== null ? () => serviceIdentityApi.get(id) : null, [id]);
  const writer = useVersionedWrite(detail.reload);
  const failure = id !== null ? detail.failure : 'notFound';
  const identity = detail.data;

  if (id === null || failure !== null) {
    return (
      <div className="space-y-6">
        <PageHeader title="サービスIDの権限を編集" />
        {failure === 'notFound' ? (
          // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
          <RegionError
            message="このサービスIDは見つかりませんでした"
            fallback={{ href: LIST_HREF, label: 'サービスID一覧へ' }}
          />
        ) : (
          <RegionError
            message="サービスIDの情報を取得できませんでした"
            onRetry={() => void writer.reload()}
          />
        )}
      </div>
    );
  }

  if (detail.isLoading || identity === null) {
    // 形は既知（見出し・権限の節・結果の節）なので、同じ配置の骨組みで待つ
    return (
      <div className="space-y-6" aria-busy="true">
        <span className="sr-only">読み込み中...</span>
        <Skeleton className="h-8 w-64" />
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <Skeleton className="h-96" />
          <Skeleton className="h-64" />
        </div>
      </div>
    );
  }

  const submit = async (values: GrantFormValues) => {
    await writer.write(
      () => serviceIdentityApi.update(id, { ...toGrantRequest(values), version: identity.version }),
      {
        success: '権限を更新しました',
        failure: '権限の更新に失敗しました',
        onSuccess: () => router.push(LIST_HREF),
      }
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader title={`${identity.display_name ?? ''} の権限を編集`} />
      {writer.conflict && (
        <div role="alert" className="space-y-3 rounded-lg border p-4">
          <p>他の担当者が更新しました。最新の内容を読み込んでから、改めて保存してください。</p>
          <Button variant="outline" onClick={() => void writer.reload()}>
            最新の内容を読み込む
          </Button>
        </div>
      )}
      {/* version が変わったら欄を取得した最新の値で描き直す */}
      <GrantForm
        {...SERVICE_IDENTITY_GRANT_FORM}
        key={identity.version}
        defaultValues={{ display_name: '', ...grantsOf(identity) }}
        editingName={identity.display_name ?? ''}
        unnamedSubject="このサービスID"
        loadRoles={serviceIdentityApi.grantableRoles}
        locked={writer.busy || writer.conflict}
        onSubmit={submit}
      />
    </div>
  );
}
