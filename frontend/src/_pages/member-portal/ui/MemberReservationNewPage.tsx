'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { useRouter } from 'next/navigation';
import { memberOrderApi } from '@/entities/order';
import { ConfirmedShiftCast, shiftApi } from '@/entities/shift';
import { platformStoreApi } from '@/entities/store';
import { integerRule, useResource } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Label,
  RegionError,
  Textarea,
} from '@/shared/ui';

interface ReservationFormValues {
  declared_name: string;
  business_date: string;
  arrival_scheduled_start_time: string;
  pax: number;
  cast_id: string;
  remarks: string;
}

/**
 * 会員ポータルの予約申請。
 *
 * <p>店舗は公式サイトから引き継いだドメイン（?store=）をサーバ側の公開照会に突き合わせて解決する — 画面はブラウザから渡された値を店舗 ID
 * として信用せず、照会の結果だけを使う。指名候補はその日の確定シフトから引く。
 */
export function MemberReservationNewPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const domain = searchParams.get('store');

  const [submitting, setSubmitting] = useState(false);

  const {
    data: storeData,
    isLoading: storeLoading,
    failure: storeFailure,
    reload: reloadStore,
  } = useResource(domain ? () => platformStoreApi.lookupByDomain(domain) : null, [domain]);
  // フックが持つのは「最後に取れた店舗」であって「今の ?store= の店舗」ではない。取りに行く
  // 理由が無くなっても値は残る（フックの既定 — 開き直した区画に生の id を出さないため）ので、
  // この頁では捨てる。残すと ?store= の外れた URL で前の店舗宛のフォームが送信できてしまう。
  // ドメインが別の値へ変わる場合は取得中の相（読み込み中）が引き取る。
  const store = domain ? storeData : null;
  const storeId = store?.id ? Number(store.id) : null;

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
  const { register, handleSubmit, setValue, watch, control } = form;

  const businessDate = watch('business_date');

  const castsRequested = storeId !== null && businessDate !== '';
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

  useEffect(() => {
    // 候補が入れ替わる契機では選択も捨てる。残すと、その日に出勤しないキャストの指名が通る。
    setValue('cast_id', '');
  }, [storeId, businessDate, setValue]);

  const submit = async (values: ReservationFormValues) => {
    if (storeId === null) return;
    setSubmitting(true);
    try {
      await memberOrderApi.create({
        store_id: storeId,
        declared_name: values.declared_name,
        business_date: values.business_date,
        pax: Number(values.pax),
        arrival_scheduled_start_time: values.arrival_scheduled_start_time || undefined,
        cast_id: values.cast_id || undefined,
        remarks: values.remarks || undefined,
      });
      notify.success('予約を申請しました');
      router.push('/member/reservations/');
    } catch {
      notify.error('予約の申請に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">予約申請</h1>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            {store ? store.name : '店舗'}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {storeLoading ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : storeFailure === 'error' ? (
            // 「そのドメインの店舗が無い」（404）と照会自体の失敗を区別する。後者は再試行で
            // 復帰しうるので、下の案内と同じ行き止まりに見せない。
            <RegionError
              message="店舗情報を取得できませんでした"
              onRetry={() => void reloadStore()}
            />
          ) : !store ? (
            <p className="text-sm text-muted-foreground">
              店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。
            </p>
          ) : (
            <Form {...form}>
              {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
                  我々の文言は永久に描かれない。人数の min={1} は下の min 規則が引き継ぐ */}
              <form onSubmit={handleSubmit(submit)} className="space-y-4" noValidate>
                {/* 店舗が知る名前はここで名乗る名前だけ。ご登録の表示名・メールは店舗へ渡らない */}
                <FormField
                  control={control}
                  name="declared_name"
                  rules={{
                    required: '店舗へ名乗るお名前を入力してください',
                    maxLength: { value: 255, message: 'お名前は 255 文字以内で入力してください' },
                  }}
                  render={({ field }) => (
                    <FormItem className="gap-1">
                      <FormLabel>店舗へ名乗るお名前</FormLabel>
                      <FormControl>
                        <Input required {...field} />
                      </FormControl>
                      <FormDescription>
                        この店舗にお伝えするお名前です。ご登録の表示名・メールアドレスは店舗に伝わりません。
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
                        <Input type="date" required {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div>
                  <Label htmlFor="arrival_scheduled_start_time">希望時刻</Label>
                  <Input
                    id="arrival_scheduled_start_time"
                    type="time"
                    {...register('arrival_scheduled_start_time')}
                  />
                </div>
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
                        <Input type="number" min={1} required {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div>
                  <Label htmlFor="cast_id">指名（任意）</Label>
                  {/* 指名候補はその日の確定シフトに限る。ネイティブ select を使うのは、フォーム値をそのまま扱えるため。 */}
                  <select
                    id="cast_id"
                    className="h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs"
                    {...register('cast_id')}
                  >
                    <option value="">指名なし</option>
                    {casts.map(cast => (
                      <option key={cast.cast_id} value={cast.cast_id}>
                        {cast.cast_name}
                        {cast.start_time ? `（${cast.start_time.slice(0, 5)}〜）` : ''}
                      </option>
                    ))}
                  </select>
                  {businessDate && castsLoading && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      出勤情報を確認しています...
                    </p>
                  )}
                  {businessDate && !castsLoading && castsFailure !== null && (
                    <RegionError
                      message="出勤情報を取得できませんでした"
                      onRetry={() => void reloadCasts()}
                      className="mt-1"
                    />
                  )}
                  {/* 取得が終わるまで「出勤なし」と言い切らない — 未解決の間に空表示を出すと、
                    指名するつもりの会員が誤った空き情報のまま指名なしで申請してしまう。 */}
                  {businessDate && !castsLoading && castsFailure === null && casts.length === 0 && (
                    <p className="mt-1 text-xs text-muted-foreground">
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
                        <Textarea rows={3} {...field} />
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
                  className="w-full"
                  disabled={submitting || castsLoading || castsFailure !== null}
                >
                  この内容で申請する
                </Button>
              </form>
            </Form>
          )}
        </CardContent>
      </Card>
      <Button
        render={<Link href="/member/reservations/" />}
        variant="outline"
        className="mt-6 w-full"
      >
        予約一覧へ
      </Button>
    </div>
  );
}
