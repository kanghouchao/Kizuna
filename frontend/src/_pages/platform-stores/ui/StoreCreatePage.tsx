'use client';

import { useState, useCallback } from 'react';
import { useAuth } from '@/entities/user';
import { useRouter } from 'next/navigation';
import { CreateStoreRequest, platformStoreApi } from '@/entities/store';
import { Button, Card, CardContent, Input, Label } from '@/shared/ui';
import toast from 'react-hot-toast';

export default function CreateStorePage() {
  const { logout } = useAuth();
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<CreateStoreRequest>({
    name: '',
    domain: '',
    email: '',
  });

  const [errors, setErrors] = useState<Partial<CreateStoreRequest>>({});

  const validateForm = (): boolean => {
    const newErrors: Partial<CreateStoreRequest> = {};

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
      await platformStoreApi.create(formData);
      toast.success('店舗を作成しました。店舗一覧に戻ります');
      router.push('/platform/stores');
    } catch (error: any) {
      if (error.response?.data?.errors) {
        setErrors(error.response.data.errors);
      }
      toast.error('店舗作成に失敗しました。入力内容をご確認ください');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleInputChange = (field: keyof CreateStoreRequest, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }
  };

  return (
    <div className="min-h-screen bg-background">
      {/* ナビゲーションバー */}
      <nav className="bg-card shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center space-x-4">
              <Button
                variant="ghost"
                size="sm"
                className="text-primary-strong"
                onClick={() => router.push('/platform/stores')}
              >
                ← 店舗一覧に戻る
              </Button>
              <h1 className="text-xl font-semibold text-foreground">店舗作成</h1>
            </div>
            <div className="flex items-center space-x-4">
              <span className="text-sm text-muted-foreground">ようこそ、someone さん</span>
              <Button variant="ghost" size="sm" onClick={logout}>
                ログアウト
              </Button>
            </div>
          </div>
        </div>
      </nav>

      {/* メインコンテンツ */}
      <div className="max-w-3xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <Card>
            <CardContent>
              <div className="mb-6">
                <h3 className="text-lg leading-6 font-medium text-foreground">店舗情報</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  新しい店舗の基本情報を入力してください。ドメインは独立サイトへのアクセスに使用されます。
                </p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-6">
                {/* 店舗名 */}
                <div className="grid gap-2">
                  <Label htmlFor="name">
                    店舗名 <span className="text-destructive-strong">*</span>
                  </Label>
                  <Input
                    type="text"
                    name="name"
                    id="name"
                    value={formData.name}
                    onChange={e => handleInputChange('name', e.target.value)}
                    aria-invalid={!!errors.name}
                    placeholder="例：ABC株式会社"
                  />
                  {errors.name && <p className="text-sm text-destructive-strong">{errors.name}</p>}
                </div>

                {/* 域名 */}
                <div className="grid gap-2">
                  <Label htmlFor="domain">
                    ドメイン <span className="text-destructive-strong">*</span>
                  </Label>
                  <Input
                    type="text"
                    name="domain"
                    id="domain"
                    value={formData.domain}
                    onChange={e => handleInputChange('domain', e.target.value.toLowerCase())}
                    aria-invalid={!!errors.domain}
                    placeholder="example.com"
                  />
                  {errors.domain && (
                    <p className="text-sm text-destructive-strong">{errors.domain}</p>
                  )}
                  <p className="text-sm text-muted-foreground">
                    完全なドメイン名を入力してください（例：company.shop.example.org）
                  </p>
                </div>

                {/* 連絡用メール */}
                <div className="grid gap-2">
                  <Label htmlFor="email">
                    連絡用メール <span className="text-destructive-strong">*</span>
                  </Label>
                  <Input
                    type="email"
                    name="email"
                    id="email"
                    value={formData.email}
                    onChange={e => handleInputChange('email', e.target.value)}
                    aria-invalid={!!errors.email}
                    placeholder="contact@abc-company.com"
                  />
                  {errors.email && (
                    <p className="text-sm text-destructive-strong">{errors.email}</p>
                  )}
                </div>

                {/* 预览信息 */}
                <div className="rounded-lg border p-4">
                  <h4 className="text-sm font-medium text-foreground mb-2">プレビュー情報</h4>
                  <dl className="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
                    <div>
                      <dt className="text-sm font-medium text-muted-foreground">店舗名</dt>
                      <dd className="mt-1 text-sm text-foreground">{formData.name || '未入力'}</dd>
                    </div>
                    <div>
                      <dt className="text-sm font-medium text-muted-foreground">
                        アクセスドメイン
                      </dt>
                      <dd className="mt-1 text-sm text-foreground">
                        {formData.domain || '未入力'}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-sm font-medium text-muted-foreground">連絡用メール</dt>
                      <dd className="mt-1 text-sm text-foreground">{formData.email || '未入力'}</dd>
                    </div>
                  </dl>
                </div>

                {/* 提交按钮 */}
                <div className="flex justify-end space-x-3">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => router.push('/platform/stores')}
                  >
                    キャンセル
                  </Button>
                  <Button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? (
                      <>
                        <svg className="animate-spin" fill="none" viewBox="0 0 24 24">
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
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
