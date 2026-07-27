'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { CustomerCreateRequest } from '@/entities/customer';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Checkbox,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  Input,
  Label,
  Textarea,
} from '@/shared/ui';

/** 顧客フォームのデータ型 */
export interface CustomerFormData {
  name: string;
  phone_number: string;
  phone_number2: string;
  address: string;
  building_name: string;
  classification: string;
  has_pet: boolean;
  rank: string;
  line_id: string;
  usage_areas: string;
  ng_type: string;
  ng_content: string;
}

/** フォーム値を API リクエスト形式へ変換する（空文字のフィールドは undefined に落とす） */
export function toCustomerRequest(data: CustomerFormData): CustomerCreateRequest {
  return {
    name: data.name,
    phone_number: data.phone_number || undefined,
    phone_number2: data.phone_number2 || undefined,
    address: data.address || undefined,
    building_name: data.building_name || undefined,
    classification: data.classification || undefined,
    has_pet: data.has_pet,
    rank: data.rank || undefined,
    line_id: data.line_id || undefined,
    usage_areas: data.usage_areas || undefined,
    ng_type: data.ng_type || undefined,
    ng_content: data.ng_content || undefined,
  };
}

interface CustomerFormProps {
  /** 編集時の初期データ */
  initialData?: Partial<CustomerFormData>;
  /** フォーム送信時のコールバック */
  onSubmit: (data: CustomerFormData) => void;
  /** 送信中フラグ */
  isSubmitting?: boolean;
}

/** 顧客登録・編集フォームコンポーネント */
export function CustomerForm({ initialData, onSubmit, isSubmitting }: CustomerFormProps) {
  const router = useRouter();
  const form = useForm<CustomerFormData>({
    defaultValues: {
      name: '',
      phone_number: '',
      phone_number2: '',
      address: '',
      building_name: '',
      classification: '',
      has_pet: false,
      rank: 'SILVER',
      line_id: '',
      usage_areas: '',
      ng_type: '',
      ng_content: '',
      ...initialData,
    },
  });
  const { register, handleSubmit, control } = form;

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* 基本情報 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              基本情報
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="name">名前 *</Label>
                <Input id="name" type="text" {...register('name', { required: true })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="classification">区分</Label>
                <Input id="classification" type="text" {...register('classification')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="phone_number">電話番号</Label>
                <Input id="phone_number" type="tel" {...register('phone_number')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="phone_number2">電話番号2</Label>
                <Input id="phone_number2" type="tel" {...register('phone_number2')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="line_id">LINE ID</Label>
                <Input id="line_id" type="text" {...register('line_id')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="rank">ランク</Label>
                <Input id="rank" type="text" placeholder="SILVER" {...register('rank')} />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 住所 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              住所
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="address">住所</Label>
                <Input id="address" type="text" {...register('address')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="building_name">建物名</Label>
                <Input id="building_name" type="text" {...register('building_name')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="usage_areas">利用エリア</Label>
                <Input id="usage_areas" type="text" {...register('usage_areas')} />
              </div>
              <FormField
                control={control}
                name="has_pet"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-center gap-2 self-end pb-2">
                    <FormControl>
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={value => field.onChange(value === true)}
                      />
                    </FormControl>
                    <FormLabel className="font-medium">ペットあり</FormLabel>
                  </FormItem>
                )}
              />
            </div>
          </CardContent>
        </Card>

        {/* NG 情報 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              NG 情報
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="ng_type">NG タイプ</Label>
                <Input
                  id="ng_type"
                  type="text"
                  placeholder="注意・禁止など"
                  {...register('ng_type')}
                />
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="ng_content">NG 内容</Label>
              <Textarea id="ng_content" rows={3} {...register('ng_content')} />
            </div>
          </CardContent>
        </Card>

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
