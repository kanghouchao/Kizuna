'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { CastFieldDefinitionResponse, castFieldDefinitionApi } from '@/entities/cast';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
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
  const form = useForm<CastFormData>({
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
  const { register, handleSubmit, setValue, watch, control } = form;

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
    <Form {...form}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* 基本情報 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={3}>
              基本情報
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex gap-8">
              <div className="grid gap-2">
                <Label>写真</Label>
                <ImageUpload
                  value={photoUrl}
                  onChange={url => setValue('photo_url', url)}
                  bucket="public"
                />
              </div>
              <div className="flex-1 space-y-4">
                <div className="grid gap-2">
                  <Label htmlFor="name">名前 *</Label>
                  <Input id="name" type="text" {...register('name', { required: true })} />
                </div>
                <FormField
                  control={control}
                  name="status"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>ステータス</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <FormControl>
                          <SelectTrigger className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="ACTIVE">有効</SelectItem>
                          <SelectItem value="INACTIVE">無効</SelectItem>
                        </SelectContent>
                      </Select>
                    </FormItem>
                  )}
                />
                <div className="grid gap-2">
                  <Label htmlFor="display_order">表示順</Label>
                  <Input
                    id="display_order"
                    type="number"
                    {...register('display_order', { valueAsNumber: true })}
                  />
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* プロフィール */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={3}>
              プロフィール
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="age">年齢</Label>
                <Input id="age" type="number" {...register('age', { valueAsNumber: true })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="height">身長 (cm)</Label>
                <Input id="height" type="number" {...register('height', { valueAsNumber: true })} />
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="bust">バスト (cm)</Label>
                <Input id="bust" type="number" {...register('bust', { valueAsNumber: true })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="waist">ウエスト (cm)</Label>
                <Input id="waist" type="number" {...register('waist', { valueAsNumber: true })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="hip">ヒップ (cm)</Label>
                <Input id="hip" type="number" {...register('hip', { valueAsNumber: true })} />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 自己紹介 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={3}>
              自己紹介
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Textarea
              id="introduction"
              rows={4}
              {...register('introduction')}
              placeholder="自己紹介を入力してください..."
            />
          </CardContent>
        </Card>

        {/* カスタムフィールド（編集時のみ。作成時はキャストがまだ無いため値を付与できない） */}
        {isEdit && (
          <Card>
            <CardHeader>
              <CardTitle role="heading" aria-level={3}>
                カスタムフィールド
              </CardTitle>
            </CardHeader>
            <CardContent>
              {isLoadingDefinitions ? (
                <div className="p-6 text-center text-sm text-muted-foreground">読み込み中...</div>
              ) : definitions.length === 0 ? (
                <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                  カスタムフィールドは登録されていません
                </div>
              ) : (
                <div className="space-y-4">
                  {definitions.map(definition => (
                    <div key={definition.key} className="grid gap-2">
                      <Label htmlFor={`cast-custom-field-${definition.key}`}>
                        {definition.label}
                      </Label>
                      <Input
                        id={`cast-custom-field-${definition.key}`}
                        type="text"
                        // 自身が所有するキーのみ初期値に採用する。プレーンオブジェクトの
                        // ブラケットアクセスは 'constructor' 等の継承プロパティを拾うため hasOwn で防ぐ。
                        defaultValue={
                          existingCustomFields &&
                          Object.hasOwn(existingCustomFields, definition.key)
                            ? existingCustomFields[definition.key]
                            : ''
                        }
                        {...register(`custom_fields.${definition.key}`)}
                      />
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {/* ボタン */}
        <div className="flex justify-end gap-4">
          <Button type="button" variant="outline" onClick={() => router.back()}>
            キャンセル
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '保存中...' : '保存する'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
