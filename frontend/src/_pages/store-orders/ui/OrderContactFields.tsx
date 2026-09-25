import { CONTACT_CHANNELS } from './OrderBusinessContactFields';
import { BusinessContactPermissionInput } from '@/entities/order';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE } from '@/shared/lib';
import { useFormContext } from 'react-hook-form';
import { FormControl, FormField, FormItem, FormLabel, FormMessage, Input } from '@/shared/ui';

export function OrderContactFields() {
  const { control, getValues, setValue } = useFormContext();
  return (
    <fieldset className="grid grid-cols-1 gap-6 md:grid-cols-2">
      <legend className="mb-4 font-medium">受付時の連絡先（任意）</legend>
      <p className="text-sm text-muted-foreground md:col-span-2">
        今回の連絡先を受注に保存します。顧客台帳は変更しません。
      </p>
      {(
        [
          ['name', 'お客様名', 'text', 255],
          ['phone_number', '電話番号', 'tel', 50],
          ['email', 'メール', 'text', 254],
          ['line_id', 'LINE ID', 'text', 255],
        ] as const
      ).map(([key, label, type, maxLength]) => (
        <FormField
          key={key}
          control={control}
          name={`contact_snapshot.${key}`}
          rules={{
            maxLength: { value: maxLength, message: `${label}は${maxLength}文字以内です` },
            validate: value =>
              key !== 'email' ||
              !value?.trim() ||
              EMAIL_PATTERN.test(value.trim()) ||
              EMAIL_PATTERN_MESSAGE,
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{label}</FormLabel>
              <FormControl>
                <Input
                  {...field}
                  value={field.value ?? ''}
                  onChange={event => {
                    field.onChange(event);
                    const channel = CONTACT_CHANNELS.find(item => item.key === key);
                    if (channel) {
                      const permissions: BusinessContactPermissionInput[] =
                        getValues('business_contact_permissions') ?? [];
                      setValue(
                        'business_contact_permissions',
                        permissions.filter(item => item.type !== channel.type),
                        { shouldDirty: true }
                      );
                    }
                  }}
                  type={type}
                  inputMode={key === 'email' ? 'email' : undefined}
                  maxLength={maxLength}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
      ))}
    </fieldset>
  );
}
