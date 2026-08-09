'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { OrderReceptionist, ReceptionRoute, orderApi } from '@/entities/order';
import { CastSearchCombobox } from './CastSearchCombobox';
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
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

// 空選択を表す番兵値。フォームが持つ値は従来どおり空文字に戻す。
// onValueChange 側で '' に戻すことで送信ペイロードは従来どおり空文字になる。
const SELECT_NONE = '__none__';

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
const RECEPTION_ROUTE_OPTIONS = [
  { value: 'PHONE', label: '電話受付' },
  { value: 'WEB', label: 'Web 申請' },
];
const DISCOUNT_OPTIONS = [
  { value: SELECT_NONE, label: 'なし' },
  { value: '一番最初割', label: '一番最初割' },
];

export interface OrderFormData {
  receptionistId: string;
  businessDate: string;
  arrivalStartTime: string;
  arrivalEndTime: string;
  customerName: string;
  phoneNumber: string;
  phoneNumber2: string;
  address: string;
  buildingName: string;
  classification: string;
  landmark: string;
  hasPet: boolean;
  castId: string;
  pax: number;
  receptionRoute: ReceptionRoute;
  courseMinutes: number;
  extensionMinutes: number;
  options: string[];
  discountName: string;
  manualDiscount: number;
  carrier: string;
  mediaName: string;
  usedPoints: number;
  manualGrantPoints: number;
  remarks: string;
  castDriverMessage: string;
  ngType: string;
  ngContent: string;
}

interface OrderFormProps {
  initialData?: Partial<OrderFormData>;
  /**
   * 指名キャストの表示名。コンボボックスの初期表示にだけ使い、選択を動かすのは候補のクリックだけ。
   *
   * 送信ペイロード（{@link OrderFormData}）ではなく別の props なのは、フォームの値ではなく表示だけの
   * データだから。名前を呼び出し側から受け取るのは、キャスト管理の詳細（CAST_MANAGE）を引かずに
   * 済ませるため — 受注の応答が cast_name を持っているので取り直す必要が無い。
   */
  castName?: string;
  onSubmit: (data: OrderFormData) => void;
  isSubmitting?: boolean;
}

export function OrderForm({ initialData, castName, onSubmit, isSubmitting }: OrderFormProps) {
  const router = useRouter();
  const form = useForm<OrderFormData>({
    defaultValues: {
      receptionistId: '',
      businessDate: new Date().toISOString().split('T')[0],
      classification: 'ーー',
      pax: 1,
      receptionRoute: 'PHONE',
      courseMinutes: 60,
      extensionMinutes: 0,
      discountName: '',
      manualDiscount: 0,
      usedPoints: 0,
      manualGrantPoints: 0,
      hasPet: false,
      ngType: 'NG無し',
      ...initialData,
    },
  });
  const { register, handleSubmit, control } = form;

  const [receptionistOptions, setReceptionistOptions] = useState<OrderReceptionist[]>([]);
  const [receptionistsFailed, setReceptionistsFailed] = useState(false);
  const receptionistItems = [
    { value: SELECT_NONE, label: '－－－' },
    ...receptionistOptions
      .filter(o => o.id !== undefined)
      .map(o => ({ value: String(o.id), label: o.display_name ?? '' })),
  ];
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する。失敗が
  // 選択肢をクリアするので、在途の古い失敗が新しい成功を消し得る
  const requestIdRef = useRef(0);

  // 再試行から呼び直せるよう effect の外に置く。取り直しの前に失敗を畳むのは、同じ姿のまま
  // 失敗を繰り返すと role="alert" が挿入されず二度目の失敗が読み上げ利用者に届かないため
  const loadReceptionists = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setReceptionistsFailed(false);
    try {
      const receptionists = await orderApi.listReceptionists();
      if (requestId === requestIdRef.current) {
        setReceptionistOptions(receptionists);
        setReceptionistsFailed(false);
      }
    } catch {
      // 選択肢が「－－－」だけの状態は「受付が 1 人も居ない」と区別がつかない
      if (requestId === requestIdRef.current) {
        setReceptionistOptions([]);
        setReceptionistsFailed(true);
      }
    }
  }, []);

  useEffect(() => {
    void loadReceptionists();
  }, [loadReceptionists]);

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* 1. 基本情報 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              基本情報
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <FormField
                control={control}
                name="receptionistId"
                // 受付はサーバ側が @NotNull。未選択のまま送ると 400 になるため送信前に止める
                rules={{ required: '受付を選択してください' }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>受付 *</FormLabel>
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
                    {/* 送信は塞がない。選べないまま押せば上の rules が欄の傍で止める */}
                    {receptionistsFailed && (
                      <RegionError
                        message="受付担当者の取得に失敗しました"
                        onRetry={() => void loadReceptionists()}
                      />
                    )}
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className="grid gap-2">
                <Label htmlFor="businessDate">営業日</Label>
                <Input id="businessDate" type="date" {...register('businessDate')} />
              </div>
            </div>

            <div className="grid gap-2">
              <Label>到着予定時刻</Label>
              <div className="flex items-center gap-2">
                <Input type="time" className="w-fit" {...register('arrivalStartTime')} />
                <span className="text-muted-foreground">～</span>
                <Input type="time" className="w-fit" {...register('arrivalEndTime')} />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 2. お客様情報 */}
        <Card>
          <CardHeader>
            <CardTitle role="heading" aria-level={2}>
              お客様情報
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="grid gap-2">
                <Label htmlFor="customerName">お客様名</Label>
                <Input id="customerName" type="text" {...register('customerName')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="phoneNumber">電話番号</Label>
                <Input id="phoneNumber" type="text" {...register('phoneNumber')} />
              </div>
              <div className="grid gap-2 md:col-span-2">
                <Label htmlFor="address">住所</Label>
                <Input id="address" type="text" {...register('address')} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="buildingName">建物</Label>
                <Input id="buildingName" type="text" {...register('buildingName')} />
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
                name="hasPet"
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

        {/* 3. コース・料金 */}
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
                name="castId"
                rules={{ required: 'キャストを候補から選択してください' }}
                render={({ field }) => (
                  <FormItem>
                    <CastSearchCombobox
                      id="castName"
                      label="キャスト *"
                      castName={castName ?? ''}
                      onChange={field.onChange}
                    />
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="courseMinutes"
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
                name="receptionRoute"
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
                <Label htmlFor="extensionMinutes">延長</Label>
                <Input id="extensionMinutes" type="number" {...register('extensionMinutes')} />
              </div>
              <FormField
                control={control}
                name="discountName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>割引</FormLabel>
                    <Select
                      items={DISCOUNT_OPTIONS}
                      value={field.value ? field.value : SELECT_NONE}
                      onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {DISCOUNT_OPTIONS.map(o => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )}
              />
            </div>
          </CardContent>
        </Card>

        {/* 4. その他 */}
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
                <Label htmlFor="castDriverMessage">キャスト・ドライバーへのメッセージ</Label>
                <Textarea id="castDriverMessage" rows={3} {...register('castDriverMessage')} />
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
