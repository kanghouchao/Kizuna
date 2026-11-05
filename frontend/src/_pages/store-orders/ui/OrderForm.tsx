'use client';

import { OrderBusinessContactFields } from './OrderBusinessContactFields';
import {
  BusinessContactPermissionInput,
  GuestContactImportInput,
  OrderApplicationDetail,
} from '@/entities/order';
import { GuestApplicationConsentSection } from './GuestApplicationConsentSection';
import { OrderContactFields } from './OrderContactFields';
import { OrderCustomerField } from './OrderCustomerField';
import { ContactSnapshot, CustomerSelection } from '@/entities/order';
import { OrderEditorSection } from './OrderEditorSection';
import { OrderCourseField } from './OrderCourseField';
import { OrderSpecialServicesField } from './OrderSpecialServicesField';

import { FeePreviewValues } from '../lib/feePreview';
import { orderFormDefaults } from '../lib/orderFormAssembly';
import { useForm, useWatch } from 'react-hook-form';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { OrderFeeLineInput, ReceptionRoute, WebApplicationReceptionRoute } from '@/entities/order';
import { CastSearchCombobox } from './CastSearchCombobox';
import { OrderReceptionistField } from './OrderReceptionistField';
import { OrderFeeLinesField } from './OrderFeeLinesField';
import { OrderAcquisitionFields, type AcquisitionValues } from './OrderAcquisitionFields';
import {
  Button,
  Checkbox,
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

export interface OrderFormData extends AcquisitionValues {
  receptionist_id: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  arrival_scheduled_end_time: string;
  customer_selection: CustomerSelection;
  contact_snapshot: ContactSnapshot;
  business_contact_permissions: BusinessContactPermissionInput[];
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
  remarks: string;
  cast_driver_message: string;
  /** 予約申請の確定でだけ使う。ゲスト申請の連絡先を台帳へ取り込むかの明示。 */
  contact_imports: GuestContactImportInput[];
  /** 予約申請の確定でだけ使う。申請の指名を外して確定するか。 */
  clear_cast: boolean;
}

interface OrderFormProps {
  onSubmit: (data: OrderFormData) => void;
  isSubmitting?: boolean;
  /**
   * 予約申請の確定。申請内容を事前入力し、確定端点が持たない項目（派遣先・受付経路など）は出さない。
   * 確定前に申請だけを書き換える口は無い — 原文を直せると「不変のまま対照できる」が成り立たない（ADR 0017）。
   */
  confirming?: { application: OrderApplicationDetail; backHref: string };
}

export function OrderForm({ onSubmit, isSubmitting, confirming }: OrderFormProps) {
  const router = useRouter();
  const application = confirming?.application;
  const form = useForm<OrderFormData & FeePreviewValues>({
    defaultValues: orderFormDefaults(application),
  });
  const { register, handleSubmit, control, setValue } = form;
  const clearCast = useWatch({ control, name: 'clear_cast' });
  const selectedCastId = useWatch({ control, name: 'cast_id' });
  // 会員コードのスナップショットの有無がそのまま入口の別。サーバ側の判定と同じ根拠を使う
  const isGuest = !!application && !application.requester_member_code;

  return (
    <Form {...form}>
      <form
        noValidate
        onSubmit={handleSubmit(({ fee_preview: _preview, ...data }) => onSubmit(data))}
        className="rounded-xl border bg-card text-card-foreground shadow-sm"
      >
        <OrderEditorSection title="受付・日時" description="受付担当と訪問する日時を設定します。">
          <div className="grid grid-cols-2 gap-6">
            <OrderReceptionistField scene={confirming ? 'confirm' : 'create'} />
            {/* 営業日はサーバ側が @NotNull。欠けたまま送ると 400 が欄の外の通知にしか出ない */}
            <FormField
              control={control}
              name="business_date"
              rules={{ required: '営業日を選択してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>営業日</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
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
          {!application || isGuest ? (
            <OrderCustomerField />
          ) : (
            <p>顧客は会員本人の関連から決まります。</p>
          )}
          {application && isGuest && <GuestApplicationConsentSection detail={application} />}
          <OrderAcquisitionFields allowUnset />
          <OrderContactFields />
          {!confirming && (
            <>
              <OrderBusinessContactFields />
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
            </>
          )}
        </OrderEditorSection>

        <OrderEditorSection
          title="サービス・料金"
          description="担当キャストと提供内容を選択します。金額は送信後の確認画面で試算します。"
        >
          <div className="grid grid-cols-2 gap-6">
            {application ? (
              // 確定の指名は任意（会員は指名なしで申請できる）。解除は明示操作で、在籍停止・シフト取消で
              // 無効になった指名を外して確定する導線
              <div className="grid gap-2">
                <CastSearchCombobox
                  id="application-confirm-cast"
                  label="指名"
                  castName={application.cast_name ?? application.cast_id ?? ''}
                  onChange={castId => setValue('cast_id', castId, { shouldDirty: true })}
                  disabled={clearCast}
                />
                {selectedCastId && (
                  <FormField
                    control={control}
                    name="clear_cast"
                    render={({ field }) => (
                      <FormItem className="flex flex-row items-center gap-2">
                        <FormControl>
                          <Checkbox
                            id="confirm_clear_cast"
                            checked={field.value}
                            onCheckedChange={value => {
                              field.onChange(value === true);
                              if (value === true) {
                                setValue('special_service_ids', [], { shouldDirty: true });
                                setValue('fee_preview.special_services', 0, { shouldDirty: true });
                              }
                            }}
                          />
                        </FormControl>
                        <FormLabel htmlFor="confirm_clear_cast" className="font-medium">
                          指名を外して確定する
                        </FormLabel>
                      </FormItem>
                    )}
                  />
                )}
              </div>
            ) : (
              // キャストはサーバ側が @NotBlank。候補から選ばないと 400 になるため送信前に止める
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
            )}
            <FormField
              control={control}
              name="pax"
              rules={{
                // 確定では人数を必須とする（受注作成は空欄を「未入力」として送らない）
                required: confirming ? '人数を入力してください' : false,
                validate: value =>
                  value == null ||
                  String(value) === '' ||
                  (Number.isInteger(Number(value)) && Number(value) >= 1) ||
                  '人数は1以上の整数で入力してください',
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>人数</FormLabel>
                  <FormControl>
                    <Input type="number" min={1} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {!confirming && (
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
            )}
            <div className="col-span-2">
              <OrderCourseField required />
            </div>
            <div className="col-span-2">
              {clearCast ? (
                <p role="status">指名を外すため、特殊サービスは選択できません。</p>
              ) : (
                <OrderSpecialServicesField />
              )}
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
            {!confirming && (
              <div className="grid gap-2">
                <Label htmlFor="cast_driver_message">キャスト・ドライバーへのメッセージ</Label>
                <Textarea id="cast_driver_message" rows={3} {...register('cast_driver_message')} />
              </div>
            )}
          </div>
        </OrderEditorSection>

        <div className="flex items-center justify-between gap-6 rounded-b-xl border-t bg-card p-6">
          <p className="text-sm text-muted-foreground">
            {confirming ? '確定' : '登録'}前に料金・報酬を確認できます。
          </p>
          <div className="flex shrink-0 gap-3">
            {confirming ? (
              <Button variant="outline" render={<Link href={confirming.backHref} />}>
                戻る
              </Button>
            ) : (
              <Button type="button" variant="outline" onClick={() => router.back()}>
                キャンセル
              </Button>
            )}
            {/* 検証では塞がない — 灰色のボタンは何が足りないかを言わない。押せば欄の傍が言う */}
            <Button type="submit" disabled={isSubmitting}>
              {confirming
                ? isSubmitting
                  ? '確定中...'
                  : '確定する'
                : isSubmitting
                  ? '登録中...'
                  : '登録する'}
            </Button>
          </div>
        </div>
      </form>
    </Form>
  );
}
