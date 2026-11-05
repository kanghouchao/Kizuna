'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { staffApi, type StaffResponse } from '@/entities/user';
import { useResource, useVersionedWrite } from '@/shared/lib';
import { Button, RegionError, Skeleton } from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import { StaffForm, type StaffFormValues, type StaffGrants } from './StaffForm';
import { toGrantRequest } from './toGrantRequest';

const LIST_LINK = { href: '/admin/staff', label: '従業員一覧へ' };

function grantsOf(staff: StaffResponse): StaffGrants {
  return {
    role_ids: (staff.roles ?? []).flatMap(role => (role.id === undefined ? [] : [role.id])),
    // 範囲の欠落を全店舗にすると保存だけで授与範囲が拡大するため、個別店舗へ倒す。
    storeSet: {
      storeScopeType: staff.store_scope_type ?? 'SPECIFIC_STORES',
      storeIds: staff.store_ids ?? [],
    },
  };
}

export default function StaffEditPage() {
  const rawId = useParams<{ id: string }>()?.id;
  // 数字でない id は指せる従業員が無いので、取りに行かず notFound として扱う
  const id = rawId !== undefined && /^\d+$/.test(rawId) ? Number(rawId) : null;
  const router = useRouter();
  const detail = useResource(id !== null ? () => staffApi.get(id) : null, [id]);
  const writer = useVersionedWrite(detail.reload);
  const failure = id !== null ? detail.failure : 'notFound';
  const staff = detail.data;

  if (id === null || failure !== null) {
    return (
      <div className="space-y-6">
        <PageHeader title="従業員の権限を編集" />
        {failure === 'notFound' ? (
          // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
          <RegionError message="この従業員は見つかりませんでした" fallback={LIST_LINK} />
        ) : (
          <RegionError
            message="従業員の情報を取得できませんでした"
            onRetry={() => void writer.reload()}
          />
        )}
      </div>
    );
  }

  if (detail.isLoading || staff === null) {
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

  const title = `${staff.display_name ?? ''} の権限を編集`;
  // editable は委譲の守衛によるサーバの判定。押しても拒まれるフォームは出さない
  if (!staff.editable) {
    return (
      <div className="space-y-6">
        <PageHeader title={title} description={staff.email} />
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">
            この従業員の権限は、あなたの権限では編集できません
          </p>
          <Button variant="outline" render={<Link href={LIST_LINK.href} />}>
            {LIST_LINK.label}
          </Button>
        </div>
      </div>
    );
  }

  const submit = async (values: StaffFormValues) => {
    await writer.write(
      () => staffApi.update(id, { ...toGrantRequest(values), version: staff.version }),
      {
        success: '権限を更新しました',
        failure: '権限の更新に失敗しました',
        onSuccess: () => router.push('/admin/staff'),
      }
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader title={title} description={staff.email} />
      {writer.conflict && (
        <div role="alert" className="space-y-3 rounded-lg border p-4">
          <p>他の担当者が更新しました。最新の内容を読み込んでから、改めて保存してください。</p>
          <Button variant="outline" onClick={() => void writer.reload()}>
            最新の内容を読み込む
          </Button>
        </div>
      )}
      {/* version が変わったら欄を取得した最新の値で描き直す */}
      <StaffForm
        key={staff.version}
        editing={{ displayName: staff.display_name ?? '', grants: grantsOf(staff) }}
        locked={writer.busy || writer.conflict}
        onSubmit={submit}
      />
    </div>
  );
}
