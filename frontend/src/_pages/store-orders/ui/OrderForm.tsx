'use client';

import { OrderContactFields } from './OrderContactFields';
import { OrderCustomerField } from './OrderCustomerField';
import { ContactSnapshot, CustomerSelection } from '@/entities/order';
import { OrderEditorSection } from './OrderEditorSection';
import { OrderCourseField } from './OrderCourseField';
import { OrderSpecialServicesField } from './OrderSpecialServicesField';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { OrderFeeLineInput, ReceptionRoute, WebApplicationReceptionRoute } from '@/entities/order';
import { CastSearchCombobox } from './CastSearchCombobox';
import { OrderReceptionistField } from './OrderReceptionistField';
import { OrderFeeLinesField } from './OrderFeeLinesField';
import {
  Button,
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

// Web 申請の群は予約申請の確定だけが名乗る値なので選択肢に出さない（後端も拒否する）。
// 選択肢が 1 つでもセレクトを残すのは、受付経路が記録項目であって、値の追加は行の追加だから。
const RECEPTION_ROUTE_OPTIONS = [{ value: 'PHONE', label: '電話受付' }];

export interface OrderFormData {
  receptionist_id: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  arrival_scheduled_end_time: string;
  customer_selection: CustomerSelection;
  contact_snapshot: ContactSnapshot;
  address: string;
  building_name: string;
  cast_id: string;
  pax: number;
  /** 受付経路。店舗側の合法値は電話受付だけ（Web 申請の群は予約申請の確定だけが名乗る）。 */
  reception_route: Exclude<ReceptionRoute, WebApplicationReceptionRoute>;
  /** この受注に適用するコース名の写し。基本コース料金の明細を置くなら必須になる。 */
  special_service_ids: string[];
  course_id: string;
  fee_lines: OrderFeeLineInput[];
  carrier: string;
  media_name: string;
  remarks: string;
  cast_driver_message: string;
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
      customer_selection: { mode: 'NONE' },
      contact_snapshot: { name: '', phone_number: '', email: '', line_id: '' },
      pax: 1,
      reception_route: 'PHONE',
      special_service_ids: [],
      course_id: '',
      fee_lines: [],
    },
  });
  const { register, handleSubmit, control } = form;

  return (
    <Form {...form}>
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="rounded-xl border bg-card text-card-foreground shadow-sm"
      >
        <OrderEditorSection title="受付・日時" description="受付担当と訪問する日時を設定します。">
          <div className="grid grid-cols-2 gap-6">
            <OrderReceptionistField scene="create" />
            <div className="grid gap-2">
              <Label htmlFor="business_date">営業日</Label>
              <Input id="business_date" type="date" {...register('business_date')} />
            </div>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="arrival_scheduled_start_time">到着予定時刻</Label>
            <div className="flex items-center gap-2">
              <Input
                id="arrival_scheduled_start_time"
                aria-label="到着予定（開始）"
                type="time"
                className="w-fit"
                {...register('arrival_scheduled_start_time')}
              />
              <span className="text-muted-foreground">～</span>
              <Input
                aria-label="到着予定（終了）"
                type="time"
                className="w-fit"
                {...register('arrival_scheduled_end_time')}
              />
            </div>
          </div>
        </OrderEditorSection>
        <OrderEditorSection
          title="お客様・訪問先"
          description="顧客と今回の連絡先を別々に指定してください。"
        >
          <OrderCustomerField />
          <OrderContactFields />
          <div className="grid grid-cols-2 gap-6">
            <div className="grid gap-2">
              <Label htmlFor="address">住所</Label>
              <Input id="address" {...register('address')} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="building_name">建物</Label>
              <Input id="building_name" {...register('building_name')} />
            </div>
          </div>
        </OrderEditorSection>

        <OrderEditorSection
          title="サービス・料金"
          description="担当キャストと提供内容を選択します。金額は送信後の確認画面で試算します。"
        >
          <div className="grid grid-cols-2 gap-6">
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
            <div className="col-span-2">
              <OrderCourseField required />
            </div>
            <div className="col-span-2">
              <OrderSpecialServicesField />
            </div>
          </div>
          <div className="mt-6">
            <OrderFeeLinesField />
          </div>
        </OrderEditorSection>
        <OrderEditorSection
          title="連絡・備考"
          description="受付メモと、キャスト・ドライバーに伝える内容を記入します。"
        >
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
        </OrderEditorSection>

        <div className="flex items-center justify-between gap-6 rounded-b-xl border-t bg-card p-6">
          <p className="text-sm text-muted-foreground">登録前に料金・報酬を確認できます。</p>
          <div className="flex shrink-0 gap-3">
            <Button type="button" variant="outline" onClick={() => router.back()}>
              キャンセル
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '登録中...' : '登録する'}
            </Button>
          </div>
        </div>
      </form>
    </Form>
  );
}
