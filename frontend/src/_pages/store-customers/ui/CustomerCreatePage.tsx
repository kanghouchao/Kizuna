'use client';

import { useRouter } from 'next/navigation';
import { CustomerForm, CustomerFormData, toCustomerRequest } from './CustomerForm';
import { customerApi } from '@/entities/customer';
import { storePath, useWrite } from '@/shared/lib';
import { useCurrentStoreId } from '@/entities/user';

/** 新規顧客登録ページ */
export default function CustomerCreatePage() {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const { busy, write } = useWrite();

  const handleSubmit = (data: CustomerFormData) =>
    write(() => customerApi.create(toCustomerRequest(data)), {
      success: '顧客を登録しました',
      failure: '顧客の登録に失敗しました',
      onSuccess: () => router.push(storePath(storeId, '/customers')),
    });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">新規顧客登録</h1>
        <p className="text-sm text-muted-foreground mt-1">新しい顧客情報を入力してください。</p>
      </div>
      <CustomerForm creating onSubmit={handleSubmit} busy={busy} />
    </div>
  );
}
