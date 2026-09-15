'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { isAxiosError } from 'axios';
import { orderApi, SurchargeCandidate } from '@/entities/order';
import { useKeyedResource } from '@/shared/lib';
import {
  Button,
  RegionError,
  Combobox,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxTrigger,
} from '@/shared/ui';

export function OrderSurchargePicker({
  historicalOrderId,
  onSelect,
}: {
  historicalOrderId?: string;
  onSelect: (candidate: SurchargeCandidate) => void;
}) {
  const [open, setOpen] = useState(true);
  const storeId = useParams()?.storeId as string;
  const [query, setQuery] = useState({
    search: '',
    page: 0,
    cursors: [undefined] as (string | undefined)[],
  });
  const { search, page, cursors } = query;
  const resource = useKeyedResource(
    ['surcharge-options', storeId, historicalOrderId, search, page, cursors[page]],
    async () => {
      try {
        if (historicalOrderId) {
          const result = await orderApi.surchargeRevisions(
            historicalOrderId,
            search,
            cursors[page]
          );
          return {
            rows: result.content,
            next: result.next_cursor,
            more: !!result.next_cursor,
            forbidden: false,
          };
        }
        const result = await orderApi.surchargeCandidates(search, page);
        return {
          rows: result.rows,
          next: undefined,
          more: (page + 1) * 20 < result.total,
          forbidden: false,
        };
      } catch (error) {
        if (isAxiosError(error) && error.response?.status === 403)
          return {
            rows: [] as SurchargeCandidate[],
            next: undefined,
            more: false,
            forbidden: true,
          };
        throw error;
      }
    }
  );
  return (
    <section aria-label="加算の選択" className="space-y-3 rounded-lg border p-3">
      <Combobox
        items={resource.data?.rows ?? []}
        filter={null}
        value={null}
        open={open}
        onOpenChange={setOpen}
        inputValue={search}
        onInputValueChange={value => setQuery({ search: value, page: 0, cursors: [undefined] })}
        itemToStringLabel={(row: SurchargeCandidate) => row.name}
        onValueChange={row => {
          if (row) onSelect(row);
        }}
      >
        <ComboboxTrigger render={<Button type="button" variant="outline" />}>
          加算を検索して選択
        </ComboboxTrigger>
        <ComboboxContent>
          <ComboboxInput aria-label="加算を検索" />
          {resource.isLoading ? (
            <p>加算を読み込み中...</p>
          ) : resource.failure ? (
            <RegionError
              message="加算を取得できませんでした"
              onRetry={() => {
                setQuery({ search, page: 0, cursors: [undefined] });
                void resource.reload();
              }}
            />
          ) : resource.data?.forbidden ? (
            <p role="alert">加算を照会する権限がありません。担当者に依頼してください。</p>
          ) : (
            <>
              {resource.data?.rows.length === 0 && <p>選択できる加算がありません。</p>}
              <ComboboxList>
                {(row: SurchargeCandidate) => (
                  <ComboboxItem key={row.revision_id} value={row}>
                    {row.name} / 料金 ¥{row.price.toLocaleString()} / 固定報酬 ¥
                    {row.remuneration.toLocaleString()} / 版{row.revision_number}
                    {row.service_deleted ? '（削除済み）' : ''}
                  </ComboboxItem>
                )}
              </ComboboxList>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={page === 0}
                  onClick={() => setQuery({ ...query, page: page - 1 })}
                >
                  前へ
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={!resource.data?.more}
                  onClick={() =>
                    setQuery({
                      search,
                      page: page + 1,
                      cursors: [...cursors.slice(0, page + 1), resource.data?.next],
                    })
                  }
                >
                  次へ
                </Button>
              </div>
            </>
          )}
        </ComboboxContent>
      </Combobox>
    </section>
  );
}
