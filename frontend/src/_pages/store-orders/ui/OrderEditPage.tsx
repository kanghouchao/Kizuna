'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { ExternalLinkIcon } from 'lucide-react';
import {
  Order,
  OrderFeeLineInput,
  OrderUpdateRequest,
  orderApi,
  storeEditableFeeLines,
  systemOwnedFeeLines,
  toFeeLineInputs,
} from '@/entities/order';
import { getApiErrorMessage, storePath, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { UNLINKED_NOTE, customerHeadingText, customerLabel } from '../lib/customerLabel';
import { CastSearchCombobox } from './CastSearchCombobox';
import { OrderFeeLinesField } from './OrderFeeLinesField';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  Input,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

// 受付担当の未選択を表す番兵値。フォームが持つ値は空文字に戻す（作成フォームと同型）。
const SELECT_NONE = '__none__';

interface OrderEditFormValues {
  /** 受付担当。'' は「変更しない」ではなく候補の未解決を意味するので、送信時に元の値へ戻す。 */
  receptionist_id: string;
  /** 指名するキャストの id。 */
  cast_id: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  arrival_scheduled_end_time: string;
  pax: string;
  /** 適用されたコース名の写し。基本コース料金の明細を置くなら必須になる。 */
  course_name: string;
  course_minutes: string;
  extension_minutes: string;
  /** 会計内訳。行に同一性は無く、送った内容がそのまま新しい内訳になる。 */
  fee_lines: OrderFeeLineInput[];
  location_address: string;
  location_building: string;
  carrier: string;
  media_name: string;
  remarks: string;
  cast_driver_message: string;
  contact_name: string;
  contact_phone_number: string;
}

const EMPTY_VALUES: OrderEditFormValues = {
  receptionist_id: '',
  cast_id: '',
  business_date: '',
  arrival_scheduled_start_time: '',
  arrival_scheduled_end_time: '',
  pax: '',
  course_name: '',
  course_minutes: '',
  extension_minutes: '',
  fee_lines: [],
  location_address: '',
  location_building: '',
  carrier: '',
  media_name: '',
  remarks: '',
  cast_driver_message: '',
  contact_name: '',
  contact_phone_number: '',
};

/** 時刻は秒まで返るが、入力欄（type=time）は分までしか扱わない。 */
function toTimeInput(value: string | undefined): string {
  return value ? value.slice(0, 5) : '';
}

/**
 * 日付・時刻・数値の空欄は「送らない」。契約は null を「変更しない」と読むため空への書き換えを表現する形が
 * 無く、空文字を送ると型の変換に失敗して 400 になる。空にしたつもりの欄は元の値が残る。
 */
function optionalNumber(value: string): number | undefined {
  return value.trim() === '' ? undefined : Number(value);
}

function optionalTime(value: string): string | undefined {
  return value === '' ? undefined : `${value}:00`;
}

function optionalDate(value: string): string | undefined {
  return value === '' ? undefined : value;
}

/** 播いた内訳から変わったか。行の集合には「触ったか」の真偽が付かないので、値そのものを突き合わせる。 */
function feeLinesChanged(current: OrderFeeLineInput[], seeded: OrderFeeLineInput[]): boolean {
  return JSON.stringify(toFeeLineInputs(current)) !== JSON.stringify(toFeeLineInputs(seeded));
}

/**
 * 触った欄の項目だけを残す。欄の名前は契約の項目名と同じなので、対応表を別に持たずに名前で引ける。
 *
 * <p>全項目を毎回運ぶと、同じ受注を別の操作者が直している間この画面が開いていた場合、触ってもいない
 * 項目まで開いた時点の値で上書きしてしまう。部分更新の契約（送らない＝変更しない）をそのまま使う。
 */
function pickEdited(
  edited: OrderUpdateRequest,
  // 明細の印は行ごとの入れ子（真偽ではない）になるため、真偽に狭めず素の値の真偽性で見る。
  // 明細そのものはここを通らず、播いた内容との突き合わせで運ぶかを決める。
  dirtyFields: Partial<Record<keyof OrderEditFormValues, unknown>>
): OrderUpdateRequest {
  // 絞り込んでも項目の型は変わらないが、Object.fromEntries は索引署名しか名乗れないため写し直す
  return Object.fromEntries(
    Object.entries(edited).filter(([key]) => dirtyFields[key as keyof OrderEditFormValues])
  ) as OrderUpdateRequest;
}

/**
 * 受注 1 件の編集ページ。欄が 17 あり、頁の縦スクロールだけで端から端まで辿れる高さが要る。
 *
 * <p>開くたびにサーバから 1 件を読み直す。一覧の行を種にすると、他の操作者が直した後の画面で 陳腐化した値をそのまま送り返してしまう。
 *
 * <p>送るのは<b>触った欄だけ</b>。全項目を毎回運ぶと、この画面を開いている間に別の操作者が同じ受注を直した場合、
 * 触ってもいない項目まで開いた時点の値で押し戻してしまう。文字列の項目は空欄を空文字で送るので「空にする」も
 * 表せるが、日付・時刻・数値にその形は無い（契約が「変更しない」を null で表すため）。
 *
 * <p>顧客区は<b>読み取りと遷移だけ</b>。顧客台帳の項目をここから直せると、受注 1 件を直したつもりの 変更が同じ顧客の他の受注へ波及する。訂正は顧客詳細で行う。
 *
 * <p>連絡先の 2 項目は顧客が着いていない受注にだけ出す。着いた受注では名乗りの正本が台帳の行にあり、 送ってもサーバが撥ねる。
 */
export default function OrderEditPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const orderId = params.id as string;
  const router = useRouter();

  const {
    data: current,
    isLoading,
    failure,
    reload,
  } = useResource<Order>(() => orderApi.get(orderId), [orderId]);

  const {
    data: receptionistOptions,
    failure: receptionistsFailure,
    reload: loadReceptionists,
  } = useResource(() => orderApi.listReceptionists());
  const receptionistItems = [
    { value: SELECT_NONE, label: '－－－' },
    ...(receptionistOptions ?? [])
      .filter(o => o.id !== undefined)
      .map(o => ({ value: String(o.id), label: o.display_name ?? '' })),
  ];

  const form = useForm<OrderEditFormValues>({ defaultValues: EMPTY_VALUES });
  const { handleSubmit, control, reset, watch, formState } = form;
  // 播種の reset がその時点の値を基準にするので、ここに現れるのは操作者が触った欄だけになる。
  // レンダー中に読むのは、react-hook-form が formState の購読をこの読み取りで決めるため
  const { dirtyFields } = formState;
  // 播き終えたか。フォームを出す条件をこれにするのは、取得の到着がレンダーより後で、
  // 「取れた」で出すと播く前の 1 フレームが空欄のまま描かれるため（DESIGN.md）。
  const [hasSeeded, setHasSeeded] = useState(false);

  // 取得できたら播く。取得の到着はレンダーより後なので、values ではなく効果で入れる
  // （初期値として渡すと、開いた最初のフレームが空欄のまま描かれる）。
  useEffect(() => {
    if (current === null) {
      return;
    }
    reset({
      receptionist_id: current.receptionist_id != null ? String(current.receptionist_id) : '',
      cast_id: current.cast_id ?? '',
      business_date: current.business_date ?? '',
      arrival_scheduled_start_time: toTimeInput(current.arrival_scheduled_start_time),
      arrival_scheduled_end_time: toTimeInput(current.arrival_scheduled_end_time),
      pax: current.pax != null ? String(current.pax) : '',
      course_name: current.course_name ?? '',
      course_minutes: current.course_minutes != null ? String(current.course_minutes) : '',
      extension_minutes: current.extension_minutes != null ? String(current.extension_minutes) : '',
      fee_lines: storeEditableFeeLines(current.fee_lines),
      location_address: current.location_address ?? '',
      location_building: current.location_building ?? '',
      carrier: current.carrier ?? '',
      media_name: current.media_name ?? '',
      remarks: current.remarks ?? '',
      cast_driver_message: current.cast_driver_message ?? '',
      contact_name: current.contact_name ?? '',
      contact_phone_number: current.contact_phone_number ?? '',
    });
    setHasSeeded(true);
  }, [current, reset]);

  // 取得は最初のレンダーより後に着く。播き終えるまでフォームを出さない（空欄のまま保存できてしまう）
  const seeded = current !== null && hasSeeded;
  const linked = current?.customer_id != null;

  const submit = async (values: OrderEditFormValues) => {
    if (current === null) {
      return;
    }
    // 文字列の項目は空欄をそのまま空文字で送る。契約は null だけを「変更しない」と読むので、これが
    // 「空にする」の表し方になる（trim するのは、空白だけの入力を空と同じに扱うため）。
    const edited: OrderUpdateRequest = {
      business_date: optionalDate(values.business_date),
      arrival_scheduled_start_time: optionalTime(values.arrival_scheduled_start_time),
      arrival_scheduled_end_time: optionalTime(values.arrival_scheduled_end_time),
      pax: optionalNumber(values.pax),
      course_name: values.course_name.trim(),
      course_minutes: optionalNumber(values.course_minutes),
      extension_minutes: optionalNumber(values.extension_minutes),
      location_address: values.location_address.trim(),
      location_building: values.location_building.trim(),
      carrier: values.carrier.trim(),
      media_name: values.media_name.trim(),
      remarks: values.remarks.trim(),
      cast_driver_message: values.cast_driver_message.trim(),
      // 顧客が着いた受注へ送るとサーバが撥ねる。着いていない受注でだけ運ぶ
      ...(linked
        ? {}
        : {
            contact_name: values.contact_name.trim(),
            contact_phone_number: values.contact_phone_number.trim(),
          }),
    };
    const request: OrderUpdateRequest = {
      // 指名と受付担当は触っていなくても毎回運ぶ。省略は「変更しない」ではなく「外す」と区別できないため、
      // 設定済みの受注では要求そのものが撥ねられる（サーバ側の契約）。欄が空のまま
      // （未設定で確定した受注）なら元の値をそのまま返し、この画面が外す口にならないようにする。
      receptionist_id: values.receptionist_id
        ? Number(values.receptionist_id)
        : current.receptionist_id,
      cast_id: values.cast_id || current.cast_id,
      ...pickEdited(edited, dirtyFields),
      // 明細は行の集合なので、他の欄のような「触ったか」の真偽が付かない。播いた内容と
      // 突き合わせて、変わったときだけ運ぶ（送れば内訳ごと差し替わるため）。
      ...(feeLinesChanged(values.fee_lines, storeEditableFeeLines(current.fee_lines))
        ? { fee_lines: toFeeLineInputs(values.fee_lines) }
        : {}),
    };
    try {
      await orderApi.update(orderId, request);
      notify.success('受注を更新しました');
      router.push(storePath(storeId, '/orders'));
    } catch (error) {
      // 終端状態の凍結など、サーバは対処の分かる文言を返す。汎用文言に潰さない
      notify.error(getApiErrorMessage(error, '受注の更新に失敗しました'));
    }
  };

  const label = current === null ? null : customerLabel(current);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-foreground text-2xl font-bold">受注の編集</h1>
        {/* 取れていない間は名乗らせない。「お客様名なし」は顧客の着いていない受注を指す文言で、
            まだ読めていない受注や存在しない受注に出すと、失敗の姿と区別が付かなくなる */}
        {current !== null && (
          <p className="text-muted-foreground mt-1 text-sm">
            {customerHeadingText(current)}
            {current.business_date ? ` ・ ${current.business_date}` : ''}
          </p>
        )}
      </div>

      {isLoading && <p className="text-muted-foreground text-sm">読み込み中...</p>}
      {/* 取得に失敗した領域を空のフォームに見せない（空欄を保存すると内容を消してしまう） */}
      {failure === 'error' && (
        <RegionError message="受注を取得できませんでした。" onRetry={() => void reload()} />
      )}
      {failure === 'notFound' && (
        // 404 は何度押しても取れないので再試行ではなく、一覧へ戻る導線だけを出す
        <RegionError
          message="この受注は見つかりませんでした。"
          fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
        />
      )}

      {seeded && (
        <Form {...form}>
          <form onSubmit={handleSubmit(submit)} className="space-y-6">
            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">担当</h2>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={control}
                  name="receptionist_id"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>受付</FormLabel>
                      <Select
                        items={receptionistItems}
                        value={field.value ? field.value : SELECT_NONE}
                        onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                      >
                        <FormControl>
                          <SelectTrigger className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {receptionistItems.map(o => (
                            <SelectItem key={o.value} value={o.value}>
                              {o.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {/* 候補が取れなくても編集は塞がない（元の受付担当がそのまま運ばれる） */}
                      {receptionistsFailure !== null && (
                        <RegionError
                          message="受付担当者の取得に失敗しました"
                          onRetry={() => void loadReceptionists()}
                        />
                      )}
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="cast_id"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <CastSearchCombobox
                          id="edit-cast"
                          label="指名"
                          castName={current.cast_name ?? ''}
                          onChange={field.onChange}
                          triggerRef={field.ref}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">日時</h2>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={control}
                  name="business_date"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>営業日</FormLabel>
                      <FormControl>
                        <Input type="date" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <div className="grid grid-cols-2 gap-2">
                  <FormField
                    control={control}
                    name="arrival_scheduled_start_time"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>到着予定（開始）</FormLabel>
                        <FormControl>
                          <Input type="time" {...field} />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={control}
                    name="arrival_scheduled_end_time"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>到着予定（終了）</FormLabel>
                        <FormControl>
                          <Input type="time" {...field} />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </div>
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">コース</h2>
              <div className="grid grid-cols-3 gap-4">
                <FormField
                  control={control}
                  name="pax"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>人数</FormLabel>
                      <FormControl>
                        <Input type="number" min={1} {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="course_minutes"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>コース（分）</FormLabel>
                      <FormControl>
                        <Input type="number" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="extension_minutes"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>延長（分）</FormLabel>
                      <FormControl>
                        <Input type="number" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="course_name"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>コース名</FormLabel>
                      <FormControl>
                        <Input type="text" maxLength={255} {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
              <OrderFeeLinesField
                systemLines={systemOwnedFeeLines(current?.fee_lines)}
                courseName={watch('course_name')}
              />
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">場所</h2>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={control}
                  name="location_address"
                  render={({ field }) => (
                    <FormItem className="col-span-2">
                      <FormLabel>住所</FormLabel>
                      <FormControl>
                        <Input type="text" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="location_building"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>建物</FormLabel>
                      <FormControl>
                        <Input type="text" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">媒体</h2>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={control}
                  name="carrier"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>キャリア</FormLabel>
                      <FormControl>
                        <Input type="text" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="media_name"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>知った媒体</FormLabel>
                      <FormControl>
                        <Input type="text" {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">その他</h2>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={control}
                  name="remarks"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>備考</FormLabel>
                      <FormControl>
                        <Textarea rows={2} {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="cast_driver_message"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>キャスト・ドライバーへのメッセージ</FormLabel>
                      <FormControl>
                        <Textarea rows={2} {...field} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-muted-foreground text-sm font-medium">顧客</h2>
              {linked ? (
                // 台帳の項目はここから直せない。受注 1 件のつもりの訂正が同じ顧客の他の受注へ波及する
                <div className="flex items-center justify-between rounded-lg border p-4">
                  <span className="text-foreground text-sm">{label?.name ?? 'お客様名なし'}</span>
                  <Button
                    variant="ghost"
                    size="sm"
                    render={
                      <Link
                        href={storePath(storeId, `/customers/${current.customer_id}`)}
                        target="_blank"
                      />
                    }
                  >
                    顧客詳細を開く
                    <ExternalLinkIcon aria-hidden="true" />
                  </Button>
                </div>
              ) : (
                // 顧客が着いていない受注では、録入された連絡先が唯一の名乗りなのでここでしか直せない
                <div className="grid grid-cols-2 gap-4">
                  <p className="text-muted-foreground col-span-2 text-xs">
                    台帳の顧客に着いていない受注です{UNLINKED_NOTE}
                    。ここで直せるのは受付で録入した連絡先だけで、 台帳への登録は行われません。
                  </p>
                  <FormField
                    control={control}
                    name="contact_name"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>お客様名</FormLabel>
                        <FormControl>
                          <Input type="text" {...field} />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={control}
                    name="contact_phone_number"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>電話番号</FormLabel>
                        <FormControl>
                          <Input type="text" {...field} />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </div>
              )}
            </section>

            <div className="flex justify-end gap-4">
              <Button
                type="button"
                variant="outline"
                disabled={formState.isSubmitting}
                onClick={() => router.push(storePath(storeId, '/orders'))}
              >
                キャンセル
              </Button>
              <Button type="submit" disabled={formState.isSubmitting}>
                {formState.isSubmitting ? '保存中...' : '保存'}
              </Button>
            </div>
          </form>
        </Form>
      )}
    </div>
  );
}
