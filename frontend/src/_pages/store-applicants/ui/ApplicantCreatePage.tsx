'use client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { applicantApi, type ApplicantIntake } from '@/entities/applicant';
import { storePath, useWrite } from '@/shared/lib';
import { Button } from '@/shared/ui';
import { ApplicantIntakeForm } from './ApplicantIntakeForm';
import { useCurrentStoreId } from '@/entities/user';
export default function ApplicantCreatePage() {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const { busy, write } = useWrite();
  const save = async (data: ApplicantIntake) => {
    await write(() => applicantApi.create(data), {
      success: '応募者を登録しました',
      failure: '応募者の登録に失敗しました',
      onSuccess: result => router.push(storePath(storeId, `/applicants/${result.id}`)),
    });
  };
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">応募者登録</h1>
        <Button variant="outline" render={<Link href={storePath(storeId, '/applicants')} />}>
          一覧へ
        </Button>
      </div>
      <ApplicantIntakeForm busy={busy} onSave={save} />
    </div>
  );
}
