'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/entities/user';
import { Tenant, UpdateTenantRequest, centralTenantApi } from '@/entities/tenant';
import toast from 'react-hot-toast';

export default function EditTenantPage() {
  const id = useParams<{ id: string }>()?.id;
  const router = useRouter();
  const { logout } = useAuth();

  const [saving, setSaving] = useState(false);
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState<UpdateTenantRequest>({
    name: '',
    email: '',
  });
  const [errors, setErrors] = useState<Partial<UpdateTenantRequest>>({});

  const loadTenant = useCallback(
    async (tenantId: string) => {
      if (!id) {
        toast.error('店舗情報の取得できませんでした');
        return;
      }
      try {
        setLoading(true);
        const res = await centralTenantApi.getById(tenantId);
        const t = res as unknown as Tenant;
        setTenant(t);
        setFormData({
          name: t.name,
          email: t.email,
        });
      } catch (e) {
        console.error('Error loading tenant:', e);
        toast.error('店舗情報の取得に失敗しました');
      } finally {
        setLoading(false);
      }
    },
    [id]
  );

  useEffect(() => {
    if (id) {
      loadTenant(id);
    }
  }, [id, loadTenant]);

  const validate = (): boolean => {
    const next: Partial<UpdateTenantRequest> = {};
    if (!formData.name.trim()) next.name = '店舗名は必須です';
    if (!formData.email.trim()) next.email = 'メールアドレスは必須です';
    else if (!/^([^\s@])+@([^\s@])+\.[^\s@]+$/.test(formData.email))
      next.email = 'メールアドレスの形式が正しくありません';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!tenant) return;
    if (!validate()) return;
    setSaving(true);
    try {
      await centralTenantApi.update(tenant.id, formData);
      toast.success('店舗情報を更新しました');
      router.push('/central/tenants');
    } catch (err: any) {
      if (err.response?.data?.errors) setErrors(err.response.data.errors);
      toast.error('更新に失敗しました。入力内容をご確認ください');
    } finally {
      setSaving(false);
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
              <h1 className="text-xl font-semibold text-gray-900">店舗編集</h1>
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

      {/* メイン */}
      <div className="max-w-3xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="bg-white shadow rounded-lg">
            <div className="px-4 py-5 sm:p-6">
              <div className="mb-6">
                <h3 className="text-lg leading-6 font-medium text-gray-900">店舗情報</h3>
                <p className="mt-1 text-sm text-gray-500">
                  店舗の基本情報を編集します。ドメインは現在変更できません。
                </p>
              </div>

              <form onSubmit={handleSave} className="space-y-6">
                {/* 店舗名 */}
                <div>
                  <label htmlFor="name" className="block text-sm font-medium text-gray-700">
                    店舗名 <span className="text-red-500">*</span>
                  </label>
                  <div className="mt-1">
                    <input
                      id="name"
                      type="text"
                      value={formData.name}
                      onChange={e => setFormData(p => ({ ...p, name: e.target.value }))}
                      className={`shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md ${
                        errors.name ? 'border-red-300' : ''
                      }`}
                    />
                    {errors.name && <p className="mt-2 text-sm text-red-600">{errors.name}</p>}
                  </div>
                </div>

                {/* 連絡用メール */}
                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                    連絡用メール <span className="text-red-500">*</span>
                  </label>
                  <div className="mt-1">
                    <input
                      id="email"
                      type="email"
                      value={formData.email}
                      onChange={e => setFormData(p => ({ ...p, email: e.target.value }))}
                      className={`shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md ${
                        errors.email ? 'border-red-300' : ''
                      }`}
                    />
                    {errors.email && <p className="mt-2 text-sm text-red-600">{errors.email}</p>}
                  </div>
                </div>

                {/* ドメイン（読み取り専用） */}
                {tenant && (
                  <div className="bg-gray-50 rounded-lg p-4">
                    <h4 className="text-sm font-medium text-gray-900 mb-2">関連ドメイン</h4>
                    {tenant.domains && tenant.domains.length > 0 ? (
                      <ul className="list-disc ml-6 text-sm text-gray-700">
                        {tenant.domains.map(d => (
                          <li key={d} className="break-all">
                            {d}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-sm text-gray-500">ドメインは設定されていません</p>
                    )}
                  </div>
                )}

                {/* 操作 */}
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
                    disabled={saving}
                    className="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {saving ? '保存中...' : '保存'}
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
