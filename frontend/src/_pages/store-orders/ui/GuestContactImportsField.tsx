import { useEffect, useState } from 'react';
import { useFormContext } from 'react-hook-form';
import { customerApi } from '@/entities/customer';
import {
  CustomerSelection,
  GuestContactImportInput,
  OrderApplicationDetail,
} from '@/entities/order';
import { hasPermission, readTokenClaims, useCursorList } from '@/shared/lib';
import {
  Button,
  Checkbox,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Label,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

const labels = { PHONE: '電話', EMAIL: 'メール', LINE: 'LINE' } as const;

export function GuestContactImportsField({ detail }: { detail: OrderApplicationDetail }) {
  const { watch, setValue } = useFormContext();
  const selection: CustomerSelection = watch('customer_selection');
  const customerId = selection.mode === 'EXISTING' ? selection.customer_id : undefined;
  const [allowed, setAllowed] = useState(false);
  useEffect(() => {
    setAllowed(hasPermission(readTokenClaims(), 'CUSTOMER_MANAGE'));
  }, []);
  useEffect(() => {
    setValue('contact_imports', []);
  }, [selection.mode, customerId, setValue]);
  if (!allowed)
    return (
      <p className="text-sm">
        連絡先・販促同意の取り込みには顧客管理権限が必要です。取り込まずに確定できます。
      </p>
    );
  if (selection.mode === 'NONE')
    return (
      <p className="text-sm">
        顧客未設定のため台帳へ取り込みません。申請の証拠と今回の業務許可は保持します。
      </p>
    );
  return <ContactChoices key={customerId ?? 'new'} detail={detail} customerId={customerId} />;
}

function ContactChoices({
  detail,
  customerId,
}: {
  detail: OrderApplicationDetail;
  customerId?: string;
}) {
  const { control } = useFormContext();
  const contacts = useCursorList(cursor =>
    customerId
      ? customerApi.contacts(customerId, { cursor, size: 20 })
      : Promise.resolve({ rows: [], nextCursor: null })
  );
  return (
    <FormField
      control={control}
      name="contact_imports"
      render={({ field }) => {
        const selected: GuestContactImportInput[] = field.value ?? [];
        const change = (type: GuestContactImportInput['type'], value?: GuestContactImportInput) =>
          field.onChange([
            ...selected.filter(item => item.type !== type),
            ...(value ? [value] : []),
          ]);
        return (
          <FormItem className="space-y-4 rounded-lg border p-4">
            <FormLabel>台帳への取り込み（任意）</FormLabel>
            <p className="text-sm">
              申請時の連絡先を種類ごとに選択してください。販促同意は別途選択し、既存の拒否は維持します。
            </p>
            {detail.business_contact_permissions.map(permission => {
              const item = selected.find(item => item.type === permission.type);
              const label = labels[permission.type];
              const options = [
                { value: '__new__', label: '新しい連絡先行を作成' },
                ...contacts.rows
                  .filter(c => c.type === permission.type && c.value === permission.value)
                  .map(c => ({
                    value: c.id,
                    label: `${c.value}（販促：${c.marketing_status}）#${c.id}`,
                  })),
              ];
              return (
                <div key={permission.type} className="space-y-3">
                  <div className="flex items-start gap-2">
                    <Checkbox
                      id={`import-${permission.type}`}
                      checked={!!item}
                      onCheckedChange={checked =>
                        change(
                          permission.type,
                          checked
                            ? { type: permission.type, import_marketing_consent: false }
                            : undefined
                        )
                      }
                    />
                    <Label htmlFor={`import-${permission.type}`}>{label}を台帳へ取り込む</Label>
                  </div>
                  <p className="text-sm break-all">{permission.value}</p>
                  {item && (
                    <>
                      {customerId && (
                        <div className="space-y-2">
                          <Label htmlFor={`target-${permission.type}`}>{label}の取り込み先</Label>
                          <Select
                            items={options}
                            value={item.contact_id ?? '__new__'}
                            onValueChange={value =>
                              change(permission.type, {
                                ...item,
                                contact_id: value === '__new__' ? undefined : (value ?? undefined),
                              })
                            }
                          >
                            <SelectTrigger
                              id={`target-${permission.type}`}
                              className="w-full min-w-0"
                            >
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {options.map(option => (
                                <SelectItem key={option.value} value={option.value}>
                                  {option.label}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                      )}
                      {detail.contact_consent?.marketing_allowed ? (
                        <div className="flex items-start gap-2">
                          <Checkbox
                            id={`marketing-${permission.type}`}
                            checked={item.import_marketing_consent}
                            onCheckedChange={checked =>
                              change(permission.type, {
                                ...item,
                                import_marketing_consent: checked === true,
                              })
                            }
                          />
                          <Label htmlFor={`marketing-${permission.type}`}>
                            {label}の販促同意を取り込む
                          </Label>
                        </div>
                      ) : (
                        <p className="text-sm">販促同意は未選択のため、既存状態を変更しません。</p>
                      )}
                    </>
                  )}
                </div>
              );
            })}
            {contacts.failed && (
              <RegionError message="既存の連絡先を取得できませんでした" onRetry={contacts.reload} />
            )}
            {contacts.isLoading && <p>既存の連絡先を読み込み中...</p>}
            {contacts.hasMore && (
              <Button
                type="button"
                variant="outline"
                disabled={contacts.isLoading}
                onClick={contacts.loadMore}
              >
                連絡先の続きを取得
              </Button>
            )}
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}
