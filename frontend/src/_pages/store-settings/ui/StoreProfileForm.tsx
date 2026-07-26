'use client';

import { useForm, useFieldArray } from 'react-hook-form';
import {
  getAllTemplateMetas,
  getTemplateMeta,
  PartnerLink,
  SnsLink,
  StoreProfileResponse,
  StoreProfileUpdateRequest,
} from '@/entities/store-profile';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  ImageUpload,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

interface StoreProfileFormData {
  template_key: string;
  logo_url: string;
  banner_url: string;
  mv_url: string;
  mv_type: string;
  description: string;
  catch_copy: string;
  address: string;
  phone: string;
  business_hours: string;
  pricing_description: string;
  custom_texts: Record<string, string>;
  sns_links: SnsLink[];
  partner_links: PartnerLink[];
}

interface StoreProfileFormProps {
  initialData: StoreProfileResponse;
  onSubmit: (data: StoreProfileUpdateRequest) => Promise<void>;
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

export function StoreProfileForm({ initialData, onSubmit, isSubmitting }: StoreProfileFormProps) {
  const form = useForm<StoreProfileFormData>({
    defaultValues: {
      template_key: initialData.template_key || 'default',
      logo_url: initialData.logo_url || '',
      banner_url: initialData.banner_url || '',
      mv_url: initialData.mv_url || '',
      mv_type: initialData.mv_type || 'image',
      description: initialData.description || '',
      catch_copy: initialData.catch_copy || '',
      address: initialData.address || '',
      phone: initialData.phone || '',
      business_hours: initialData.business_hours || '',
      pricing_description: initialData.pricing_description || '',
      custom_texts: initialData.custom_texts || {},
      sns_links: initialData.sns_links || [],
      partner_links: initialData.partner_links || [],
    },
  });
  const { register, control, handleSubmit, watch } = form;

  const templateKey = watch('template_key');
  const textSlots = getTemplateMeta(templateKey).textSlots;

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

  const handleFormSubmit = (data: StoreProfileFormData) => {
    // 現模版に無い既存 key を潰さないよう、保存済み custom_texts にマージする
    onSubmit({
      ...data,
      custom_texts: { ...(initialData.custom_texts || {}), ...(data.custom_texts || {}) },
    });
  };

  return (
    <Form {...form}>
      <form
        onSubmit={handleSubmit(handleFormSubmit)}
        className="space-y-10 bg-card text-card-foreground p-8 rounded-xl shadow-sm border"
      >
        {/* 1. 基本設定 */}
        <section>
          <h3 className="text-lg font-semibold text-foreground mb-6 border-l-4 border-primary pl-3">
            基本設定
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            <div className="col-span-2 grid gap-2">
              <Label>テンプレート</Label>
              {/* サムネイル付き予覧カードで模版を選択。選択中の key は既存 watch で
                  模版テキスト欄の描画に連動する（追加配線は不要） */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {getAllTemplateMetas().map(meta => {
                  const selected = templateKey === meta.key;
                  return (
                    <label
                      key={meta.key}
                      className={`block rounded-lg border p-3 cursor-pointer transition-colors has-[:focus-visible]:ring-2 ${
                        selected
                          ? 'border-primary ring-2 ring-primary bg-primary/10'
                          : 'hover:bg-muted'
                      }`}
                    >
                      <input
                        type="radio"
                        value={meta.key}
                        {...register('template_key')}
                        className="sr-only"
                      />
                      {/* eslint-disable-next-line @next/next/no-img-element -- 静的サムネイル、最適化不要 */}
                      <img src={meta.thumbnail} alt={meta.name} className="w-full rounded border" />
                      <p
                        className={`mt-2 text-sm font-medium ${
                          selected ? 'text-primary-strong' : 'text-foreground'
                        }`}
                      >
                        {meta.name}
                      </p>
                      <p className="text-xs text-muted-foreground">{meta.description}</p>
                    </label>
                  );
                })}
              </div>
            </div>
            <div className="col-span-2 grid gap-2">
              <Label htmlFor="description">店舗説明文</Label>
              <Textarea
                id="description"
                rows={4}
                placeholder="店舗の紹介文を入力してください..."
                {...register('description')}
              />
            </div>
            <div className="col-span-2 grid gap-2">
              <Label htmlFor="catch_copy">キャッチコピー</Label>
              <Textarea
                id="catch_copy"
                rows={2}
                placeholder="トップページに大きく表示される一言を入力してください..."
                {...register('catch_copy')}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="address">住所</Label>
              <Input
                id="address"
                type="text"
                placeholder="例: 東京都新宿区..."
                {...register('address')}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="phone">電話番号</Label>
              <Input id="phone" type="tel" placeholder="例: 03-1234-5678" {...register('phone')} />
            </div>
            <div className="col-span-2 grid gap-2">
              <Label htmlFor="business_hours">営業時間</Label>
              <Input
                id="business_hours"
                type="text"
                placeholder="例: 10:00〜翌1:00（不定休）"
                {...register('business_hours')}
              />
            </div>
            <div className="col-span-2 grid gap-2">
              <Label htmlFor="pricing_description">料金説明文</Label>
              <p className="text-xs text-muted-foreground">
                料金ページの本文です。改行はそのまま公開サイトに表示されます。
              </p>
              <Textarea
                id="pricing_description"
                rows={6}
                placeholder={'例:\nセット料金 60分 5,000円\n指名料 1,000円\n延長 30分 2,500円'}
                {...register('pricing_description')}
              />
            </div>
          </div>
        </section>

        {/* 模版テキスト */}
        <section>
          <h3 className="text-lg font-semibold text-foreground mb-6 border-l-4 border-primary pl-3">
            模版テキスト
          </h3>
          {textSlots.length === 0 ? (
            <div className="p-6 rounded-lg border border-dashed text-center text-muted-foreground text-sm">
              この模版に追加のテキスト項目はありません
            </div>
          ) : (
            <div className="space-y-6">
              {textSlots.map(slot => (
                <div key={slot.key} className="grid gap-2">
                  <Label htmlFor={`custom_texts.${slot.key}`}>{slot.label}</Label>
                  <Textarea
                    id={`custom_texts.${slot.key}`}
                    rows={3}
                    {...register(`custom_texts.${slot.key}`)}
                  />
                </div>
              ))}
            </div>
          )}
        </section>

        {/* 2. ビジュアル設定 */}
        <section>
          <h3 className="text-lg font-semibold text-foreground mb-6 border-l-4 border-primary pl-3">
            ビジュアル設定
          </h3>

          {/* ロゴ */}
          <div className="mb-8 grid gap-2">
            <Label>ロゴ画像</Label>
            <p className="text-xs text-muted-foreground">
              正方形の画像を推奨します（例: 200x200px）
            </p>
            <div className="flex items-start">
              <FormField
                control={control}
                name="logo_url"
                render={({ field: { value, onChange } }) => (
                  <ImageUpload
                    value={value}
                    onChange={onChange}
                    bucket="public"
                    className="w-32 h-32" // Square
                  />
                )}
              />
            </div>
          </div>

          {/* バナー */}
          <div className="mb-8 grid gap-2">
            <Label>バナー画像</Label>
            <p className="text-xs text-muted-foreground">
              横長の画像を推奨します（例: 1200x400px）
            </p>
            <FormField
              control={control}
              name="banner_url"
              render={({ field: { value, onChange } }) => (
                <ImageUpload
                  value={value}
                  onChange={onChange}
                  bucket="public"
                  className="w-full h-40 max-w-2xl" // Wide rectangle
                />
              )}
            />
          </div>

          {/* メインビジュアル (MV) */}
          <div className="mb-8 grid gap-2">
            <div className="flex justify-between items-center">
              <Label>メインビジュアル (MV)</Label>
              <FormField
                control={control}
                name="mv_type"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-center gap-2">
                    <FormLabel className="text-muted-foreground font-normal">タイプ:</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger size="sm">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="image">画像</SelectItem>
                        <SelectItem value="video">動画</SelectItem>
                      </SelectContent>
                    </Select>
                  </FormItem>
                )}
              />
            </div>
            <p className="text-xs text-muted-foreground">
              サイトの顔となる大きな画像です（例: 1920x800px）
            </p>
            <FormField
              control={control}
              name="mv_url"
              render={({ field: { value, onChange } }) => (
                <ImageUpload
                  value={value}
                  onChange={onChange}
                  bucket="public"
                  className="w-full h-64" // Taller wide rectangle
                />
              )}
            />
          </div>
        </section>

        {/* 3. SNSリンク */}
        <section>
          <div className="flex justify-between items-center mb-6">
            <h3 className="text-lg font-semibold text-foreground border-l-4 border-primary pl-3">
              SNS リンク
            </h3>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => appendSns({ platform: 'twitter', url: '', label: '' })}
            >
              + 追加
            </Button>
          </div>
          <div className="space-y-4">
            {snsFields.length === 0 ? (
              <div className="p-6 rounded-lg border border-dashed text-center text-muted-foreground text-sm">
                SNS リンクが登録されていません
              </div>
            ) : (
              snsFields.map((field, index) => (
                <div key={field.id} className="flex items-start gap-4 p-4 rounded-lg border">
                  <div className="flex-1 grid grid-cols-1 md:grid-cols-3 gap-4">
                    <FormField
                      control={control}
                      name={`sns_links.${index}.platform`}
                      render={({ field: platformField }) => (
                        <FormItem>
                          <FormLabel>プラットフォーム</FormLabel>
                          <Select
                            value={platformField.value}
                            onValueChange={platformField.onChange}
                          >
                            <FormControl>
                              <SelectTrigger className="w-full">
                                <SelectValue />
                              </SelectTrigger>
                            </FormControl>
                            <SelectContent>
                              {SNS_PLATFORMS.map(p => (
                                <SelectItem key={p.value} value={p.value}>
                                  {p.label}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </FormItem>
                      )}
                    />
                    <div className="md:col-span-2 grid gap-2">
                      <Label htmlFor={`sns_links.${index}.url`}>URL</Label>
                      <Input
                        id={`sns_links.${index}.url`}
                        type="url"
                        placeholder="https://..."
                        {...register(`sns_links.${index}.url`)}
                      />
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => removeSns(index)}
                    className="mt-6 text-muted-foreground hover:text-destructive"
                    aria-label="削除"
                  >
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M6 18L18 6M6 6l12 12"
                      />
                    </svg>
                  </Button>
                </div>
              ))
            )}
          </div>
        </section>

        {/* 4. パートナーリンク */}
        <section>
          <div className="flex justify-between items-center mb-6">
            <h3 className="text-lg font-semibold text-foreground border-l-4 border-primary pl-3">
              パートナーリンク
            </h3>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => appendPartner({ name: '', url: '', logo_url: '' })}
            >
              + 追加
            </Button>
          </div>
          <div className="space-y-4">
            {partnerFields.length === 0 ? (
              <div className="p-6 rounded-lg border border-dashed text-center text-muted-foreground text-sm">
                パートナーリンクが登録されていません
              </div>
            ) : (
              partnerFields.map((field, index) => (
                <div key={field.id} className="flex items-start gap-4 p-4 rounded-lg border">
                  <div className="flex-1 grid grid-cols-1 md:grid-cols-12 gap-4">
                    <div className="md:col-span-4 grid gap-2">
                      <Label htmlFor={`partner_links.${index}.name`}>名前</Label>
                      <Input
                        id={`partner_links.${index}.name`}
                        type="text"
                        placeholder="パートナー名"
                        {...register(`partner_links.${index}.name`)}
                      />
                    </div>
                    <div className="md:col-span-5 grid gap-2">
                      <Label htmlFor={`partner_links.${index}.url`}>URL</Label>
                      <Input
                        id={`partner_links.${index}.url`}
                        type="url"
                        placeholder="https://..."
                        {...register(`partner_links.${index}.url`)}
                      />
                    </div>
                    <div className="md:col-span-3 grid gap-2">
                      <Label>ロゴ</Label>
                      <div className="h-10">
                        <FormField
                          control={control}
                          name={`partner_links.${index}.logo_url`}
                          render={({ field: { value, onChange } }) => (
                            <ImageUpload
                              value={value}
                              onChange={onChange}
                              bucket="public"
                              className="w-full h-10"
                            />
                          )}
                        />
                      </div>
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => removePartner(index)}
                    className="mt-6 text-muted-foreground hover:text-destructive"
                    aria-label="削除"
                  >
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M6 18L18 6M6 6l12 12"
                      />
                    </svg>
                  </Button>
                </div>
              ))
            )}
          </div>
        </section>

        {/* Buttons */}
        <div className="flex justify-end space-x-4 pt-6 border-t sticky bottom-0 bg-card/90 backdrop-blur-sm p-4 -mx-8 -mb-8 rounded-b-xl">
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '保存中...' : '設定を保存する'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
