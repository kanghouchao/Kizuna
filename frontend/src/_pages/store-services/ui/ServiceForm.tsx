'use client';
import { useForm, useWatch } from 'react-hook-form';
import {
  ChargeType,
  ServiceCreateRequest,
  ServiceSummary,
  serviceKindLabels,
} from '@/entities/service';
import {
  Button,
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Input,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/shared/ui';

interface Props {
  initial?: ServiceSummary;
  onSave: (values: ServiceCreateRequest) => Promise<void>;
  disabled: boolean;
}
const integer = (value: number) =>
  (Number.isInteger(value) && value >= 0 && value <= 2147483647) ||
  '0〜2147483647 の整数を入力してください';
export function ServiceForm({ initial, onSave, disabled }: Props) {
  const form = useForm<ServiceCreateRequest>({
    defaultValues: {
      kind: initial?.kind ?? 'COURSE',
      name: initial?.name ?? '',
      duration_minutes: initial?.duration_minutes ?? Number.NaN,
      charge_type: initial?.charge_type ?? 'PAID',
      price: initial?.price ?? Number.NaN,
      remuneration: initial?.remuneration ?? 0,
    },
  });
  const kind = useWatch({ control: form.control, name: 'kind' });
  const chargeType = useWatch({ control: form.control, name: 'charge_type' });
  const free = kind === 'SPECIAL_SERVICE' && chargeType === 'FREE';
  return (
    <Form {...form}>
      <form
        noValidate
        className="space-y-4"
        onSubmit={form.handleSubmit(async values => {
          await onSave({
            ...values,
            name: values.name.trim(),
            duration_minutes: kind === 'COURSE' ? values.duration_minutes : undefined,
            charge_type: kind === 'SPECIAL_SERVICE' ? values.charge_type : undefined,
          });
        })}
      >
        <FormField
          control={form.control}
          name="kind"
          render={({ field }) => (
            <FormItem>
              <FormLabel>種別</FormLabel>
              <Select
                required
                items={serviceKindLabels}
                value={field.value}
                onValueChange={value => {
                  if (value) {
                    field.onChange(value);
                    form.clearErrors();
                  }
                }}
                disabled={!!initial || disabled}
              >
                <FormControl>
                  <SelectTrigger ref={field.ref}>
                    <SelectValue />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {Object.entries(serviceKindLabels).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
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
          name="name"
          rules={{
            validate: value =>
              (!!value.trim() && value.trim().length <= 255) ||
              '名称を 1〜255 文字で入力してください',
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>名称</FormLabel>
              <FormControl>
                <Input {...field} required disabled={disabled} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        {kind === 'SPECIAL_SERVICE' && (
          <FormField
            control={form.control}
            name="charge_type"
            rules={{ required: '有料・無料を選択してください' }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>料金区分</FormLabel>
                <Select
                  required
                  items={{ PAID: '有料', FREE: '無料' }}
                  value={field.value}
                  onValueChange={(value: ChargeType | null) => {
                    if (value) {
                      field.onChange(value);
                      if (value === 'FREE') {
                        form.setValue('price', 0);
                        form.setValue('remuneration', 0);
                      }
                      form.clearErrors();
                    }
                  }}
                  disabled={disabled}
                >
                  <FormControl>
                    <SelectTrigger ref={field.ref}>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value="PAID">有料</SelectItem>
                    <SelectItem value="FREE">無料</SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
        {kind === 'COURSE' && (
          <FormField
            control={form.control}
            name="duration_minutes"
            rules={{
              validate: value =>
                (value !== undefined && integer(value) === true && value > 0) ||
                '所要時間は正の整数分で入力してください',
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>所要時間（分）</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    value={Number.isNaN(field.value) ? '' : field.value}
                    onChange={event => field.onChange(event.target.valueAsNumber)}
                    type="number"
                    min={1}
                    required
                    disabled={disabled}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
        <FormField
          control={form.control}
          name="price"
          rules={{
            validate: value =>
              integer(value) !== true
                ? integer(value)
                : free
                  ? value === 0 || '無料の価格は零です'
                  : value > 0 || '価格は正の整数円で入力してください',
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>価格（円）</FormLabel>
              <FormControl>
                <Input
                  {...field}
                  value={Number.isNaN(field.value) ? '' : field.value}
                  onChange={event => field.onChange(event.target.valueAsNumber)}
                  type="number"
                  min={free ? 0 : 1}
                  required
                  readOnly={free}
                  disabled={disabled}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="remuneration"
          rules={{
            validate: value =>
              integer(value) !== true
                ? integer(value)
                : value <= form.getValues('price') || '報酬は価格以下で入力してください',
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>固定報酬（円）</FormLabel>
              <FormControl>
                <Input
                  {...field}
                  value={Number.isNaN(field.value) ? '' : field.value}
                  onChange={event => field.onChange(event.target.valueAsNumber)}
                  type="number"
                  min={0}
                  required
                  readOnly={free}
                  disabled={disabled}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" disabled={disabled || form.formState.isSubmitting}>
          保存する
        </Button>
      </form>
    </Form>
  );
}
