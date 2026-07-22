'use client';

import { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { CustomerForm, CustomerFormData, toCustomerRequest } from './CustomerForm';
import { customerApi } from '@/entities/customer';
import { toast } from 'react-hot-toast';
import { storePath } from '@/shared/lib';

/** 新規顧客登録ページ */
export default function CustomerCreatePage() {
  const router = useRouter();
  const params = useParams();
  const storeId = params.storeId as string;
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: CustomerFormData) => {
    try {
      setIsSubmitting(true);
      await customerApi.create(toCustomerRequest(data));
      toast.success('顧客を登録しました');
      router.push(storePath(storeId, '/customers'));
    } catch {
      toast.error('顧客の登録に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">新規顧客登録</h1>
        <p className="text-sm text-gray-500 mt-1">新しい顧客情報を入力してください。</p>
      </div>
      <CustomerForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
    </div>
  );
}
