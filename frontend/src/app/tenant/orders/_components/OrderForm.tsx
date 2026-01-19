'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';

export interface OrderFormData {
  storeName: string;
  receptionistId: string;
  businessDate: string;
  arrivalStartTime: string;
  arrivalEndTime: string;
  customerName: string;
  phoneNumber: string;
  phoneNumber2: string;
  address: string;
  buildingName: string;
  classification: string;
  landmark: string;
  hasPet: boolean;
  girlId: string;
  courseMinutes: number;
  extensionMinutes: number;
  options: string[];
  discountName: string;
  manualDiscount: number;
  carrier: string;
  mediaName: string;
  usedPoints: number;
  manualGrantPoints: number;
  remarks: string;
  girlDriverMessage: string;
  ngType: string;
  ngContent: string;
}

interface OrderFormProps {
  initialData?: Partial<OrderFormData>;
  onSubmit: (data: OrderFormData) => void;
  isSubmitting?: boolean;
}

export function OrderForm({ initialData, onSubmit, isSubmitting }: OrderFormProps) {
  const router = useRouter();
  const { register, handleSubmit } = useForm<OrderFormData>({
    defaultValues: {
      businessDate: new Date().toISOString().split('T')[0],
      courseMinutes: 60,
      extensionMinutes: 0,
      manualDiscount: 0,
      usedPoints: 0,
      manualGrantPoints: 0,
      hasPet: false,
      ngType: 'NG無し',
      ...initialData,
    },
  });

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="space-y-8 bg-white p-8 rounded-xl shadow-sm border border-gray-100"
    >
      {/* 1. 基本情報 Section */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          基本情報
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">店舗名</label>
            <select
              {...register('storeName')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="沼津H">沼津H</option>
              <option value="横浜F">横浜F</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">受付</label>
            <select
              {...register('receptionistId')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="">－－－</option>
              <option value="1">なほ</option>
              <option value="2">松本</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">営業日</label>
            <input
              type="date"
              {...register('businessDate')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>

        <div className="mt-6">
          <label className="block text-sm font-medium text-gray-700 mb-1">到着予定時刻</label>
          <div className="flex items-center space-x-2">
            <input
              type="time"
              {...register('arrivalStartTime')}
              className="rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
            <span className="text-gray-500">～</span>
            <input
              type="time"
              {...register('arrivalEndTime')}
              className="rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
      </section>

      {/* 2. お客様情報 Section */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          お客様情報
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">お客様名</label>
            <input
              type="text"
              {...register('customerName')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">電話番号</label>
            <input
              type="text"
              {...register('phoneNumber')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">住所</label>
            <input
              type="text"
              {...register('address')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">建物</label>
            <input
              type="text"
              {...register('buildingName')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">区分</label>
            <select
              {...register('classification')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="ーー">ーー</option>
              <option value="自宅">自宅</option>
              <option value="ラブホ">ラブホ</option>
              <option value="ビジホ">ビジホ</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ペット有無</label>
            <select
              {...register('hasPet')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="false">なし</option>
              <option value="true">あり</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">目印</label>
            <input
              type="text"
              {...register('landmark')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
      </section>

      {/* 3. コース・料金 Section */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          コース・料金
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">女の子名</label>
            <input
              type="text"
              {...register('girlId')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              placeholder="フリー"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ｺｰｽ(分)</label>
            <select
              {...register('courseMinutes')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="60">60</option>
              <option value="90">90</option>
              <option value="120">120</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">延長</label>
            <input
              type="number"
              {...register('extensionMinutes')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">割引</label>
            <select
              {...register('discountName')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="">なし</option>
              <option value="一番最初割">一番最初割</option>
            </select>
          </div>
        </div>
      </section>

      {/* 4. その他 Section */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          その他
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">備考</label>
            <textarea
              {...register('remarks')}
              rows={3}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              女の子、ドライバー伝言
            </label>
            <textarea
              {...register('girlDriverMessage')}
              rows={3}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
      </section>

      {/* Buttons */}
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
          {isSubmitting ? '登録中...' : '登録する'}
        </button>
      </div>
    </form>
  );
}
