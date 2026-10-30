'use client';

import { useState } from 'react';
import { Loader2Icon } from 'lucide-react';
import { useRouter } from 'next/navigation';
import {
  CreateStoreRequest,
  isStoreDomain,
  storeShortNameError,
  adminStoreApi,
} from '@/entities/store';
import { useStoreContext } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import { Button, Card, CardContent, Input, Label } from '@/shared/ui';
import { notify } from '@/shared/notify';

export default function CreateStorePage() {
  const router = useRouter();
  const { reload: reloadStores } = useStoreContext();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<CreateStoreRequest>({
    name: '',
    short_name: '',
    domain: '',
  });

  const [errors, setErrors] = useState<Partial<CreateStoreRequest>>({});

  const validateForm = (): boolean => {
    const newErrors: Partial<CreateStoreRequest> = {};

    if (!formData.name.trim()) {
      newErrors.name = '店舗名は必須です';
    }

    const shortNameError = storeShortNameError(formData.short_name);
    if (shortNameError) {
      newErrors.short_name = shortNameError;
    }

    if (!formData.domain.trim()) {
      newErrors.domain = 'ドメインは必須です';
    } else {
      if (!isStoreDomain(formData.domain)) {
        newErrors.domain = 'ドメイン形式が正しくありません';
      }

      const domain = formData.domain.toLowerCase();
      if (domain.startsWith('api.') || domain === 'api') {
        newErrors.domain = 'api 関連のドメインは使用できません';
      }
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
      await adminStoreApi.create({ ...formData, short_name: formData.short_name.trim() });
      reloadStores();
      notify.success('店舗を作成しました。店舗一覧に戻ります');
      router.push('/admin/stores');
    } catch (error: unknown) {
      if (
        typeof error === 'object' &&
        error !== null &&
        'response' in error &&
        typeof error.response === 'object' &&
        error.response !== null &&
        'data' in error.response &&
        typeof error.response.data === 'object' &&
        error.response.data !== null &&
        'errors' in error.response.data
      ) {
        setErrors(error.response.data.errors as Partial<CreateStoreRequest>);
      }
      notify.error(getApiErrorMessage(error, '店舗作成に失敗しました。入力内容をご確認ください'));
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
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">店舗作成</h1>
        <p className="text-sm text-muted-foreground mt-1">
          新しい店舗の基本情報を入力してください。ドメインは独立サイトへのアクセスに使用されます。
        </p>
      </div>

      <Card>
        <CardContent>
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

            {/* 略称 */}
            <div className="grid gap-2">
              <Label htmlFor="short_name">
                略称 <span className="text-destructive-strong">*</span>
              </Label>
              <Input
                type="text"
                name="short_name"
                id="short_name"
                value={formData.short_name}
                onChange={e => handleInputChange('short_name', e.target.value)}
                aria-invalid={!!errors.short_name}
                placeholder="例：新宿"
              />
              {errors.short_name && (
                <p className="text-sm text-destructive-strong">{errors.short_name}</p>
              )}
              <p className="text-sm text-muted-foreground">
                1〜3 文字で入力してください。店舗の切替ボタンに表示されます。
              </p>
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
              {errors.domain && <p className="text-sm text-destructive-strong">{errors.domain}</p>}
              <p className="text-sm text-muted-foreground">
                完全なドメイン名を入力してください（例：company.shop.example.org）
              </p>
            </div>

            <div className="flex justify-end space-x-3">
              <Button type="button" variant="outline" onClick={() => router.push('/admin/stores')}>
                キャンセル
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2Icon className="animate-spin" />
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
  );
}
