'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { CustomerCreateRequest } from '@/entities/customer';

/** 顧客フォームのデータ型 */
export interface CustomerFormData {
  name: string;
  phone_number: string;
  phone_number2: string;
  address: string;
  building_name: string;
  classification: string;
  has_pet: boolean;
  rank: string;
  line_id: string;
  usage_areas: string;
  ng_type: string;
  ng_content: string;
}

/** フォーム値を API リクエスト形式へ変換する（空文字のフィールドは undefined に落とす） */
export function toCustomerRequest(data: CustomerFormData): CustomerCreateRequest {
  return {
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
}

interface CustomerFormProps {
  /** 編集時の初期データ */
  initialData?: Partial<CustomerFormData>;
  /** フォーム送信時のコールバック */
  onSubmit: (data: CustomerFormData) => void;
  /** 送信中フラグ */
  isSubmitting?: boolean;
}

/** 顧客登録・編集フォームコンポーネント */
export function CustomerForm({ initialData, onSubmit, isSubmitting }: CustomerFormProps) {
  const router = useRouter();
  const { register, handleSubmit } = useForm<CustomerFormData>({
    defaultValues: {
      name: '',
      phone_number: '',
      phone_number2: '',
      address: '',
      building_name: '',
      classification: '',
      has_pet: false,
      rank: 'SILVER',
      line_id: '',
      usage_areas: '',
      ng_type: '',
      ng_content: '',
      ...initialData,
    },
  });

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="space-y-8 bg-white p-8 rounded-xl shadow-sm border border-gray-100"
    >
      {/* 基本情報 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          基本情報
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">名前 *</label>
            <input
              type="text"
              {...register('name', { required: true })}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">区分</label>
            <input
              type="text"
              {...register('classification')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">電話番号</label>
            <input
              type="tel"
              {...register('phone_number')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">電話番号2</label>
            <input
              type="tel"
              {...register('phone_number2')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">LINE ID</label>
            <input
              type="text"
              {...register('line_id')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ランク</label>
            <input
              type="text"
              {...register('rank')}
              placeholder="SILVER"
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
      </section>

      {/* 住所 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          住所
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">住所</label>
            <input
              type="text"
              {...register('address')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">建物名</label>
            <input
              type="text"
              {...register('building_name')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">利用エリア</label>
            <input
              type="text"
              {...register('usage_areas')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div className="flex items-end pb-2">
            <label className="inline-flex items-center text-sm font-medium text-gray-700">
              <input
                type="checkbox"
                {...register('has_pet')}
                className="mr-2 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
              />
              ペットあり
            </label>
          </div>
        </div>
      </section>

      {/* NG 情報 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          NG 情報
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">NG タイプ</label>
            <input
              type="text"
              {...register('ng_type')}
              placeholder="注意・禁止など"
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
        <div className="mt-4">
          <label className="block text-sm font-medium text-gray-700 mb-1">NG 内容</label>
          <textarea
            {...register('ng_content')}
            rows={3}
            className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
          />
        </div>
      </section>

      {/* ボタン */}
      <div className="flex justify-end space-x-4 pt-6 border-t border-gray-100">
        <button
          type="button"
          onClick={() => router.back()}
          className="px-6 py-2.5 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50 transition-colors"
        >
          キャンセル
        </button>
        <button
          type="submit"
          disabled={isSubmitting}
          className="px-10 py-2.5 rounded-md bg-indigo-600 text-white font-semibold shadow-lg shadow-indigo-200 hover:bg-indigo-700 disabled:opacity-50 transition-all"
        >
          {isSubmitting ? '保存中...' : '保存する'}
        </button>
      </div>
    </form>
  );
}
