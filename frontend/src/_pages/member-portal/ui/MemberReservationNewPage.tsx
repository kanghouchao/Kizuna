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
  const [casts, setCasts] = useState<ConfirmedShiftCast[]>([]);
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
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
    if (!domain) {
      setStoreResolved(true);
      return;
    }
    let cancelled = false;
    platformStoreApi
      .lookupByDomain(domain)
      .then(resolved => {
        if (!cancelled) setStore(resolved);
      })
      .catch(() => {
        if (!cancelled) setStore(null);
      })
      .finally(() => {
        if (!cancelled) setStoreResolved(true);
      });
    return () => {
      cancelled = true;
    };
  }, [domain]);

  const storeId = store?.id ? Number(store.id) : null;

  useEffect(() => {
    if (storeId === null || !businessDate) {
      setCasts([]);
      return;
    }
    let cancelled = false;
    shiftApi
      .confirmedCasts({ store_id: storeId, date: businessDate })
      .then(list => {
        if (!cancelled) setCasts(list);
      })
      .catch(() => {
        if (!cancelled) setCasts([]);
      });
    return () => {
      cancelled = true;
    };
  }, [storeId, businessDate]);

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
                {businessDate && casts.length === 0 && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    この日に出勤予定のキャストはいません。
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="remarks">ご要望（任意）</Label>
                <Textarea id="remarks" rows={3} {...register('remarks', { maxLength: 500 })} />
              </div>
              <Button type="submit" className="w-full" disabled={submitting}>
                この内容で申請する
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
      <Button asChild variant="outline" className="mt-6 w-full">
        <Link href="/member/reservations/">予約一覧へ</Link>
      </Button>
    </div>
  );
}
