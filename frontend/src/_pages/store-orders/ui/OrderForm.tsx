'use client';

import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { CastResponse, castApi } from '@/entities/cast';
import { OrderReceptionist, orderApi } from '@/entities/order';
import { toast } from 'react-hot-toast';
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
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

// Radix Select は value="" を許容しないため、空選択を表す番兵値。
// onValueChange 側で '' に戻すことで送信ペイロードは従来どおり空文字になる。
const SELECT_NONE = '__none__';

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
  onSubmit: (data: OrderFormData) => void;
  isSubmitting?: boolean;
}

export function OrderForm({ initialData, onSubmit, isSubmitting }: OrderFormProps) {
  const router = useRouter();
  const form = useForm<OrderFormData>({
    defaultValues: {
      receptionistId: '',
      businessDate: new Date().toISOString().split('T')[0],
      classification: 'ーー',
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
  const { register, handleSubmit, setValue, control } = form;

  const [castNameInput, setCastNameInput] = useState('');
  const [castOptions, setCastOptions] = useState<CastResponse[]>([]);
  const [isCastLoading, setIsCastLoading] = useState(false);
  const [isCastOpen, setIsCastOpen] = useState(false);
  const skipNextSearchRef = useRef(false);
  const [receptionistOptions, setReceptionistOptions] = useState<OrderReceptionist[]>([]);

  useEffect(() => {
    const loadReceptionists = async () => {
      try {
        const receptionists = await orderApi.listReceptionists();
        setReceptionistOptions(receptionists);
      } catch {
        toast.error('受付担当者の取得に失敗しました');
      }
    };

    loadReceptionists();
  }, []);

  useEffect(() => {
    const loadInitialCast = async () => {
      if (!initialData?.castId) return;
      try {
        const cast = await castApi.get(initialData.castId);
        skipNextSearchRef.current = true;
        setCastNameInput(cast.name ?? '');
      } catch {
        toast.error('キャスト名の取得に失敗しました');
      }
    };

    loadInitialCast();
  }, [initialData?.castId]);

  useEffect(() => {
    if (skipNextSearchRef.current) {
      skipNextSearchRef.current = false;
      return;
    }

    const keyword = castNameInput.trim();
    if (!keyword) {
      setCastOptions([]);
      setIsCastOpen(false);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        setIsCastLoading(true);
        const response = await castApi.list({
          size: 10,
          sort: 'displayOrder,asc',
          search: keyword,
        });
        setCastOptions(response.rows);
        setIsCastOpen(true);
      } catch {
        toast.error('キャスト候補の取得に失敗しました');
      } finally {
        setIsCastLoading(false);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [castNameInput]);

  const handleCastSelect = (cast: CastResponse) => {
    skipNextSearchRef.current = true;
    setCastNameInput(cast.name ?? '');
    setValue('castId', cast.id ?? '', { shouldValidate: true, shouldDirty: true });
    setIsCastOpen(false);
  };

  const handleCastInputChange = (value: string) => {
    setCastNameInput(value);
    setValue('castId', '', { shouldValidate: true, shouldDirty: true });
  };

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
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>受付</FormLabel>
                    <Select
                      value={field.value ? field.value : SELECT_NONE}
                      onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value={SELECT_NONE}>－－－</SelectItem>
                        {receptionistOptions.map(option => {
                          const id = option.id;
                          if (id === undefined) return null;
                          return (
                            <SelectItem key={id} value={String(id)}>
                              {option.display_name}
                            </SelectItem>
                          );
                        })}
                      </SelectContent>
                    </Select>
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
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="ーー">ーー</SelectItem>
                        <SelectItem value="自宅">自宅</SelectItem>
                        <SelectItem value="ラブホ">ラブホ</SelectItem>
                        <SelectItem value="ビジホ">ビジホ</SelectItem>
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
                      value={String(field.value)}
                      onValueChange={v => field.onChange(v === 'true')}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="false">なし</SelectItem>
                        <SelectItem value="true">あり</SelectItem>
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
              <div className="relative grid gap-2">
                <Label htmlFor="castName">キャスト</Label>
                <Input
                  id="castName"
                  type="text"
                  value={castNameInput}
                  onChange={e => handleCastInputChange(e.target.value)}
                  onFocus={() => castOptions.length > 0 && setIsCastOpen(true)}
                  placeholder="名前で検索"
                  role="combobox"
                  aria-expanded={isCastOpen}
                  aria-controls="cast-suggestions"
                  autoComplete="off"
                />
                <input type="hidden" {...register('castId')} />
                {isCastOpen && (
                  <div
                    id="cast-suggestions"
                    role="listbox"
                    className="absolute top-full z-20 mt-1 w-full rounded-md border border-border bg-popover shadow-lg"
                  >
                    {isCastLoading ? (
                      <div className="px-4 py-2 text-sm text-muted-foreground">検索中...</div>
                    ) : castOptions.length === 0 ? (
                      <div className="px-4 py-2 text-sm text-muted-foreground">
                        該当するキャストがいません
                      </div>
                    ) : (
                      <ul className="max-h-56 overflow-auto py-1">
                        {castOptions.map(cast => (
                          <li key={cast.id}>
                            <button
                              type="button"
                              onClick={() => handleCastSelect(cast)}
                              className="flex w-full items-center justify-between px-4 py-2 text-left text-sm text-foreground hover:bg-primary/10"
                              role="option"
                            >
                              <span className="font-medium">{cast.name}</span>
                              <span className="text-xs text-muted-foreground">ID: {cast.id}</span>
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </div>
              <FormField
                control={control}
                name="courseMinutes"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>ｺｰｽ(分)</FormLabel>
                    <Select
                      value={String(field.value)}
                      onValueChange={v => field.onChange(Number(v))}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="60">60</SelectItem>
                        <SelectItem value="90">90</SelectItem>
                        <SelectItem value="120">120</SelectItem>
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
                      value={field.value ? field.value : SELECT_NONE}
                      onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
                    >
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value={SELECT_NONE}>なし</SelectItem>
                        <SelectItem value="一番最初割">一番最初割</SelectItem>
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
