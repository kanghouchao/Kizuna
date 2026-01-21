'use client';

import { useState, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { useRouter } from 'next/navigation';
import { centralApi } from '@/services/central/api';
import { CreateTenantRequest } from '@/types/api';
import toast from 'react-hot-toast';

export default function CreateTenantPage() {
  const { logout } = useAuth();
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<CreateTenantRequest>({
    name: '',
    domain: '',
    email: '',
  });

  const [errors, setErrors] = useState<Partial<CreateTenantRequest>>({});

  const validateForm = (): boolean => {
    const newErrors: Partial<CreateTenantRequest> = {};

    if (!formData.name.trim()) {
      newErrors.name = '店舗名は必須です';
    }

    if (!formData.domain.trim()) {
      newErrors.domain = 'ドメインは必須です';
    } else {
      const domainRegex = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$/;
      if (!domainRegex.test(formData.domain)) {
        newErrors.domain = 'ドメイン形式が正しくありません';
      }

      const domain = formData.domain.toLowerCase();
      if (domain.startsWith('api.') || domain === 'api') {
        newErrors.domain = 'api 関連のドメインは使用できません';
      }
    }

    if (!formData.email.trim()) {
      newErrors.email = 'メールアドレスは必須です';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = 'メールアドレスの形式が正しくありません';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);

    try {
      await centralApi.create(formData);
      toast.success('店舗を作成しました。店舗一覧に戻ります');
      router.push('/central/tenants');
    } catch (error: any) {
      if (error.response?.data?.errors) {
        setErrors(error.response.data.errors);
      }
      toast.error('店舗作成に失敗しました。入力内容をご確認ください');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleInputChange = (field: keyof CreateTenantRequest, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* ナビゲーションバー */}
      <nav className="bg-white shadow">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center space-x-4">
              <button
                onClick={() => router.push('/central/tenants')}
                className="text-indigo-600 hover:text-indigo-800 text-sm font-medium"
              >
                ← 店舗一覧に戻る
              </button>
              <h1 className="text-xl font-semibold text-gray-900">店舗作成</h1>
            </div>
            <div className="flex items-center space-x-4">
              <span className="text-sm text-gray-700">ようこそ、someone さん</span>
              <button
                onClick={logout}
                className="text-gray-700 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium"
              >
                ログアウト
              </button>
            </div>
          </div>
        </div>
      </nav>

      {/* メインコンテンツ */}
      <div className="max-w-3xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="bg-white shadow rounded-lg">
            <div className="px-4 py-5 sm:p-6">
              <div className="mb-6">
                <h3 className="text-lg leading-6 font-medium text-gray-900">店舗情報</h3>
                <p className="mt-1 text-sm text-gray-500">
                  新しい店舗の基本情報を入力してください。ドメインは独立サイトへのアクセスに使用されます。
                </p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-6">
                {/* 店舗名 */}
                <div>
                  <label htmlFor="name" className="block text-sm font-medium text-gray-700">
                    店舗名 <span className="text-red-500">*</span>
                  </label>
                  <div className="mt-1">
                    <input
                      type="text"
                      name="name"
                      id="name"
                      value={formData.name}
                      onChange={e => handleInputChange('name', e.target.value)}
                      className={`shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md ${
                        errors.name ? 'border-red-300' : ''
                      }`}
                      placeholder="例：ABC株式会社"
                    />
                    {errors.name && <p className="mt-2 text-sm text-red-600">{errors.name}</p>}
                  </div>
                </div>

                {/* 域名 */}
                <div>
                  <label htmlFor="domain" className="block text-sm font-medium text-gray-700">
                    ドメイン <span className="text-red-500">*</span>
                  </label>
                  <div className="mt-1">
                    <input
                      type="text"
                      name="domain"
                      id="domain"
                      value={formData.domain}
                      onChange={e => handleInputChange('domain', e.target.value.toLowerCase())}
                      className={`focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md ${
                        errors.domain ? 'border-red-300' : ''
                      }`}
                      placeholder="example.com"
                    />
                  </div>
                  {errors.domain && <p className="mt-2 text-sm text-red-600">{errors.domain}</p>}
                  <p className="mt-2 text-sm text-gray-500">
                    完全なドメイン名を入力してください（例：company.shop.example.org）
                  </p>
                </div>

                {/* 連絡用メール */}
                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                    連絡用メール <span className="text-red-500">*</span>
                  </label>
                  <div className="mt-1">
                    <input
                      type="email"
                      name="email"
                      id="email"
                      value={formData.email}
                      onChange={e => handleInputChange('email', e.target.value)}
                      className={`shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md ${
                        errors.email ? 'border-red-300' : ''
                      }`}
                      placeholder="contact@abc-company.com"
                    />
                    {errors.email && <p className="mt-2 text-sm text-red-600">{errors.email}</p>}
                  </div>
                </div>

                {/* 预览信息 */}
                <div className="bg-gray-50 rounded-lg p-4">
                  <h4 className="text-sm font-medium text-gray-900 mb-2">プレビュー情報</h4>
                  <dl className="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
                    <div>
                      <dt className="text-sm font-medium text-gray-500">店舗名</dt>
                      <dd className="mt-1 text-sm text-gray-900">{formData.name || '未入力'}</dd>
                    </div>
                    <div>
                      <dt className="text-sm font-medium text-gray-500">アクセスドメイン</dt>
                      <dd className="mt-1 text-sm text-gray-900">{formData.domain || '未入力'}</dd>
                    </div>
                    <div>
                      <dt className="text-sm font-medium text-gray-500">連絡用メール</dt>
                      <dd className="mt-1 text-sm text-gray-900">{formData.email || '未入力'}</dd>
                    </div>
                  </dl>
                </div>

                {/* 提交按钮 */}
                <div className="flex justify-end space-x-3">
                  <button
                    type="button"
                    onClick={() => router.push('/central/tenants')}
                    className="bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                  >
                    キャンセル
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {isSubmitting ? (
                      <>
                        <svg
                          className="animate-spin -ml-1 mr-3 h-5 w-5 text-white"
                          fill="none"
                          viewBox="0 0 24 24"
                        >
                          <circle
                            className="opacity-25"
                            cx="12"
                            cy="12"
                            r="10"
                            stroke="currentColor"
                            strokeWidth="4"
                          ></circle>
                          <path
                            className="opacity-75"
                            fill="currentColor"
                            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                          ></path>
                        </svg>
                        作成中...
                      </>
                    ) : (
                      '店舗を作成'
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
