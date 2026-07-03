'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { CustomerForm, CustomerFormData } from './CustomerForm';
import { CustomerCreateRequest, customerApi } from '@/entities/customer';
import { toast } from 'react-hot-toast';

/** 新規顧客登録ページ */
export default function CustomerCreatePage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: CustomerFormData) => {
    try {
      setIsSubmitting(true);
      const requestData: CustomerCreateRequest = {
        name: data.name,
        phone_number: data.phone_number || undefined,
        phone_number2: data.phone_number2 || undefined,
        address: data.address || undefined,
        building_name: data.building_name || undefined,
        classification: data.classification || undefined,
        has_pet: data.has_pet,
        rank: data.rank || undefined,
        line_id: data.line_id || undefined,
        usage_areas: data.usage_areas || undefined,
        ng_type: data.ng_type || undefined,
        ng_content: data.ng_content || undefined,
      };
      await customerApi.create(requestData);
      toast.success('顧客を登録しました');
      router.push('/tenant/customers');
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
