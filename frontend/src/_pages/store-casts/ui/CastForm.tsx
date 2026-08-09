'use client';

import { Control, useForm } from 'react-hook-form';
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
  FormMessage,
  ImageUpload,
  Input,
  Label,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';
import { integerRule, useManagedList } from '@/shared/lib';

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

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '在籍中' },
  { value: 'INACTIVE', label: '在籍停止' },
];

/**
 * プロフィールの任意数値欄。いずれもサーバ側は Integer で、noValidate によって原生の step=1 が
 * 執行されなくなったぶんを規則で引き継ぐ。
 *
 * 空欄の valueAsNumber は NaN で、そのまま value に載せるとブラウザが警告を出すため表示だけ
 * 空文字へ戻す。未入力は許すので required は持たない。
 */
function NumericProfileField({
  control,
  name,
  label,
  unit,
}: {
  control: Control<CastFormData>;
  name: 'age' | 'height' | 'bust' | 'waist' | 'hip';
  label: string;
  unit?: string;
}) {
  return (
    <FormField
      control={control}
      name={name}
      rules={{ validate: integerRule(label) }}
      render={({ field }) => (
        <FormItem className="gap-2">
          <FormLabel>{unit ? `${label} (${unit})` : label}</FormLabel>
          <FormControl>
            {/* id は FormControl が持つ。ここで与えると FormLabel の htmlFor と食い違う */}
            <Input
              type="number"
              {...field}
              value={field.value === null || Number.isNaN(field.value) ? '' : field.value}
              onChange={event => field.onChange(event.target.valueAsNumber)}
            />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
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
  const {
    items: definitions,
    isLoading: isLoadingDefinitions,
    failed: definitionsFailed,
    refetch: refetchDefinitions,
  } = useManagedList<CastFieldDefinitionResponse>(() =>
    isEdit ? castFieldDefinitionApi.list() : Promise.resolve([])
  );

  return (
    <Form {...form}>
      {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
          我々の文言は永久に描かれない。執行は各 rules が担う */}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>
        {/* 基本情報 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
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
                <FormField
                  control={control}
                  name="name"
                  rules={{ required: '名前を入力してください' }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>名前 *</FormLabel>
                      <FormControl>
                        <Input type="text" required {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="status"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>在籍状態</FormLabel>
                      <Select
                        items={STATUS_OPTIONS}
                        value={field.value}
                        onValueChange={field.onChange}
                      >
                        <FormControl>
                          <SelectTrigger className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {STATUS_OPTIONS.map(o => (
                            <SelectItem key={o.value} value={o.value}>
                              {o.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="display_order"
                  rules={{ validate: integerRule('表示順') }}
                  render={({ field }) => (
                    <FormItem className="gap-2">
                      <FormLabel>表示順</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          {...field}
                          value={Number.isNaN(field.value) ? '' : field.value}
                          onChange={event => field.onChange(event.target.valueAsNumber)}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* プロフィール */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              プロフィール
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
              <NumericProfileField control={control} name="age" label="年齢" />
              <NumericProfileField control={control} name="height" label="身長" unit="cm" />
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <NumericProfileField control={control} name="bust" label="バスト" unit="cm" />
              <NumericProfileField control={control} name="waist" label="ウエスト" unit="cm" />
              <NumericProfileField control={control} name="hip" label="ヒップ" unit="cm" />
            </div>
          </CardContent>
        </Card>

        {/* 自己紹介 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
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
              <CardTitle role="heading" aria-level={2}>
                カスタムフィールド
              </CardTitle>
            </CardHeader>
            <CardContent>
              {isLoadingDefinitions ? (
                <div className="p-6 text-center text-sm text-muted-foreground">読み込み中...</div>
              ) : definitionsFailed ? (
                // 「登録されていません」と言い切ると、読めなかっただけの状態が事実に化ける。
                // 定義が取れないまま保存しても custom_fields 自体を送らないので既存値は消えない
                <RegionError
                  message="カスタムフィールド定義の取得に失敗しました"
                  onRetry={() => void refetchDefinitions()}
                  className="justify-center p-6"
                />
              ) : definitions.length === 0 ? (
                <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
                  カスタムフィールドは登録されていません
                </div>
              ) : (
                <div className="space-y-4">
                  {definitions.map(definition => {
                    const key = definition.key;
                    if (key === undefined) return null;
                    return (
                      <div key={key} className="grid gap-2">
                        <Label htmlFor={`cast-custom-field-${key}`}>{definition.label}</Label>
                        <Input
                          id={`cast-custom-field-${key}`}
                          type="text"
                          // 自身が所有するキーのみ初期値に採用する。プレーンオブジェクトの
                          // ブラケットアクセスは 'constructor' 等の継承プロパティを拾うため hasOwn で防ぐ。
                          defaultValue={
                            existingCustomFields && Object.hasOwn(existingCustomFields, key)
                              ? existingCustomFields[key]
                              : ''
                          }
                          {...register(`custom_fields.${key}`)}
                        />
                      </div>
                    );
                  })}
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
