import { useState } from 'react';
import type { Ref } from 'react';
import { MonthlyRemunerationCast, monthlyRemunerationApi } from '@/entities/order';
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

interface Props {
  value: MonthlyRemunerationCast | null;
  onChange: (value: MonthlyRemunerationCast) => void;
  triggerRef: Ref<HTMLButtonElement>;
  id?: string;
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
}

export function PersonPicker({ value, onChange, triggerRef, ...props }: Props) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const people = useResource(open ? () => monthlyRemunerationApi.casts(search, page) : null, [
    open,
    search,
    page,
  ]);
  const rows = people.isLoading || people.failure !== null ? [] : (people.data?.rows ?? []);
  return (
    <Combobox
      items={rows}
      filter={null}
      value={null}
      itemToStringLabel={(person: MonthlyRemunerationCast) => person.name}
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
            ref={triggerRef}
            aria-required="true"
            type="button"
            variant="outline"
            className="w-full min-w-0"
          />
        }
      >
        <span className="truncate">{value?.name ?? '源氏名で検索'}</span>
      </ComboboxTrigger>
      <ComboboxContent className="w-80 max-w-[90vw]">
        <ComboboxInput placeholder="源氏名で検索" />
        {people.isLoading ? (
          <p className="p-4 text-sm">検索中...</p>
        ) : people.failure !== null ? (
          <RegionError
            message="キャスト本人を取得できませんでした。"
            onRetry={people.reload}
            className="p-4"
          />
        ) : (
          <>
            <ComboboxEmpty>該当するキャスト本人がいません</ComboboxEmpty>
            <ComboboxList>
              {(person: MonthlyRemunerationCast) => (
                <ComboboxItem key={person.person_id} value={person}>
                  <span className="min-w-0 truncate">{person.name}</span>
                  <span className="ml-auto shrink-0">#{person.person_id}</span>
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
