import { useFieldArray, useFormContext, useWatch } from 'react-hook-form';
import { BusinessContactPermissionInput, ContactSnapshot } from '@/entities/order';
import {
  Button,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Textarea,
} from '@/shared/ui';

export const CONTACT_CHANNELS = [
  { type: 'PHONE', key: 'phone_number', label: '電話' },
  { type: 'EMAIL', key: 'email', label: 'メール' },
  { type: 'LINE', key: 'line_id', label: 'LINE' },
] as const;
export const CONTACT_STATUS_LABELS = { UNKNOWN: '未確認', ALLOWED: '許可', DENIED: '拒否' };
const OPTIONS = Object.entries(CONTACT_STATUS_LABELS).map(([value, label]) => ({ value, label }));
interface PermissionForm {
  contact_snapshot: ContactSnapshot;
  business_contact_permissions: BusinessContactPermissionInput[];
}
export function OrderBusinessContactFields() {
  const { control } = useFormContext<PermissionForm>();
  const { fields, append, remove } = useFieldArray({
    control,
    name: 'business_contact_permissions',
  });
  const contact = useWatch({ control, name: 'contact_snapshot' });
  return (
    <fieldset className="space-y-6">
      <legend className="mb-4 font-medium">今回だけの業務連絡</legend>
      <p className="text-sm text-muted-foreground">
        連絡先の入力だけでは許可しません。販促には使わず、顧客台帳も変更しません。同店に有効な拒否がある場合は連絡できません。
      </p>
      {CONTACT_CHANNELS.map(channel => {
        const index = fields.findIndex(row => row.type === channel.type);
        if (!contact?.[channel.key]?.trim()) return null;
        if (index < 0)
          return (
            <Button
              key={channel.type}
              type="button"
              variant="outline"
              onClick={() =>
                append({ type: channel.type, status: 'UNKNOWN', source: '', reason: '' })
              }
            >
              {channel.label}の連絡可否を記録
            </Button>
          );
        return (
          <div key={fields[index].id} className="space-y-6 rounded-lg border p-4">
            <FormField
              control={control}
              name={`business_contact_permissions.${index}.status`}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{channel.label}の今回の連絡可否</FormLabel>
                  <Select items={OPTIONS} value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger ref={field.ref} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {OPTIONS.map(option => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name={`business_contact_permissions.${index}.source`}
              rules={{
                validate: value => !!value?.trim() || '出所を入力してください',
                maxLength: { value: 200, message: '出所は200文字以内です' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{channel.label}の出所</FormLabel>
                  <FormControl>
                    <Input {...field} required maxLength={200} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name={`business_contact_permissions.${index}.reason`}
              rules={{
                validate: value => !!value?.trim() || '根拠を入力してください',
                maxLength: { value: 2000, message: '根拠は2000文字以内です' },
              }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{channel.label}の根拠</FormLabel>
                  <FormControl>
                    <Textarea {...field} required maxLength={2000} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="button" variant="outline" onClick={() => remove(index)}>
              {channel.label}の変更を取り消す
            </Button>
          </div>
        );
      })}
      <p className="text-sm text-muted-foreground">
        宛先を変えると旧宛先の許可は失効します。新しい宛先への許可は、宛先入力後に根拠付きで記録してください。
      </p>
    </fieldset>
  );
}
