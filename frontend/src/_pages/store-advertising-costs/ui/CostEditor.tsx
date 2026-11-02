import { useState } from 'react';
import { useForm } from 'react-hook-form';
import Link from 'next/link';
import { mediumApi } from '@/entities/medium';
import { storePath, useResource } from '@/shared/lib';
import { categoryLabels, type Category, type Cost, type Mutation } from '../api/advertising';
import {
  Button,
  RegionError,
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
  ConfirmDialog,
} from '@/shared/ui';
interface Values {
  category: Category;
  medium_id: string;
  agency_name: string;
  plan_name: string;
  inquiry_count: number | null;
  amount: number | null;
  reason: string;
}
export function CostEditor({
  cost,
  remove,
  disabled,
  onSubmit,
}: {
  cost?: Cost;
  remove?: boolean;
  disabled: boolean;
  onSubmit: (m: Mutation) => Promise<void>;
}) {
  const [confirmation, setConfirmation] = useState<Mutation | null>(null);
  const options = useResource(remove ? null : () => mediumApi.options(), []);
  // 停止中の媒体は選択肢に出ないため、編集中の行が持つ媒体は値を保てるよう候補へ足す。
  const current = cost?.medium;
  const mediumItems = (options.data ?? []).map(o => ({ value: o.id, label: o.name }));
  if (current && !mediumItems.some(o => o.value === current.id))
    mediumItems.push({
      value: current.id,
      label: options.data ? `${current.name}（停止中）` : current.name,
    });
  const form = useForm<Values>({
    defaultValues: {
      category: cost?.category ?? 'SALES',
      medium_id: cost?.medium.id ?? '',
      agency_name: cost?.agency_name ?? '',
      plan_name: cost?.plan_name ?? '',
      inquiry_count: cost?.inquiry_count ?? null,
      amount: cost?.amount ?? null,
      reason: '',
    },
  });
  const integer = (n: number | null) =>
    n === null ||
    (Number.isInteger(n) && n >= 0 && n <= 2147483647) ||
    '0〜2,147,483,647の整数で入力してください';
  const submit = form.handleSubmit(async v => {
    const reason = v.reason.trim();
    if (remove && cost) {
      setConfirmation({ kind: 'delete', id: cost.id, version: cost.version, reason });
      return;
    }
    const values = {
      category: v.category,
      medium_id: v.medium_id,
      agency_name: v.agency_name.trim() || null,
      plan_name: v.plan_name.trim() || null,
      inquiry_count: v.inquiry_count,
      amount: v.amount!,
    };
    await onSubmit(
      cost
        ? { kind: 'replace', id: cost.id, version: cost.version, values, reason }
        : { kind: 'create', values }
    );
  });
  return (
    <Form {...form}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {remove ? (
          <p>
            「{cost?.medium.name}
            」の広告費を一覧と集計から除きます。変更前の内容と理由は履歴に残ります。
          </p>
        ) : (
          <>
            <FormField
              control={form.control}
              name="category"
              rules={{ required: '区分を選択してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>区分</FormLabel>
                  <Select
                    required
                    value={field.value}
                    onValueChange={field.onChange}
                    items={categoryLabels}
                    disabled={disabled}
                  >
                    <FormControl>
                      <SelectTrigger ref={field.ref}>
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {Object.entries(categoryLabels).map(([v, l]) => (
                        <SelectItem key={v} value={v}>
                          {l}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="medium_id"
              rules={{ required: '媒体を選択してください' }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>媒体</FormLabel>
                  <Select
                    required
                    value={field.value || null}
                    onValueChange={v => field.onChange(v ?? '')}
                    items={mediumItems}
                    disabled={disabled || options.isLoading}
                  >
                    <FormControl>
                      <SelectTrigger ref={field.ref} className="w-full min-w-0">
                        <SelectValue
                          placeholder={options.isLoading ? '読み込み中...' : '媒体を選択'}
                        />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent className="max-w-(--available-width)">
                      {mediumItems.map(o => (
                        <SelectItem key={o.value} value={o.value} className="break-all">
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {options.failure ? (
                    <RegionError
                      message="媒体の選択肢を取得できませんでした。"
                      onRetry={() => void options.reload()}
                    />
                  ) : (
                    options.data &&
                    mediumItems.length === 0 && (
                      <div className="text-sm">
                        <p>選択できる媒体がありません。媒体管理で登録してから選んでください。</p>
                        <Button
                          variant="link"
                          className="px-0"
                          render={<Link href={storePath('/advertising-costs/media')} />}
                        >
                          媒体を管理
                        </Button>
                      </div>
                    )
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />
            {(['agency_name', 'plan_name'] as const).map((name, i) => (
              <FormField
                key={name}
                control={form.control}
                name={name}
                rules={{
                  maxLength: { value: 200, message: '200文字以内で入力してください' },
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{['広告会社（任意）', 'プラン（任意）'][i]}</FormLabel>
                    <FormControl>
                      <Input {...field} maxLength={200} disabled={disabled} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ))}
            {(['inquiry_count', 'amount'] as const).map(name => (
              <FormField
                key={name}
                control={form.control}
                name={name}
                rules={{
                  validate: v =>
                    name === 'amount' && v === null ? '金額を入力してください' : integer(v),
                }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      {name === 'amount' ? '金額（円）' : '問い合わせ人数（未計測は空欄）'}
                    </FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        min={0}
                        max={2147483647}
                        step={1}
                        required={name === 'amount'}
                        name={field.name}
                        ref={field.ref}
                        onBlur={field.onBlur}
                        value={field.value ?? ''}
                        onChange={e =>
                          field.onChange(
                            Number.isNaN(e.target.valueAsNumber) ? null : e.target.valueAsNumber
                          )
                        }
                        disabled={disabled}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ))}
          </>
        )}
        {cost && (
          <FormField
            control={form.control}
            name="reason"
            rules={{
              validate: v => !!v.trim() || '理由を入力してください',
              maxLength: { value: 500, message: '500文字以内で入力してください' },
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>理由</FormLabel>
                <FormControl>
                  <Textarea {...field} maxLength={500} required disabled={disabled} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
        <Button
          type="submit"
          disabled={disabled || form.formState.isSubmitting || options.isLoading}
          variant={remove ? 'destructive' : 'default'}
        >
          {remove ? '削除内容を確認' : '保存する'}
        </Button>
      </form>
      <ConfirmDialog
        open={confirmation !== null}
        title="この広告費を削除しますか？"
        description="変更前の内容と入力した理由は履歴に残ります。"
        onClose={() => setConfirmation(null)}
        onConfirm={() => {
          if (confirmation && !disabled) void onSubmit(confirmation);
        }}
      />
    </Form>
  );
}
