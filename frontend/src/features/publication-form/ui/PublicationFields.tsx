import { useFormContext } from 'react-hook-form';
import {
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Input,
  Switch,
} from '@/shared/ui';
import { PublicationFormValues, optionalStoreDate } from '../lib/publication';

// 呼び出し側の Form（FormProvider）に公開設定の 4 項目を足す。外側のフォームが他の項目を
// 持っていても使えるよう、control は props ではなく文脈から読む。
export function PublicationFields({ busy }: { busy: boolean }) {
  const { control } = useFormContext<PublicationFormValues>();
  return (
    <>
      <FormField
        control={control}
        name="published"
        render={({ field }) => (
          <FormItem className="flex items-center justify-between gap-3">
            <FormLabel>公式サイトで公開する</FormLabel>
            <FormControl>
              <Switch
                ref={field.ref}
                checked={field.value}
                onCheckedChange={field.onChange}
                disabled={busy}
              />
            </FormControl>
          </FormItem>
        )}
      />
      <FormField
        control={control}
        name="publish_from"
        rules={{ validate: optionalStoreDate }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>公開開始（任意・店舗の現地時刻）</FormLabel>
            <FormControl>
              <Input {...field} type="datetime-local" disabled={busy} />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
      <FormField
        control={control}
        name="publish_until"
        rules={{
          validate: (value, values) => {
            const valid = optionalStoreDate(value);
            if (valid !== true) return valid;
            return (
              value === '' ||
              values.publish_from === '' ||
              values.publish_from < value ||
              '公開終了は公開開始より後にしてください'
            );
          },
        }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>公開終了（任意・店舗の現地時刻。この時刻に掲出を終えます）</FormLabel>
            <FormControl>
              <Input {...field} type="datetime-local" disabled={busy} />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
      <FormField
        control={control}
        name="display_order"
        rules={{
          validate: value =>
            value === null ||
            (Number.isInteger(value) && Math.abs(value) <= 2147483647) ||
            '表示順は整数で入力してください（空欄は末尾）',
        }}
        render={({ field }) => (
          <FormItem>
            <FormLabel>表示順（任意・小さいほど先頭。空欄は末尾に新しい順）</FormLabel>
            <FormControl>
              <Input
                {...field}
                type="number"
                step={1}
                disabled={busy}
                value={field.value === null || Number.isNaN(field.value) ? '' : field.value}
                onChange={event =>
                  field.onChange(event.target.value === '' ? null : event.target.valueAsNumber)
                }
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    </>
  );
}
