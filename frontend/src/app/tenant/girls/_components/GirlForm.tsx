'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import ImageUpload from '@/components/ui/ImageUpload';

/** キャストフォームのデータ型 */
export interface GirlFormData {
  name: string;
  status: string;
  photo_url: string;
  introduction: string;
  age: number | null;
  height: number | null;
  bust: number | null;
  waist: number | null;
  hip: number | null;
  display_order: number;
}

interface GirlFormProps {
  /** 編集時の初期データ */
  initialData?: Partial<GirlFormData>;
  /** フォーム送信時のコールバック */
  onSubmit: (data: GirlFormData) => void;
  /** 送信中フラグ */
  isSubmitting?: boolean;
}

/** キャスト登録・編集フォームコンポーネント */
export function GirlForm({ initialData, onSubmit, isSubmitting }: GirlFormProps) {
  const router = useRouter();
  const { register, handleSubmit, setValue, watch } = useForm<GirlFormData>({
    defaultValues: {
      name: '',
      status: 'ACTIVE',
      photo_url: '',
      introduction: '',
      age: null,
      height: null,
      bust: null,
      waist: null,
      hip: null,
      display_order: 0,
      ...initialData,
    },
  });

  const photoUrl = watch('photo_url');

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
        <div className="flex gap-8">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">写真</label>
            <ImageUpload
              value={photoUrl}
              onChange={url => setValue('photo_url', url)}
              directory="girls"
            />
          </div>
          <div className="flex-1 space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">名前 *</label>
              <input
                type="text"
                {...register('name', { required: true })}
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">ステータス</label>
              <select
                {...register('status')}
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              >
                <option value="ACTIVE">有効</option>
                <option value="INACTIVE">無効</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">表示順</label>
              <input
                type="number"
                {...register('display_order', { valueAsNumber: true })}
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              />
            </div>
          </div>
        </div>
      </section>

      {/* プロフィール */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          プロフィール
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">年齢</label>
            <input
              type="number"
              {...register('age', { valueAsNumber: true })}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">身長 (cm)</label>
            <input
              type="number"
              {...register('height', { valueAsNumber: true })}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">バスト (cm)</label>
            <input
              type="number"
              {...register('bust', { valueAsNumber: true })}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ウエスト (cm)</label>
            <input
              type="number"
              {...register('waist', { valueAsNumber: true })}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ヒップ (cm)</label>
            <input
              type="number"
              {...register('hip', { valueAsNumber: true })}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
      </section>

      {/* 自己紹介 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          自己紹介
        </h3>
        <textarea
          {...register('introduction')}
          rows={4}
          className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
          placeholder="自己紹介を入力してください..."
        />
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
