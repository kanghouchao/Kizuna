'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useFormContext } from 'react-hook-form';
import { CourseCandidate, OrderCourse, orderApi } from '@/entities/order';
import { useKeyedResource } from '@/shared/lib';
import {
  Button,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  RegionError,
  Combobox,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxTrigger,
} from '@/shared/ui';
import { isAxiosError } from 'axios';

export function OrderCourseField({
  current,
  historicalOrderId,
  required = false,
}: {
  current?: OrderCourse;
  historicalOrderId?: string;
  required?: boolean;
}) {
  const { control } = useFormContext<{ course_id: string; course_revision_id: string }>();
  const storeId = useParams()?.storeId as string;
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [cursors, setCursors] = useState<(string | undefined)[]>([undefined]);
  const cursor = cursors[page];
  const [selected, setSelected] = useState<{ scope: string; value: string; label: string }>();
  const scope = `${storeId}:${historicalOrderId ?? ''}`;
  const result = useKeyedResource(
    ['course-options', storeId, historicalOrderId, search, page, cursor],
    async () => {
      try {
        if (historicalOrderId) {
          const data = await orderApi.courseRevisions(historicalOrderId, search, cursor);
          return {
            rows: data.content,
            next: data.next_cursor,
            more: !!data.next_cursor,
            forbidden: false,
          };
        }
        const data = await orderApi.courseCandidates(search, page);
        return {
          rows: data.rows,
          next: undefined,
          more: (page + 1) * 20 < data.total,
          forbidden: false,
        };
      } catch (error) {
        if (isAxiosError(error) && error.response?.status === 403)
          return { rows: [] as CourseCandidate[], next: undefined, more: false, forbidden: true };
        throw error;
      }
    }
  );
  const rows = result.data?.rows ?? [];
  const items = [
    { value: '', label: required ? 'コースを選択' : '採用済み条件を保持' },
    ...rows.map(row => ({
      value: historicalOrderId ? row.revision_id : row.service_id,
      label: `${row.name} / ${row.duration_minutes}分 / 料金 ¥${row.price.toLocaleString()} / 固定報酬 ¥${row.remuneration.toLocaleString()} / 版${row.revision_number}${row.service_deleted ? '（削除済み）' : ''}`,
    })),
  ];
  if (selected?.scope === scope && !items.some(item => item.value === selected.value))
    items.push(selected);
  return (
    <section className="space-y-3 rounded-lg border p-4">
      {current && (
        <p>
          採用済み: {current.name} / {current.duration_minutes}分 / 料金 ¥
          {current.price.toLocaleString()} / 固定報酬 ¥{current.remuneration.toLocaleString()} / 版
          {current.revision_number}
        </p>
      )}
      <FormField
        control={control}
        name={historicalOrderId ? 'course_revision_id' : 'course_id'}
        rules={required ? { required: 'コースを選択してください' } : undefined}
        render={({ field }) => (
          <FormItem>
            <FormLabel>{historicalOrderId ? '訂正する過去の版' : 'コース'}</FormLabel>
            <Combobox
              items={items}
              filter={null}
              value={null}
              open={open}
              onOpenChange={setOpen}
              inputValue={search}
              onInputValueChange={value => {
                setSearch(value);
                setPage(0);
                setCursors([undefined]);
              }}
              itemToStringLabel={(item: { value: string; label: string }) => item.label}
              onValueChange={item => {
                if (!item) return;
                field.onChange(item.value);
                setSelected({ ...item, scope });
                setOpen(false);
              }}
            >
              <FormControl>
                <ComboboxTrigger
                  render={
                    <Button
                      type="button"
                      variant="outline"
                      className="h-auto min-h-9 whitespace-normal text-left"
                    />
                  }
                >
                  <span>
                    {items.find(item => item.value === field.value)?.label ?? items[0].label}
                  </span>
                </ComboboxTrigger>
              </FormControl>
              <ComboboxContent>
                <ComboboxInput aria-label="コースを検索" />
                {result.data?.forbidden ? (
                  <p role="alert">コースを照会する権限がありません。担当者に依頼してください。</p>
                ) : result.failure ? (
                  <RegionError
                    message="コースを取得できませんでした"
                    onRetry={() => void result.reload()}
                  />
                ) : result.isLoading ? (
                  <p>コースを読み込み中...</p>
                ) : (
                  <>
                    <ComboboxList>
                      {(item: { value: string; label: string }) => (
                        <ComboboxItem key={item.value} value={item}>
                          {item.label}
                        </ComboboxItem>
                      )}
                    </ComboboxList>
                    {rows.length === 0 && <p>選択できるコースがありません。</p>}
                    <div className="flex gap-2 border-t p-2">
                      <Button
                        type="button"
                        variant="outline"
                        disabled={page === 0}
                        onClick={() => setPage(Math.max(0, page - 1))}
                      >
                        前へ
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        disabled={!result.data?.more}
                        onClick={() => {
                          setCursors([...cursors.slice(0, page + 1), result.data?.next]);
                          setPage(page + 1);
                        }}
                      >
                        次へ
                      </Button>
                    </div>
                  </>
                )}
              </ComboboxContent>
            </Combobox>
            <FormMessage />
          </FormItem>
        )}
      />
    </section>
  );
}
