'use client';

import { useForm, useFieldArray, Controller } from 'react-hook-form';
import { TenantConfigResponse, TenantConfigUpdateRequest, SnsLink, PartnerLink } from '@/types/api';
import ImageUpload from '@/components/ui/ImageUpload';

interface TenantConfigFormData {
  template_key: string;
  logo_url: string;
  banner_url: string;
  mv_url: string;
  mv_type: string;
  description: string;
  sns_links: SnsLink[];
  partner_links: PartnerLink[];
}

interface TenantConfigFormProps {
  initialData: TenantConfigResponse;
  onSubmit: (data: TenantConfigUpdateRequest) => Promise<void>;
  isSubmitting: boolean;
}

const SNS_PLATFORMS = [
  { value: 'twitter', label: 'Twitter / X' },
  { value: 'instagram', label: 'Instagram' },
  { value: 'line', label: 'LINE' },
  { value: 'tiktok', label: 'TikTok' },
  { value: 'youtube', label: 'YouTube' },
  { value: 'facebook', label: 'Facebook' },
];

export function TenantConfigForm({ initialData, onSubmit, isSubmitting }: TenantConfigFormProps) {
  const { register, control, handleSubmit } = useForm<TenantConfigFormData>({
    defaultValues: {
      template_key: initialData.template_key || 'default',
      logo_url: initialData.logo_url || '',
      banner_url: initialData.banner_url || '',
      mv_url: initialData.mv_url || '',
      mv_type: initialData.mv_type || 'image',
      description: initialData.description || '',
      sns_links: initialData.sns_links || [],
      partner_links: initialData.partner_links || [],
    },
  });

  const {
    fields: snsFields,
    append: appendSns,
    remove: removeSns,
  } = useFieldArray({
    control,
    name: 'sns_links',
  });

  const {
    fields: partnerFields,
    append: appendPartner,
    remove: removePartner,
  } = useFieldArray({
    control,
    name: 'partner_links',
  });

  const handleFormSubmit = (data: TenantConfigFormData) => {
    onSubmit(data);
  };

  return (
    <form
      onSubmit={handleSubmit(handleFormSubmit)}
      className="space-y-10 bg-white p-8 rounded-xl shadow-sm border border-gray-100"
    >
      {/* 1. 基本設定 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          基本設定
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">テンプレート</label>
            <select
              {...register('template_key')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="default">デフォルト</option>
              <option value="modern">モダン</option>
              <option value="classic">クラシック</option>
            </select>
          </div>
          <div className="col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-2">店舗説明文</label>
            <textarea
              {...register('description')}
              rows={4}
              placeholder="店舗の紹介文を入力してください..."
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
        </div>
      </section>

      {/* 2. ビジュアル設定 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          ビジュアル設定
        </h3>

        {/* ロゴ */}
        <div className="mb-8">
          <label className="block text-sm font-medium text-gray-700 mb-2">ロゴ画像</label>
          <p className="text-xs text-gray-500 mb-3">正方形の画像を推奨します（例: 200x200px）</p>
          <div className="flex items-start">
            <Controller
              control={control}
              name="logo_url"
              render={({ field: { value, onChange } }) => (
                <ImageUpload
                  value={value}
                  onChange={onChange}
                  directory="config"
                  className="w-32 h-32" // Square
                />
              )}
            />
          </div>
        </div>

        {/* バナー */}
        <div className="mb-8">
          <label className="block text-sm font-medium text-gray-700 mb-2">バナー画像</label>
          <p className="text-xs text-gray-500 mb-3">横長の画像を推奨します（例: 1200x400px）</p>
          <Controller
            control={control}
            name="banner_url"
            render={({ field: { value, onChange } }) => (
              <ImageUpload
                value={value}
                onChange={onChange}
                directory="config"
                className="w-full h-40 max-w-2xl" // Wide rectangle
              />
            )}
          />
        </div>

        {/* メインビジュアル (MV) */}
        <div className="mb-8">
          <div className="flex justify-between items-center mb-2">
            <label className="block text-sm font-medium text-gray-700">メインビジュアル (MV)</label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-gray-600">タイプ:</span>
              <select
                {...register('mv_type')}
                className="text-sm rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 py-1 pl-2 pr-8"
              >
                <option value="image">画像</option>
                <option value="video">動画</option>
              </select>
            </div>
          </div>
          <p className="text-xs text-gray-500 mb-3">
            サイトの顔となる大きな画像です（例: 1920x800px）
          </p>
          <Controller
            control={control}
            name="mv_url"
            render={({ field: { value, onChange } }) => (
              <ImageUpload
                value={value}
                onChange={onChange}
                directory="config"
                className="w-full h-64" // Taller wide rectangle
              />
            )}
          />
        </div>
      </section>

      {/* 3. SNSリンク */}
      <section>
        <div className="flex justify-between items-center mb-6">
          <h3 className="text-lg font-semibold text-gray-900 border-l-4 border-indigo-500 pl-3">
            SNS リンク
          </h3>
          <button
            type="button"
            onClick={() => appendSns({ platform: 'twitter', url: '', label: '' })}
            className="px-4 py-2 text-sm bg-indigo-50 text-indigo-600 rounded-md hover:bg-indigo-100 transition-colors"
          >
            + 追加
          </button>
        </div>
        <div className="space-y-4">
          {snsFields.length === 0 ? (
            <div className="p-6 bg-gray-50 rounded-lg border border-dashed border-gray-300 text-center text-gray-500 text-sm">
              SNS リンクが登録されていません
            </div>
          ) : (
            snsFields.map((field, index) => (
              <div
                key={field.id}
                className="flex items-start gap-4 p-4 bg-gray-50 rounded-lg border border-gray-200"
              >
                <div className="flex-1 grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-500 mb-1">
                      プラットフォーム
                    </label>
                    <select
                      {...register(`sns_links.${index}.platform`)}
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"
                    >
                      {SNS_PLATFORMS.map(p => (
                        <option key={p.value} value={p.value}>
                          {p.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="md:col-span-2">
                    <label className="block text-xs font-medium text-gray-500 mb-1">URL</label>
                    <input
                      type="url"
                      {...register(`sns_links.${index}.url`)}
                      placeholder="https://..."
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"
                    />
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => removeSns(index)}
                  className="mt-6 p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-colors"
                  aria-label="削除"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M6 18L18 6M6 6l12 12"
                    />
                  </svg>
                </button>
              </div>
            ))
          )}
        </div>
      </section>

      {/* 4. パートナーリンク */}
      <section>
        <div className="flex justify-between items-center mb-6">
          <h3 className="text-lg font-semibold text-gray-900 border-l-4 border-indigo-500 pl-3">
            パートナーリンク
          </h3>
          <button
            type="button"
            onClick={() => appendPartner({ name: '', url: '', logo_url: '' })}
            className="px-4 py-2 text-sm bg-indigo-50 text-indigo-600 rounded-md hover:bg-indigo-100 transition-colors"
          >
            + 追加
          </button>
        </div>
        <div className="space-y-4">
          {partnerFields.length === 0 ? (
            <div className="p-6 bg-gray-50 rounded-lg border border-dashed border-gray-300 text-center text-gray-500 text-sm">
              パートナーリンクが登録されていません
            </div>
          ) : (
            partnerFields.map((field, index) => (
              <div
                key={field.id}
                className="flex items-start gap-4 p-4 bg-gray-50 rounded-lg border border-gray-200"
              >
                <div className="flex-1 grid grid-cols-1 md:grid-cols-12 gap-4">
                  <div className="md:col-span-4">
                    <label className="block text-xs font-medium text-gray-500 mb-1">名前</label>
                    <input
                      type="text"
                      {...register(`partner_links.${index}.name`)}
                      placeholder="パートナー名"
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"
                    />
                  </div>
                  <div className="md:col-span-5">
                    <label className="block text-xs font-medium text-gray-500 mb-1">URL</label>
                    <input
                      type="url"
                      {...register(`partner_links.${index}.url`)}
                      placeholder="https://..."
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"
                    />
                  </div>
                  <div className="md:col-span-3">
                    <label className="block text-xs font-medium text-gray-500 mb-1">ロゴ</label>
                    <div className="h-10">
                      <Controller
                        control={control}
                        name={`partner_links.${index}.logo_url`}
                        render={({ field: { value, onChange } }) => (
                          <ImageUpload
                            value={value}
                            onChange={onChange}
                            directory="partners"
                            className="w-full h-10 border-gray-200"
                          />
                        )}
                      />
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => removePartner(index)}
                  className="mt-6 p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-full transition-colors"
                  aria-label="削除"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M6 18L18 6M6 6l12 12"
                    />
                  </svg>
                </button>
              </div>
            ))
          )}
        </div>
      </section>

      {/* Buttons */}
      <div className="flex justify-end space-x-4 pt-6 border-t border-gray-100 sticky bottom-0 bg-white/90 backdrop-blur-sm p-4 -mx-8 -mb-8 rounded-b-xl">
        <button
          type="submit"
          disabled={isSubmitting}
          className="px-10 py-2.5 rounded-md bg-indigo-600 text-white font-semibold shadow-lg shadow-indigo-200 hover:bg-indigo-700 disabled:opacity-50 transition-all transform active:scale-95"
        >
          {isSubmitting ? '保存中...' : '設定を保存する'}
        </button>
      </div>
    </form>
  );
}
