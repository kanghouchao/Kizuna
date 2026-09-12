'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { OrderFeeLineInput, ReceptionRoute, WebApplicationReceptionRoute } from '@/entities/order';
import { CastSearchCombobox } from './CastSearchCombobox';
import { OrderReceptionistField } from './OrderReceptionistField';
import { OrderFeeLinesField } from './OrderFeeLinesField';
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
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

// 引き金に出る文言は候補一覧（items）から引かれる。渡さないと生の値が出るので、
// 選べる値と表示はここで対にして持ち、項目の描画も同じ配列を読む。
const CLASSIFICATION_OPTIONS = ['ーー', '自宅', 'ラブホ', 'ビジホ'].map(v => ({
  value: v,
  label: v,
}));
const HAS_PET_OPTIONS = [
  { value: 'false', label: 'なし' },
  { value: 'true', label: 'あり' },
];
const COURSE_MINUTES_OPTIONS = ['60', '90', '120'].map(v => ({ value: v, label: v }));
// Web 申請の群は予約申請の確定だけが名乗る値なので選択肢に出さない（後端も拒否する）。
// 選択肢が 1 つでもセレクトを残すのは、受付経路が記録項目であって、値の追加は行の追加だから。
const RECEPTION_ROUTE_OPTIONS = [{ value: 'PHONE', label: '電話受付' }];

export interface OrderFormData {
  receptionist_id: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  arrival_scheduled_end_time: string;
  customer_name: string;
  phone_number: string;
  phone_number2: string;
  address: string;
  building_name: string;
  classification: string;
  landmark: string;
  has_pet: boolean;
  cast_id: string;
  pax: number;
  /** 受付経路。店舗側の合法値は電話受付だけ（Web 申請の群は予約申請の確定だけが名乗る）。 */
  reception_route: Exclude<ReceptionRoute, WebApplicationReceptionRoute>;
  /** この受注に適用するコース名の写し。基本コース料金の明細を置くなら必須になる。 */
  course_name: string;
  course_minutes: number;
  extension_minutes: number;
  fee_lines: OrderFeeLineInput[];
  carrier: string;
  media_name: string;
  remarks: string;
  cast_driver_message: string;
  ng_type: string;
  ng_content: string;
}

interface OrderFormProps {
  onSubmit: (data: OrderFormData) => void;
  isSubmitting?: boolean;
}

export function OrderForm({ onSubmit, isSubmitting }: OrderFormProps) {
  const router = useRouter();
  const form = useForm<OrderFormData>({
    defaultValues: {
      receptionist_id: '',
      business_date: new Date().toISOString().split('T')[0],
      classification: 'ーー',
      pax: 1,
      reception_route: 'PHONE',
      course_name: '',
      course_minutes: 60,
      extension_minutes: 0,
      fee_lines: [],
      has_pet: false,
      ng_type: 'NG無し',
    },
  });
  const { register, handleSubmit, control, watch } = form;
  const course_name = watch('course_name');

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              基本情報
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <OrderReceptionistField scene="create" />
              <div className="grid gap-2">
                <Label htmlFor="business_date">営業日</Label>
                <Input id="business_date" type="date" {...register('business_date')} />
              </div>
            </div>

            <div className="grid gap-2">
              <Label>到着予定時刻</Label>
              <div className="flex items-center gap-2">
                <Input
                  type="time"
                  className="w-fit"
                  {...register('arrival_scheduled_start_time')}
                />
                <span className="text-muted-foreground">～</span>
                <Input type="time" className="w-fit" {...register('arrival_scheduled_end_time')} />
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              お客様情報
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="customer_name">お客様名</Label>
                <Input id="customer_name" type="text" {...register('customer_name')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="phone_number">電話番号</Label>
                <Input id="phone_number" type="text" {...register('phone_number')} />
              </div>
              <div className="grid gap-2 md:col-span-2">
                <Label htmlFor="address">住所</Label>
                <Input id="address" type="text" {...register('address')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="building_name">建物</Label>
                <Input id="building_name" type="text" {...register('building_name')} />
              </div>
              <FormField
                control={control}
                name="classification"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>区分</FormLabel>
                    <Select
                      items={CLASSIFICATION_OPTIONS}
                      value={field.value}
                      onValueChange={field.onChange}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {CLASSIFICATION_OPTIONS.map(o => (
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
                name="has_pet"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>ペット有無</FormLabel>
                    <Select
                      items={HAS_PET_OPTIONS}
                      value={String(field.value)}
                      onValueChange={v => field.onChange(v === 'true')}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {HAS_PET_OPTIONS.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )}
              />
              <div className="grid gap-2">
                <Label htmlFor="landmark">目印</Label>
                <Input id="landmark" type="text" {...register('landmark')} />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              コース・料金
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
              {/* キャストはサーバ側が @NotBlank。候補から選ばないと 400 になるため送信前に止める */}
              <FormField
                control={control}
                name="cast_id"
                rules={{ required: 'キャストを候補から選択してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <CastSearchCombobox
                        id="castName"
                        label="キャスト *"
                        castName=""
                        onChange={field.onChange}
                        triggerRef={field.ref}
                        required
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="course_minutes"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>ｺｰｽ(分)</FormLabel>
                    <Select
                      items={COURSE_MINUTES_OPTIONS}
                      value={String(field.value)}
                      onValueChange={v => field.onChange(Number(v))}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {COURSE_MINUTES_OPTIONS.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )}
              />
              <div className="grid gap-2">
                <Label htmlFor="pax">人数</Label>
                <Input id="pax" type="number" min={1} {...register('pax')} />
              </div>
              <FormField
                control={control}
                name="reception_route"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>受付経路</FormLabel>
                    <Select
                      items={RECEPTION_ROUTE_OPTIONS}
                      value={field.value}
                      onValueChange={field.onChange}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {RECEPTION_ROUTE_OPTIONS.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )}
              />
              <div className="grid gap-2">
                <Label htmlFor="extension_minutes">延長</Label>
                <Input id="extension_minutes" type="number" {...register('extension_minutes')} />
              </div>
              <FormField
                control={control}
                name="course_name"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>コース名</FormLabel>
                    <FormControl>
                      <Input {...field} maxLength={255} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <div className="mt-6">
              <OrderFeeLinesField courseName={course_name} />
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              その他
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="remarks">備考</Label>
                <Textarea id="remarks" rows={3} {...register('remarks')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="cast_driver_message">キャスト・ドライバーへのメッセージ</Label>
                <Textarea id="cast_driver_message" rows={3} {...register('cast_driver_message')} />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* ボタン */}
        <div className="flex justify-end gap-4">
          <Button type="button" variant="outline" onClick={() => router.back()}>
            キャンセル
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '登録中...' : '登録する'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
