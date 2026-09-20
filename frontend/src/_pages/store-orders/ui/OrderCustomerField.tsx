import { useEffect, useState } from 'react';
import { useFormContext } from 'react-hook-form';
import { CustomerSelection, OrderCustomerCandidate, orderApi } from '@/entities/order';
import { hasPermission, readTokenClaims, useCursorList } from '@/shared/lib';
import {
  Button,
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
  useFormField,
  Combobox,
  ComboboxTrigger,
  ComboboxContent,
  ComboboxInput,
  ComboboxEmpty,
  ComboboxList,
  ComboboxItem,
} from '@/shared/ui';

const MODES = [
  { value: 'NONE', label: '顧客未設定' },
  { value: 'EXISTING', label: '既存顧客' },
  { value: 'NEW', label: '新規顧客' },
];
export function OrderCustomerField({
  locked = false,
  customerName,
}: {
  locked?: boolean;
  customerName?: string | null;
}) {
  const { control } = useFormContext();
  const [canCreate, setCanCreate] = useState(false);
  useEffect(() => {
    setCanCreate(hasPermission(readTokenClaims(), 'CUSTOMER_MANAGE'));
  }, []);
  if (locked)
    return (
      <p className="rounded-lg border p-4 text-sm">
        会員申請の顧客：{customerName || '未設定'}（会員の関連から決定）
      </p>
    );
  const options = MODES.filter(m => m.value !== 'NEW' || canCreate);
  return (
    <FormField
      control={control}
      name="customer_selection"
      rules={{
        validate: (value: CustomerSelection) =>
          value.mode === 'EXISTING'
            ? !!value.customer_id || '顧客を候補から選択してください'
            : value.mode === 'NEW'
              ? !!value.new_customer.name.trim() || '新規顧客名を入力してください'
              : true,
      }}
      render={({ field }) => {
        const selection: CustomerSelection = field.value ?? { mode: 'NONE' };
        return (
          <FormItem>
            <FormLabel>顧客の選択</FormLabel>
            <Select
              items={options}
              value={selection.mode}
              onValueChange={mode =>
                field.onChange(
                  mode === 'NEW'
                    ? { mode, new_customer: { name: '' } }
                    : mode === 'EXISTING'
                      ? { mode, customer_id: '' }
                      : { mode: 'NONE' }
                )
              }
            >
              <FormControl>
                <SelectTrigger ref={field.ref} className="w-full">
                  <SelectValue />
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                {options.map(m => (
                  <SelectItem key={m.value} value={m.value}>
                    {m.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {selection.mode === 'EXISTING' && (
              <CustomerCandidates
                selected={selection.customer_id}
                name={customerName}
                triggerRef={field.ref}
                onSelect={id => field.onChange({ mode: 'EXISTING', customer_id: id })}
              />
            )}
            {selection.mode === 'NEW' && (
              <NewCustomerName
                value={selection.new_customer.name}
                inputRef={field.ref}
                onChange={name => field.onChange({ mode: 'NEW', new_customer: { name } })}
              />
            )}
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}
function NewCustomerName({
  value,
  onChange,
  inputRef,
}: {
  value: string;
  onChange: (value: string) => void;
  inputRef: React.Ref<HTMLInputElement>;
}) {
  const { error, formItemId, formMessageId } = useFormField();
  const id = `${formItemId}-name`;
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>新規顧客名</Label>
      <Input
        id={id}
        ref={inputRef}
        value={value}
        maxLength={255}
        onChange={e => onChange(e.target.value)}
        aria-invalid={!!error}
        aria-describedby={error ? formMessageId : undefined}
      />
      <p className="text-sm text-muted-foreground">
        名前だけを台帳へ登録します。連絡先は下の欄で別に記録してください。
      </p>
    </div>
  );
}
function CustomerCandidates({
  selected,
  name,
  onSelect,
  triggerRef,
}: {
  selected: string;
  name?: string | null;
  onSelect: (id: string) => void;
  triggerRef: React.Ref<HTMLButtonElement>;
}) {
  const { error, formItemId, formMessageId } = useFormField();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [chosen, setChosen] = useState<OrderCustomerCandidate | null>(null);
  const list = useCursorList(
    (cursor, search: string) => orderApi.customerCandidates(search, cursor),
    ''
  );
  const label = selected
    ? chosen?.id === selected
      ? chosen.name || selected
      : name || selected
    : '氏名・電話で検索';
  return (
    <div className="grid gap-2">
      <Label htmlFor={`${formItemId}-candidate`}>既存顧客の検索</Label>
      <Combobox
        items={list.rows}
        filter={null}
        value={null}
        open={open}
        onOpenChange={setOpen}
        inputValue={query}
        onInputValueChange={value => {
          setQuery(value);
          list.search(value);
        }}
        itemToStringLabel={(candidate: OrderCustomerCandidate) => candidate.name || candidate.id}
        onValueChange={candidate => {
          if (candidate) {
            setChosen(candidate);
            onSelect(candidate.id);
            setOpen(false);
          }
        }}
      >
        <ComboboxTrigger
          render={
            <Button
              id={`${formItemId}-candidate`}
              type="button"
              variant="outline"
              ref={triggerRef}
              className="min-w-0"
              aria-invalid={!!error}
              aria-describedby={error ? formMessageId : undefined}
            />
          }
        >
          <span className="truncate">{label}</span>
        </ComboboxTrigger>
        <ComboboxContent className="min-w-72">
          <ComboboxInput aria-label="顧客検索（氏名・電話）" placeholder="氏名・電話で検索" />
          {list.isLoading && (
            <p role="status" className="p-3 text-sm">
              顧客を読み込み中...
            </p>
          )}
          {list.failed && (
            <RegionError message="顧客を取得できませんでした。" onRetry={list.reload} />
          )}
          {!list.isLoading && !list.failed && <ComboboxEmpty>一致する顧客がいません</ComboboxEmpty>}
          <ComboboxList>
            {(candidate: OrderCustomerCandidate) => (
              <ComboboxItem
                key={candidate.id}
                value={candidate}
                className="break-all whitespace-normal"
              >
                {candidate.name || '氏名なし'} {candidate.phone_number}（ID: {candidate.id}）
              </ComboboxItem>
            )}
          </ComboboxList>
          {list.hasMore && (
            <Button
              type="button"
              variant="outline"
              disabled={list.isLoading}
              onClick={list.loadMore}
            >
              さらに読み込む
            </Button>
          )}
        </ComboboxContent>
      </Combobox>
    </div>
  );
}
