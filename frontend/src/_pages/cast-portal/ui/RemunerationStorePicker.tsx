import { useState, type Ref } from 'react';
import { selfMonthlyRemunerationApi, SelfMonthlyRemunerationStore } from '@/entities/order';
import { useResource } from '@/shared/lib';
import {
  Button,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

interface Props {
  value: SelfMonthlyRemunerationStore | null;
  onChange: (value: SelfMonthlyRemunerationStore) => void;
  triggerRef: Ref<HTMLButtonElement>;
  id?: string;
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
}

export function RemunerationStorePicker({ value, onChange, triggerRef, ...props }: Props) {
  const [page, setPage] = useState(0);
  const stores = useResource(() => selfMonthlyRemunerationApi.stores(page), [page]);
  if (stores.isLoading) return <p className="text-sm">店舗を読み込み中...</p>;
  if (stores.failure !== null)
    return <RegionError message="店舗を取得できませんでした。" onRetry={stores.reload} />;
  const rows = stores.data?.rows ?? [];
  const items =
    value && !rows.some(row => row.store_id === value.store_id) ? [value, ...rows] : rows;
  return (
    <div className="space-y-2">
      <Select
        value={value?.store_id ?? null}
        items={items.map(row => ({ value: row.store_id, label: row.store_name }))}
        onValueChange={id => {
          const selected = items.find(row => row.store_id === id);
          if (selected) onChange(selected);
        }}
      >
        <SelectTrigger {...props} ref={triggerRef} aria-required="true" className="w-full min-w-0">
          <SelectValue placeholder="店舗を選択" />
        </SelectTrigger>
        <SelectContent className="max-w-[90vw]">
          {items.map(row => (
            <SelectItem key={row.store_id} value={row.store_id}>
              <span className="break-words whitespace-normal">{row.store_name}</span>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {rows.length === 0 && <p className="text-sm">在籍した店舗はありません</p>}
      {(page > 0 || (stores.data?.pageCount ?? 0) > 1) && (
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            店舗候補の前のページ
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page + 1 >= (stores.data?.pageCount ?? 0)}
            onClick={() => setPage(page + 1)}
          >
            店舗候補の次のページ
          </Button>
        </div>
      )}
    </div>
  );
}
