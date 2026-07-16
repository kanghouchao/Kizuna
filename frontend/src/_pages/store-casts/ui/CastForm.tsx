'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { CastFieldDefinitionResponse, castFieldDefinitionApi } from '@/entities/cast';
import { ImageUpload } from '@/shared/ui';
import { useManagedList } from '@/shared/lib';

/** キャストフォームのデータ型 */
export interface CastFormData {
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
  // 活きた定義に対応する入力欄のみ登録される（孤児キーは含まれない）
  custom_fields?: Record<string, string>;
}

interface CastFormProps {
  /** 編集時の初期データ */
  initialData?: Partial<CastFormData>;
  /** 編集時の既存カスタムフィールド値（活きた定義の分のみ動的欄の初期値に使う。孤児値は描画しない） */
  existingCustomFields?: Record<string, string>;
  /** フォーム送信時のコールバック */
  onSubmit: (data: CastFormData) => void;
  /** 送信中フラグ */
  isSubmitting?: boolean;
}

/** キャスト登録・編集フォームコンポーネント */
export function CastForm({
  initialData,
  existingCustomFields,
  onSubmit,
  isSubmitting,
}: CastFormProps) {
  const router = useRouter();
  const { register, handleSubmit, setValue, watch } = useForm<CastFormData>({
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

  // カスタムフィールドの動的欄は編集時のみ表示する（値の入力自体は既存 PUT のまま、
  // 作成時は付与するキャストがまだ無いため定義取得自体を行わない）。
  const isEdit = initialData !== undefined;
  const { items: definitions, isLoading: isLoadingDefinitions } =
    useManagedList<CastFieldDefinitionResponse>(
      () => (isEdit ? castFieldDefinitionApi.list() : Promise.resolve([])),
      'カスタムフィールド定義の取得に失敗しました'
    );

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
              bucket="public"
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

      {/* カスタムフィールド（編集時のみ。作成時はキャストがまだ無いため値を付与できない） */}
      {isEdit && (
        <section>
          <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-blue-600 pl-3">
            カスタムフィールド
          </h3>
          {isLoadingDefinitions ? (
            <div className="p-6 text-center text-sm text-gray-500">読み込み中...</div>
          ) : definitions.length === 0 ? (
            <div className="p-6 bg-gray-50 rounded-lg border border-dashed border-gray-300 text-center text-gray-500 text-sm">
              カスタムフィールドは登録されていません
            </div>
          ) : (
            <div className="space-y-4">
              {definitions.map(definition => (
                <div key={definition.key}>
                  <label
                    htmlFor={`cast-custom-field-${definition.key}`}
                    className="block text-sm font-medium text-gray-700 mb-1"
                  >
                    {definition.label}
                  </label>
                  <input
                    id={`cast-custom-field-${definition.key}`}
                    type="text"
                    // 自身が所有するキーのみ初期値に採用する。プレーンオブジェクトの
                    // ブラケットアクセスは 'constructor' 等の継承プロパティを拾うため hasOwn で防ぐ。
                    defaultValue={
                      existingCustomFields && Object.hasOwn(existingCustomFields, definition.key)
                        ? existingCustomFields[definition.key]
                        : ''
                    }
                    {...register(`custom_fields.${definition.key}`)}
                    className="w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
                  />
                </div>
              ))}
            </div>
          )}
        </section>
      )}

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
