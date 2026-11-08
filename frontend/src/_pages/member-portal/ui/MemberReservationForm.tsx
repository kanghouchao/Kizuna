'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { memberOrderApplicationApi } from '@/entities/order';
import { ConfirmedShiftCast, shiftApi } from '@/entities/shift';
import { formatTime, integerRule, useResource } from '@/shared/lib';
import {
  Button,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

// 会員の段（本文 18px・押せる領域 48px）。Input は md 以上で text-sm に縮むので明示して止める。
const FIELD = 'h-12 text-lg md:text-lg';

// Select の値は空文字を「未選択」と区別できないので、指名なしには専用の値を置く。
const NO_CAST = '__none__';

interface ReservationFormValues {
  declared_name: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  pax: number;
  cast_id: string;
  remarks: string;
}

/** 予約申請のフォーム。宛先の店舗は呼び出し側が解決して渡し、指名候補はその日の確定シフトから引く。 */
export function MemberReservationForm({
  storeId,
  storeName,
  onChangeStore,
}: {
  storeId: number;
  storeName: string;
  /** 渡すと見出しの横に「店舗を選び直す」を出す。 */
  onChangeStore?: () => void;
}) {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const form = useForm<ReservationFormValues>({
    defaultValues: {
      declared_name: '',
      business_date: '',
      arrival_scheduled_start_time: '',
      pax: 1,
      cast_id: '',
      remarks: '',
    },
  });
  const { handleSubmit, setValue, control } = form;

  const businessDate = useWatch({ control, name: 'business_date' });

  const castsRequested = businessDate !== '';
  const {
    data: castsData,
    isLoading: castsLoading,
    failure: castsFailure,
    reload: reloadCasts,
  } = useResource(
    castsRequested
      ? () => shiftApi.confirmedCasts({ store_id: storeId, date: businessDate })
      : null,
    [storeId, businessDate]
  );
  // 候補はその日その店舗の確定シフトに紐づく。日付は頁に留まったまま何度でも変わり、フックは
  // 次が届くまで前の値を持ち続けるので、取得中も読み込み表示だけで済ませると、選択肢には前の
  // 日付のキャストが並び続ける（選べてしまう）。取りに行っていない間・待っている間は空にする。
  const casts: ConfirmedShiftCast[] = castsRequested && !castsLoading ? (castsData ?? []) : [];
  const castItems = [
    { value: NO_CAST, label: '指名なし' },
    ...casts.map(cast => ({
      value: cast.cast_id,
      label: `${cast.cast_name}${cast.start_time ? `（${formatTime(cast.start_time)}〜）` : ''}`,
    })),
  ];

  useEffect(() => {
    // 候補が入れ替わる契機では選択も捨てる。残すと、その日に出勤しないキャストの指名が通る。
    // 店舗の入れ替わりは呼び出し側がフォームごと作り直す（key）ので、ここでは日付だけを見る。
    setValue('cast_id', '');
  }, [businessDate, setValue]);

  const submit = async (values: ReservationFormValues) => {
    setSubmitting(true);
    try {
      await memberOrderApplicationApi.create({
        store_id: storeId,
        declared_name: values.declared_name,
        business_date: values.business_date,
        pax: Number(values.pax),
        arrival_scheduled_start_time: values.arrival_scheduled_start_time || undefined,
        cast_id: values.cast_id || undefined,
        remarks: values.remarks || undefined,
      });
      notify.success('予約を申請しました');
      // 成功時は送信中のまま残す。遷移が終わるまでフォームは見えていて、戻すと同じ申請を二度送れる
      router.push('/member/reservations/');
    } catch {
      notify.error('予約の申請に失敗しました');
      setSubmitting(false);
    }
  };

  return (
    <section aria-labelledby="reservation-store" className="space-y-4">
      {/* 題名帯は「予約申請」。どの店舗への申請かはフォームの見出しで名乗る */}
      {/* 見出しの横に並べると、長い店舗名が細く折り返されるので下に置く */}
      <div className="space-y-2">
        <h2 id="reservation-store" className="text-xl font-semibold wrap-anywhere">
          {storeName}
        </h2>
        {onChangeStore && (
          <Button
            type="button"
            variant="outline"
            className="min-h-12 text-lg"
            onClick={onChangeStore}
          >
            店舗を選び直す
          </Button>
        )}
      </div>
      <Form {...form}>
        {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
            我々の文言は永久に描かれない。人数の min={1} は下の min 規則が引き継ぐ */}
        {/* 欄の名前と検証の文言は既定で 14px。会員の段の本文に合わせて大きくする */}
        <form
          onSubmit={handleSubmit(submit)}
          className="space-y-4 [&_[data-slot=form-message]]:text-base [&_label]:text-lg"
          noValidate
        >
          {/* 店舗が知る名前はここで名乗る名前だけ。ご登録の表示名・メールは店舗へ渡らない */}
          <FormField
            control={control}
            name="declared_name"
            rules={{
              required: '店舗へ名乗るお名前を入力してください',
              maxLength: { value: 255, message: 'お名前は 255 文字以内で入力してください' },
              // required は空白だけの入力を「入っている」と見る。サーバ側の @NotBlank が
              // 弾く形をそのまま通すと、欄の傍ではなく送信失敗の通知として返ってくる。
              validate: value => value.trim().length > 0 || '店舗へ名乗るお名前を入力してください',
            }}
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel>店舗へ名乗るお名前</FormLabel>
                <FormControl>
                  <Input required className={FIELD} {...field} />
                </FormControl>
                {/* 名乗りが台帳に載るのは、その店舗に自分の記録がまだ無いときだけ（既にあれば
                    店舗が持つ記録の氏名がそのまま使われる）。「必ず店舗に伝わる」と読める文言は
                    置かない */}
                <FormDescription className="text-base">
                  初めてのご利用なら、このお名前で店舗の台帳に登録されます。ご登録の表示名・メールアドレスは店舗に伝わりません。
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={control}
            name="business_date"
            rules={{ required: '利用日を選択してください' }}
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel>利用日</FormLabel>
                <FormControl>
                  <Input type="date" required className={FIELD} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={control}
            name="arrival_scheduled_start_time"
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel>希望時刻</FormLabel>
                <FormControl>
                  <Input type="time" className={FIELD} {...field} />
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={control}
            name="pax"
            rules={{
              required: '人数を入力してください',
              min: { value: 1, message: '人数は 1 以上です' },
              // noValidate は type="number" の暗黙の step=1 も止める。これが無いと 1.5 が
              // Integer の pax へ届く
              validate: integerRule('人数'),
            }}
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel>人数</FormLabel>
                <FormControl>
                  <Input type="number" min={1} required className={FIELD} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className="grid gap-1">
            <FormField
              control={control}
              name="cast_id"
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>指名（任意）</FormLabel>
                  {/* 候補はその日の確定シフトに限る */}
                  <Select
                    items={castItems}
                    value={field.value || NO_CAST}
                    onValueChange={value => {
                      if (value === null) return;
                      field.onChange(value === NO_CAST ? '' : value);
                    }}
                  >
                    <FormControl>
                      <SelectTrigger
                        className={`w-full min-w-0 ${FIELD} data-[size=default]:h-12`}
                        onBlur={field.onBlur}
                        ref={field.ref}
                      >
                        <SelectValue className="truncate" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {castItems.map(item => (
                        <SelectItem
                          key={item.value}
                          value={item.value}
                          className="min-h-12 text-lg"
                        >
                          {item.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />
            {businessDate && castsLoading && (
              <p className="text-base text-muted-foreground">出勤情報を確認しています...</p>
            )}
            {businessDate && !castsLoading && castsFailure !== null && (
              <RegionError
                message="出勤情報を取得できませんでした"
                onRetry={() => void reloadCasts()}
              />
            )}
            {/* 取得が終わるまで「出勤なし」と言い切らない — 未解決の間に空表示を出すと、
              指名するつもりの会員が誤った空き情報のまま指名なしで申請してしまう。 */}
            {businessDate && !castsLoading && castsFailure === null && casts.length === 0 && (
              <p className="text-base text-muted-foreground">
                この日に出勤予定のキャストはいません。
              </p>
            )}
          </div>
          <FormField
            control={control}
            name="remarks"
            rules={{
              maxLength: { value: 500, message: 'ご要望は 500 文字以内で入力してください' },
            }}
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel>ご要望（任意）</FormLabel>
                <FormControl>
                  <Textarea rows={3} className="text-lg md:text-lg" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {/* 候補が確定するまで送信させない。取得中も失敗中も「その日に誰が出勤するか」は
            未確定であり、指名を選ぶ機会が無いまま「指名なし」で申請が通ってしまう。
            失敗のときは再読み込みが復帰の手段になる。 */}
          <Button
            type="submit"
            className="min-h-12 w-full text-lg"
            disabled={submitting || castsLoading || castsFailure !== null}
          >
            この内容で申請する
          </Button>
        </form>
      </Form>
    </section>
  );
}
