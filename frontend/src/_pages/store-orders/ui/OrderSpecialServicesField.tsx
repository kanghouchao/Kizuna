'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { useFormContext, useWatch } from 'react-hook-form';
import { isAxiosError } from 'axios';
import { OrderSpecialService, orderApi } from '@/entities/order';
import { useKeyedResource } from '@/shared/lib';
import { Button, Checkbox, Input, Label, RegionError } from '@/shared/ui';

export function OrderSpecialServicesField({
  current = [],
  originalCast,
  historicalOrderId,
}: {
  current?: OrderSpecialService[];
  originalCast?: string;
  historicalOrderId?: string;
}) {
  const { control, setValue } = useFormContext<{
    cast_id: string;
    special_service_ids: string[];
    special_service_revision_ids: string[];
  }>();
  const cast = useWatch({ control, name: 'cast_id' });
  const field = historicalOrderId ? 'special_service_revision_ids' : 'special_service_ids';
  const selected = useWatch({ control, name: field }) ?? [];
  const lastCast = useRef(cast);
  useEffect(() => {
    if (lastCast.current !== cast && !historicalOrderId) {
      setValue('special_service_ids', [], { shouldDirty: true });
      lastCast.current = cast;
    }
  }, [cast, historicalOrderId, setValue]);
  const store = useParams()?.storeId as string;
  const changedCast = originalCast !== undefined && originalCast !== cast;
  return (
    <SpecialServiceOptions
      key={`${store}:${cast}:${historicalOrderId}`}
      current={current}
      cast={cast}
      changedCast={changedCast}
      historicalOrderId={historicalOrderId}
      selected={selected}
      onChange={ids => setValue(field, ids, { shouldDirty: true })}
    />
  );
}

function SpecialServiceOptions({
  current,
  cast,
  changedCast,
  historicalOrderId,
  selected,
  onChange,
}: {
  current: OrderSpecialService[];
  cast?: string;
  changedCast: boolean;
  historicalOrderId?: string;
  selected: string[];
  onChange: (ids: string[]) => void;
}) {
  const store = useParams()?.storeId as string;
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [cursors, setCursors] = useState<(string | undefined)[]>([undefined]);
  const result = useKeyedResource(
    ['special-services', store, cast, historicalOrderId, search, page],
    (!cast && !historicalOrderId) || changedCast
      ? null
      : async () => {
          try {
            if (historicalOrderId) {
              const data = await orderApi.specialServiceRevisions(
                historicalOrderId,
                search,
                cursors[page]
              );
              return {
                rows: data.rows,
                more: !!data.nextCursor,
                cursor: data.nextCursor ?? undefined,
                forbidden: false,
              };
            }
            const data = await orderApi.specialServiceCandidates(cast!, search, page);
            return {
              rows: data.rows,
              more: (page + 1) * 20 < data.total,
              cursor: undefined,
              forbidden: false,
            };
          } catch (error) {
            if (isAxiosError(error) && error.response?.status === 403)
              return { rows: [], more: false, cursor: undefined, forbidden: true };
            throw error;
          }
        }
  );
  const [chosen, setChosen] = useState<typeof current>([]);
  const rows = [...current, ...chosen, ...(result.data?.rows ?? [])].filter(
    (row, i, all) =>
      all.findIndex(other =>
        historicalOrderId
          ? other.revision_id === row.revision_id
          : other.service_id === row.service_id
      ) === i
  );
  return (
    <section className="space-y-3 rounded-lg border p-4" aria-label="特殊サービス">
      <h2 className="font-medium">特殊サービス</h2>
      {changedCast ? (
        <p role="status">
          担当変更で特殊サービスをすべて除去します。保存後、新担当の受諾候補から選び直してください。
        </p>
      ) : !cast && !historicalOrderId ? (
        <p>担当を選択すると受諾済みの候補を表示します。</p>
      ) : (
        <>
          <Label htmlFor="special-service-search">特殊サービスを検索</Label>
          <Input
            id="special-service-search"
            value={search}
            onChange={e => {
              setSearch(e.target.value);
              setPage(0);
              setCursors([undefined]);
            }}
          />
          {result.data?.forbidden ? (
            <p role="alert">特殊サービスを照会する権限がありません。担当者に依頼してください。</p>
          ) : result.failure ? (
            <RegionError
              message="特殊サービスを取得できませんでした"
              onRetry={() => void result.reload()}
            />
          ) : result.isLoading ? (
            <p>特殊サービスを読み込み中...</p>
          ) : (
            result.data?.rows.length === 0 && <p>新しく選択できる特殊サービスがありません。</p>
          )}
          <ul className="space-y-3">
            {rows.map(row => {
              const value = historicalOrderId ? row.revision_id : row.service_id;
              const saved = current.find(item => item.service_id === row.service_id);
              const available = !!result.data?.rows.some(
                item => (historicalOrderId ? item.revision_id : item.service_id) === value
              );
              return (
                <li key={value} className="space-y-1">
                  <Label className="flex items-start gap-2">
                    <Checkbox
                      checked={selected.includes(value)}
                      disabled={!selected.includes(value) && !available}
                      onCheckedChange={checked => {
                        onChange(
                          checked
                            ? [
                                ...selected.filter(
                                  id =>
                                    !historicalOrderId ||
                                    !rows.some(
                                      item =>
                                        item.service_id === row.service_id &&
                                        item.revision_id === id
                                    )
                                ),
                                value,
                              ]
                            : selected.filter(id => id !== value)
                        );
                        if (checked)
                          setChosen(previous => [...previous, row as OrderSpecialService]);
                      }}
                    />
                    <span>
                      {row.name} / 版{row.revision_number} / 料金 ¥{row.price.toLocaleString()} /
                      固定報酬 ¥{row.remuneration.toLocaleString()}
                    </span>
                  </Label>
                  {saved?.requires_attention && (
                    <p className="text-destructive-strong" role="alert">
                      本人拒否・要対応：除去または改選して保存してください。
                    </p>
                  )}
                  {saved?.current_consent_status === 'RECONFIRMATION_REQUIRED' && (
                    <p>再受諾待ち：採用済みの旧約定は保持されます。</p>
                  )}
                </li>
              );
            })}
          </ul>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              前の候補
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={!result.data?.more}
              onClick={() => {
                setCursors([...cursors.slice(0, page + 1), result.data?.cursor]);
                setPage(page + 1);
              }}
            >
              次の候補
            </Button>
          </div>
        </>
      )}
    </section>
  );
}
