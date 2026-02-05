'use client';

import { useForm, useFieldArray } from 'react-hook-form';
import { SiteConfigResponse, SiteConfigUpdateRequest, SnsLink, PartnerLink } from '@/types/api';

interface SiteConfigFormData {
  templateKey: string;
  logoUrl: string;
  bannerUrl: string;
  mvUrl: string;
  mvType: string;
  description: string;
  snsLinks: SnsLink[];
  partnerLinks: PartnerLink[];
}

interface SiteConfigFormProps {
  initialData: SiteConfigResponse;
  onSubmit: (data: SiteConfigUpdateRequest) => Promise<void>;
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

export function SiteConfigForm({ initialData, onSubmit, isSubmitting }: SiteConfigFormProps) {
  const { register, control, handleSubmit } = useForm<SiteConfigFormData>({
    defaultValues: {
      templateKey: initialData.templateKey || 'default',
      logoUrl: initialData.logoUrl || '',
      bannerUrl: initialData.bannerUrl || '',
      mvUrl: initialData.mvUrl || '',
      mvType: initialData.mvType || 'image',
      description: initialData.description || '',
      snsLinks: initialData.snsLinks || [],
      partnerLinks: initialData.partnerLinks || [],
    },
  });

  const {
    fields: snsFields,
    append: appendSns,
    remove: removeSns,
  } = useFieldArray({
    control,
    name: 'snsLinks',
  });

  const {
    fields: partnerFields,
    append: appendPartner,
    remove: removePartner,
  } = useFieldArray({
    control,
    name: 'partnerLinks',
  });

  const handleFormSubmit = (data: SiteConfigFormData) => {
    onSubmit(data);
  };

  return (
    <form
      onSubmit={handleSubmit(handleFormSubmit)}
      className="space-y-8 bg-white p-8 rounded-xl shadow-sm border border-gray-100"
    >
      {/* 1. 基本設定 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          基本設定
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">テンプレート</label>
            <select
              {...register('templateKey')}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            >
              <option value="default">デフォルト</option>
              <option value="modern">モダン</option>
              <option value="classic">クラシック</option>
            </select>
          </div>
        </div>
      </section>

      {/* 2. ビジュアル設定 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          ビジュアル設定
        </h3>
        <div className="grid grid-cols-1 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ロゴ URL</label>
            <input
              type="url"
              {...register('logoUrl')}
              placeholder="https://example.com/logo.png"
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">バナー URL</label>
            <input
              type="url"
              {...register('bannerUrl')}
              placeholder="https://example.com/banner.png"
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
            />
          </div>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="md:col-span-3">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                メインビジュアル URL
              </label>
              <input
                type="url"
                {...register('mvUrl')}
                placeholder="https://example.com/main-visual.png"
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">MV タイプ</label>
              <select
                {...register('mvType')}
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              >
                <option value="image">画像</option>
                <option value="video">動画</option>
              </select>
            </div>
          </div>
        </div>
      </section>

      {/* 3. 店舗説明 */}
      <section>
        <h3 className="text-lg font-semibold text-gray-900 mb-6 border-l-4 border-indigo-500 pl-3">
          店舗説明
        </h3>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">説明文</label>
          <textarea
            {...register('description')}
            rows={5}
            placeholder="店舗の紹介文を入力してください..."
            className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
          />
        </div>
      </section>

      {/* 4. SNSリンク */}
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
            <p className="text-gray-500 text-sm">
              SNS リンクがありません。「+ 追加」で追加できます。
            </p>
          ) : (
            snsFields.map((field, index) => (
              <div key={field.id} className="flex items-start gap-4 p-4 bg-gray-50 rounded-lg">
                <div className="flex-1 grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      プラットフォーム
                    </label>
                    <select
                      {...register(`snsLinks.${index}.platform`)}
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    >
                      {SNS_PLATFORMS.map(p => (
                        <option key={p.value} value={p.value}>
                          {p.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">URL</label>
                    <input
                      type="url"
                      {...register(`snsLinks.${index}.url`)}
                      placeholder="https://..."
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      表示名（任意）
                    </label>
                    <input
                      type="text"
                      {...register(`snsLinks.${index}.label`)}
                      placeholder="@username"
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    />
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => removeSns(index)}
                  className="mt-6 p-2 text-red-500 hover:text-red-700 hover:bg-red-50 rounded transition-colors"
                  aria-label={`SNS リンク ${index + 1} を削除`}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                    />
                  </svg>
                </button>
              </div>
            ))
          )}
        </div>
      </section>

      {/* 5. パートナーリンク */}
      <section>
        <div className="flex justify-between items-center mb-6">
          <h3 className="text-lg font-semibold text-gray-900 border-l-4 border-indigo-500 pl-3">
            パートナーリンク
          </h3>
          <button
            type="button"
            onClick={() => appendPartner({ name: '', url: '', logoUrl: '' })}
            className="px-4 py-2 text-sm bg-indigo-50 text-indigo-600 rounded-md hover:bg-indigo-100 transition-colors"
          >
            + 追加
          </button>
        </div>
        <div className="space-y-4">
          {partnerFields.length === 0 ? (
            <p className="text-gray-500 text-sm">
              パートナーリンクがありません。「+ 追加」で追加できます。
            </p>
          ) : (
            partnerFields.map((field, index) => (
              <div key={field.id} className="flex items-start gap-4 p-4 bg-gray-50 rounded-lg">
                <div className="flex-1 grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">名前</label>
                    <input
                      type="text"
                      {...register(`partnerLinks.${index}.name`)}
                      placeholder="パートナー名"
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">URL</label>
                    <input
                      type="url"
                      {...register(`partnerLinks.${index}.url`)}
                      placeholder="https://..."
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      ロゴ URL（任意）
                    </label>
                    <input
                      type="url"
                      {...register(`partnerLinks.${index}.logoUrl`)}
                      placeholder="https://..."
                      className="w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                    />
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => removePartner(index)}
                  className="mt-6 p-2 text-red-500 hover:text-red-700 hover:bg-red-50 rounded transition-colors"
                  aria-label={`パートナーリンク ${index + 1} を削除`}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                    />
                  </svg>
                </button>
              </div>
            ))
          )}
        </div>
      </section>

      {/* Buttons */}
      <div className="flex justify-end space-x-4 pt-6 border-t border-gray-100">
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
