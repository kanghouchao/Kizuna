import { useState } from 'react';
import type { Ref } from 'react';
import type { PageResult } from '@/shared/api';
import { useResource } from '@/shared/lib';
import {
  Button,
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxTrigger,
  RegionError,
} from '@/shared/ui';

interface Props<T> {
  value: T | null;
  fetcher: (search: string, page: number) => Promise<PageResult<T>>;
  itemId: (item: T) => number;
  itemLabel: (item: T) => string;
  placeholder: string;
  errorMessage: string;
  disabled?: boolean;
  onChange: (value: T) => void;
  triggerRef: Ref<HTMLButtonElement>;
  id?: string;
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
}

export function CandidatePicker<T>({
  value,
  onChange,
  triggerRef,
  fetcher,
  itemId,
  itemLabel,
  placeholder,
  errorMessage,
  disabled,
  ...props
}: Props<T>) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const people = useResource(open ? () => fetcher(search, page) : null, [open, search, page]);
  const rows = people.isLoading || people.failure !== null ? [] : (people.data?.rows ?? []);
  return (
    <Combobox
      items={rows}
      filter={null}
      value={null}
      itemToStringLabel={itemLabel}
      open={open}
      onOpenChange={next => {
        setOpen(next);
        if (!next) {
          setSearch('');
          setPage(0);
        }
      }}
      inputValue={search}
      onInputValueChange={next => {
        setSearch(next);
        setPage(0);
      }}
      onValueChange={person => {
        if (person) {
          onChange(person);
          setOpen(false);
          setSearch('');
          setPage(0);
        }
      }}
    >
      <ComboboxTrigger
        render={
          <Button
            {...props}
            disabled={disabled}
            ref={triggerRef}
            aria-required="true"
            type="button"
            variant="outline"
            className="w-full min-w-0"
          />
        }
      >
        <span className="truncate">{value ? itemLabel(value) : placeholder}</span>
      </ComboboxTrigger>
      <ComboboxContent className="w-80 max-w-[90vw]">
        <ComboboxInput placeholder={placeholder} />
        {people.isLoading ? (
          <p className="p-4 text-sm">検索中...</p>
        ) : people.failure !== null ? (
          <RegionError message={errorMessage} onRetry={people.reload} className="p-4" />
        ) : (
          <>
            <ComboboxEmpty>該当する候補がありません</ComboboxEmpty>
            <ComboboxList>
              {(person: T) => (
                <ComboboxItem key={itemId(person)} value={person}>
                  <span className="min-w-0 truncate">{itemLabel(person)}</span>
                  <span className="ml-auto shrink-0">#{itemId(person)}</span>
                </ComboboxItem>
              )}
            </ComboboxList>
            {(page > 0 || (people.data?.pageCount ?? 0) > 1) && (
              <div className="flex justify-between gap-3 border-t p-3">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  候補の前のページ
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= (people.data?.pageCount ?? 0)}
                  onClick={() => setPage(page + 1)}
                >
                  候補の次のページ
                </Button>
              </div>
            )}
          </>
        )}
      </ComboboxContent>
    </Combobox>
  );
}
