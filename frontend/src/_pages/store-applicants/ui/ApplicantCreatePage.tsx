'use client';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { applicantApi, type ApplicantIntake } from '@/entities/applicant';
import { getApiErrorMessage } from '@/shared/lib';
import { storePath } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Button } from '@/shared/ui';
import { ApplicantIntakeForm } from './ApplicantIntakeForm';
export default function ApplicantCreatePage() {
  const { storeId } = useParams<{ storeId: string }>();
  const router = useRouter();
  const save = async (data: ApplicantIntake) => {
    try {
      const result = await applicantApi.create(data);
      notify.success('応募者を登録しました');
      router.push(storePath(storeId, `/applicants/${result.id}`));
    } catch (error) {
      notify.error(getApiErrorMessage(error, '応募者の登録に失敗しました'));
    }
  };
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">応募者登録</h1>
        <Button variant="outline" render={<Link href={storePath(storeId, '/applicants')} />}>
          一覧へ
        </Button>
      </div>
      <ApplicantIntakeForm onSave={save} />
    </div>
  );
}
