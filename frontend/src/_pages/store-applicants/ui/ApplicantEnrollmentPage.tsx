'use client';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { applicantApi, type ApplicantEnrollmentInput } from '@/entities/applicant';
import type { CastCreateRequest } from '@/entities/cast';
import { CastForm, type CastFormData, toCastCreateRequest } from '@/features/cast-form';
import {
  getApiErrorMessage,
  hasPermission,
  readTokenClaims,
  storePath,
  useResource,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Button, RegionError } from '@/shared/ui';
// 在籍登録とキャスト直接登録の本文は同じ項目を持つ（entities 同士は参照できないので、両方を使うここで型を突き合わせる）。
type SameKeys<A, B> = [keyof A] extends [keyof B]
  ? [keyof B] extends [keyof A]
    ? true
    : false
  : false;
const enrollmentMirrorsCastCreation: SameKeys<ApplicantEnrollmentInput, CastCreateRequest> &
  ([CastCreateRequest] extends [ApplicantEnrollmentInput] ? true : false) &
  ([ApplicantEnrollmentInput] extends [CastCreateRequest] ? true : false) = true;
void enrollmentMirrorsCastCreation;
export default function ApplicantEnrollmentPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const resource = useResource(() => applicantApi.get(id), [id]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [canEnroll, setCanEnroll] = useState<boolean | null>(null);
  useEffect(() => {
    setCanEnroll(hasPermission(readTokenClaims(), 'CAST_MANAGE'));
  }, []);
  const detailPath = storePath(`/applicants/${id}`);
  const applicant = resource.data;
  const submit = async (data: CastFormData) => {
    if (!applicant || isSubmitting) return;
    setIsSubmitting(true);
    try {
      await applicantApi.enroll(id, applicant.version, toCastCreateRequest(data));
      notify.success('在籍を登録しました');
      router.push(detailPath);
    } catch (error) {
      notify.error(getApiErrorMessage(error, '在籍の登録に失敗しました'));
      setIsSubmitting(false);
    }
  };
  const body = () => {
    if (resource.isLoading || canEnroll === null) return <p>読み込み中...</p>;
    if (resource.failure !== null || !applicant)
      return resource.failure === 'notFound' ? (
        <RegionError
          message="応募者が見つかりませんでした"
          fallback={{ href: storePath('/applicants'), label: '一覧へ' }}
        />
      ) : (
        <RegionError
          message="応募者情報の取得に失敗しました"
          onRetry={() => void resource.reload()}
        />
      );
    if (applicant.enrollment_id)
      return (
        <RegionError
          message="この応募者は既に在籍登録されています"
          fallback={{
            href: storePath(`/casts/${applicant.enrollment_id}/edit`),
            label: '在籍を開く',
          }}
        />
      );
    if (!canEnroll)
      return (
        <RegionError
          message="在籍登録にはキャスト管理の権限が必要です"
          fallback={{ href: detailPath, label: '応募者詳細へ' }}
        />
      );
    if (applicant.status !== 'HIRED')
      return (
        <RegionError
          message="在籍登録は採用済みの応募者だけに行えます"
          fallback={{ href: detailPath, label: '応募者詳細へ' }}
        />
      );
    return (
      <>
        <p className="text-sm text-muted-foreground">
          応募者「<span className="break-all">{applicant.name}</span>
          」の在籍と未公開のプロフィールを作ります。連絡先・面接記録・添付は写しません。招待は在籍の作成後にキャスト一覧から発行してください。
        </p>
        <CastForm onSubmit={submit} isSubmitting={isSubmitting} />
      </>
    );
  };
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">在籍登録</h1>
        <Button variant="outline" render={<Link href={detailPath} />}>
          応募者詳細へ
        </Button>
      </div>
      {body()}
    </div>
  );
}
