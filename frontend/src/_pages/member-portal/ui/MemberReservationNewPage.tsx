'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { useRouter } from 'next/navigation';
import { memberOrderApi } from '@/entities/order';
import { ConfirmedShiftCast, shiftApi } from '@/entities/shift';
import { Store, platformStoreApi } from '@/entities/store';
import { isNotFound } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Textarea,
} from '@/shared/ui';

interface ReservationFormValues {
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

  const [store, setStore] = useState<Store | null>(null);
  const [storeResolved, setStoreResolved] = useState(false);
  // 照会そのものの失敗。「その店舗が無い」と同じ表示にすると、瞬断のときに再試行する手段が無くなる。
  const [storeLookupFailed, setStoreLookupFailed] = useState(false);
  // 取得失敗時の再試行用。ドメインが同じままでも effect を引き直せるようにする。
  const [storeReloadNonce, setStoreReloadNonce] = useState(0);
  const [casts, setCasts] = useState<ConfirmedShiftCast[]>([]);
  const [castsLoading, setCastsLoading] = useState(false);
  const [castsFailed, setCastsFailed] = useState(false);
  // 取得失敗時の再試行用。日付・店舗が同じままでも effect を引き直せるようにする。
  const [castsReloadNonce, setCastsReloadNonce] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<ReservationFormValues>({
    defaultValues: {
      business_date: '',
      arrival_scheduled_start_time: '',
      pax: 1,
      cast_id: '',
      remarks: '',
    },
  });

  const businessDate = watch('business_date');

  useEffect(() => {
    // ドメインが変わった時点で前の店舗を捨てる。照会の完了を待つと、その間だけ前の店舗宛の
    // フォームが送信可能なまま残る。
    setStore(null);
    setStoreLookupFailed(false);
    if (!domain) {
      setStoreResolved(true);
      return;
    }
    setStoreResolved(false);
    let cancelled = false;
    platformStoreApi
      .lookupByDomain(domain)
      .then(resolved => {
        if (!cancelled) setStore(resolved);
      })
      .catch(error => {
        // 「そのドメインの店舗が無い」（404）と照会自体の失敗を区別する。後者は再試行で復帰しうる。
        if (!cancelled && !isNotFound(error)) setStoreLookupFailed(true);
      })
      .finally(() => {
        if (!cancelled) setStoreResolved(true);
      });
    return () => {
      cancelled = true;
    };
  }, [domain, storeReloadNonce]);

  const storeId = store?.id ? Number(store.id) : null;

  useEffect(() => {
    // 候補はその日その店舗の確定シフトに紐づくため、日付・店舗が変わった時点で選択も候補も捨てる。
    // 非同期の取得完了を待つと、その間だけ前の日付のキャストを指名したまま送信できてしまう。
    setCasts([]);
    setCastsFailed(false);
    setValue('cast_id', '');
    if (storeId === null || !businessDate) {
      // 取得するものが無い＝待っているものも無い。読み込み中として送信を止めない。
      setCastsLoading(false);
      return;
    }
    setCastsLoading(true);
    let cancelled = false;
    shiftApi
      .confirmedCasts({ store_id: storeId, date: businessDate })
      .then(list => {
        if (!cancelled) setCasts(list);
      })
      .catch(() => {
        // 取得失敗を空候補と区別する — 「出勤なし」に見せると、指名するつもりの会員が
        // 誤った空き情報のまま指名なしで申請してしまう
        if (!cancelled) setCastsFailed(true);
      })
      .finally(() => {
        if (!cancelled) setCastsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [storeId, businessDate, setValue, castsReloadNonce]);

  const submit = async (values: ReservationFormValues) => {
    if (storeId === null) return;
    setSubmitting(true);
    try {
      await memberOrderApi.create({
        store_id: storeId,
        business_date: values.business_date,
        pax: Number(values.pax),
        arrival_scheduled_start_time: values.arrival_scheduled_start_time || undefined,
        cast_id: values.cast_id || undefined,
        remarks: values.remarks || undefined,
      });
      toast.success('予約を申請しました');
      router.push('/member/reservations/');
    } catch {
      toast.error('予約の申請に失敗しました');
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
          {!storeResolved ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : storeLookupFailed ? (
            <div className="space-y-2">
              <p className="text-sm text-destructive-strong">店舗情報を取得できませんでした。</p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setStoreReloadNonce(nonce => nonce + 1)}
              >
                再読み込み
              </Button>
            </div>
          ) : !store ? (
            <p className="text-sm text-muted-foreground">
              店舗が特定できませんでした。店舗公式サイトの予約ボタンからお進みください。
            </p>
          ) : (
            <form onSubmit={handleSubmit(submit)} className="space-y-4">
              <div>
                <Label htmlFor="business_date">利用日</Label>
                <Input
                  id="business_date"
                  type="date"
                  {...register('business_date', { required: '利用日を選択してください' })}
                />
                {errors.business_date && (
                  <p className="mt-1 text-xs text-destructive-strong">
                    {errors.business_date.message}
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="arrival_scheduled_start_time">希望時刻</Label>
                <Input
                  id="arrival_scheduled_start_time"
                  type="time"
                  {...register('arrival_scheduled_start_time')}
                />
              </div>
              <div>
                <Label htmlFor="pax">人数</Label>
                <Input
                  id="pax"
                  type="number"
                  min={1}
                  {...register('pax', {
                    required: '人数を入力してください',
                    min: { value: 1, message: '人数は 1 以上です' },
                  })}
                />
                {errors.pax && (
                  <p className="mt-1 text-xs text-destructive-strong">{errors.pax.message}</p>
                )}
              </div>
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
                  <p className="mt-1 text-xs text-muted-foreground">出勤情報を確認しています...</p>
                )}
                {businessDate && !castsLoading && castsFailed && (
                  <div className="mt-1 flex items-center gap-2">
                    <p className="text-xs text-destructive-strong">
                      出勤情報を取得できませんでした。
                    </p>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setCastsReloadNonce(nonce => nonce + 1)}
                    >
                      再読み込み
                    </Button>
                  </div>
                )}
                {/* 取得が終わるまで「出勤なし」と言い切らない — 未解決の間に空表示を出すと、
                    指名するつもりの会員が誤った空き情報のまま指名なしで申請してしまう。 */}
                {businessDate && !castsLoading && !castsFailed && casts.length === 0 && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    この日に出勤予定のキャストはいません。
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="remarks">ご要望（任意）</Label>
                <Textarea
                  id="remarks"
                  rows={3}
                  {...register('remarks', {
                    maxLength: { value: 500, message: 'ご要望は 500 文字以内で入力してください' },
                  })}
                />
                {errors.remarks && (
                  <p className="mt-1 text-xs text-destructive-strong">{errors.remarks.message}</p>
                )}
              </div>
              {/* 候補が確定するまで送信させない。取得中も失敗中も「その日に誰が出勤するか」は
                  未確定であり、指名を選ぶ機会が無いまま「指名なし」で申請が通ってしまう。
                  失敗のときは再読み込みが復帰の手段になる。 */}
              <Button
                type="submit"
                className="w-full"
                disabled={submitting || castsLoading || castsFailed}
              >
                この内容で申請する
              </Button>
            </form>
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
