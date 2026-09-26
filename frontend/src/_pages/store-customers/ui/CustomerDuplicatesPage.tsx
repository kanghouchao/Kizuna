'use client';

import { contactLabels } from '../lib/contactLabels';

import Link from 'next/link';
import { ChevronLeftIcon } from 'lucide-react';
import { ReactNode, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { ContactType, CustomerMergeComparisonResponse, customerApi } from '@/entities/customer';
import { storePath, useCursorList, hasPermission, readTokenClaims } from '@/shared/lib';
import { CustomerMergeComparison } from './CustomerMergeComparison';
import { CustomerMergeConfirmDialog } from './CustomerMergeConfirmDialog';
import {
  Input,
  Label,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  Badge,
  Button,
  Checkbox,
  RegionError,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 見比べる対象は 2 行。3 行以上を一度に畳む導線は持たない（ADR 0010 は一括統合を採らない）。 */
const PAIR_SIZE = 2;

/**
 * 選択中の 2 行。グループを跨いだ選択は持たない — 候補が意味を持つのは同じ種類・値のグループの中だけで、
 * 別グループの行と並べても「連絡先が一致する」という手がかりが消える。
 */
interface Selection {
  groupKey: string;
  ids: string[];
}

export default function CustomerDuplicatesPage() {
  const [allowed, setAllowed] = useState<boolean | null>(null);
  useEffect(() => {
    const claims = readTokenClaims();
    setAllowed(hasPermission(claims, 'CUSTOMER_MANAGE') && hasPermission(claims, 'CUSTOMER_MERGE'));
  }, []);
  if (allowed === null) return <p>読み込み中...</p>;
  if (!allowed) return <p role="alert">顧客の管理と統合の権限が必要です。</p>;
  return <CustomerDuplicatesContent />;
}

function CustomerDuplicatesContent() {
  const params = useParams();
  const storeId = params.storeId as string;
  const [searchTerm, setSearchTerm] = useState('');
  const [type, setType] = useState<ContactType | 'ALL'>('ALL');
  const {
    search,
    rows: groups,
    setRows: setGroups,
    isLoading,
    failed,
    hasMore,
    reload,
    loadMore,
  } = useCursorList(
    (cursor, criteria: { search?: string; type?: ContactType }) =>
      customerApi.duplicates({ cursor, ...criteria }),
    {}
  );
  const [selection, setSelection] = useState<Selection | null>(null);
  const [survivingId, setSurvivingId] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState<{
    survivingId: string;
    mergedId: string;
    survivingName: string;
    mergedName: string;
    movedOrderCount: number;
  } | null>(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const isSubmitting = isConfirming;

  const toggle = (groupKey: string, row: CustomerMergeComparisonResponse) => {
    const customerId = row.id ?? '';
    // 残す行の選択は選び直しのたびに捨てる。前の組み合わせで選んだ行が、次の組み合わせに
    // 残っていない状態で「残す行」として効いてしまわないように
    setSurvivingId(null);
    setSelection(current => {
      if (current === null || current.groupKey !== groupKey) {
        return { groupKey, ids: [customerId] };
      }
      if (current.ids.includes(customerId)) {
        return {
          ...current,
          ids: current.ids.filter(id => id !== customerId),
        };
      }
      if (current.ids.length >= PAIR_SIZE) return current;
      return { ...current, ids: [...current.ids, customerId] };
    });
  };

  const isSelected = (groupKey: string, customerId: string) =>
    selection?.groupKey === groupKey && selection.ids.includes(customerId);

  /** 選択中の 2 行を、候補一覧に並んでいる順のまま取り出す。 */
  const selectedPair = (
    rows: CustomerMergeComparisonResponse[]
  ): [CustomerMergeComparisonResponse, CustomerMergeComparisonResponse] | null => {
    const selected = rows.filter(row => row.id !== undefined && selection?.ids.includes(row.id));
    return selected.length === PAIR_SIZE ? [selected[0], selected[1]] : null;
  };

  const closeMergeAndReload = () => {
    setIsConfirming(false);
    setSelection(null);
    setSurvivingId(null);
    setGroups([]);
    reload();
  };

  const renderRows = (groupKey: string, rows: CustomerMergeComparisonResponse[]) => {
    const pair = selection?.groupKey === groupKey ? selectedPair(rows) : null;
    return (
      <>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-24">見比べる</TableHead>
              <TableHead>名前</TableHead>
              <TableHead>区分</TableHead>
              <TableHead>受注</TableHead>
              <TableHead>会員</TableHead>
              <TableHead>NG</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map(row => (
              <TableRow key={row.id}>
                <TableCell>
                  {/* flex の容器が要る。Checkbox の既定の描画要素は span で、素の
                              テーブルセルに置くと display:inline のまま size-4 が効かず 2px に潰れる
                              （既存の呼出は FormItem の flex がこれを担っていた） */}
                  <div className="flex items-center">
                    {/* 名前を含む aria-label を持たせる。同じ画面に同型の選択が並ぶので、
                                「見比べる」だけでは読み上げでどの行か判らない */}
                    <Checkbox
                      aria-label={`${row.name} を見比べる`}
                      checked={isSelected(groupKey, row.id ?? '')}
                      // 3 行目以降は組み合わせが決まらないので、2 行選んだ時点で塞ぐ
                      disabled={
                        isSubmitting ||
                        (!isSelected(groupKey, row.id ?? '') &&
                          selection?.groupKey === groupKey &&
                          selection.ids.length >= PAIR_SIZE)
                      }
                      onCheckedChange={() => toggle(groupKey, row)}
                    />
                  </div>
                </TableCell>
                <TableCell className="font-medium text-foreground">{row.name}</TableCell>
                <TableCell className="text-muted-foreground">{row.classification || '-'}</TableCell>
                <TableCell className="text-muted-foreground">{row.order_count} 件</TableCell>
                <TableCell>
                  {row.member_linked ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      紐づけ済み
                    </Badge>
                  ) : (
                    <span className="text-muted-foreground">未紐づけ</span>
                  )}
                </TableCell>
                <TableCell>
                  {row.ng_type ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-destructive/10 text-destructive-strong"
                    >
                      {row.ng_type}
                    </Badge>
                  ) : (
                    <span className="text-muted-foreground">-</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        {pair && (
          <CustomerMergeComparison
            rows={pair}
            survivingId={survivingId}
            onSurvivingChange={setSurvivingId}
            onMerge={() => {
              const surviving = pair.find(row => row.id === survivingId);
              const merged = pair.find(row => row.id !== survivingId);
              if (!surviving?.id || !merged?.id) return;
              setConfirmation({
                survivingId: surviving.id,
                mergedId: merged.id,
                survivingName: surviving.name ?? '',
                mergedName: merged.name ?? '',
                movedOrderCount: merged.order_count ?? 0,
              });
              setIsConfirming(true);
            }}
            disabled={isSubmitting}
          />
        )}
      </>
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">重複候補</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            連絡先が同じ顧客を手がかりとして並べています。同一人物かどうかはご自身で確かめてください。
          </p>
        </div>
        <Button render={<Link href={storePath(storeId, '/customers')} />} variant="outline">
          <ChevronLeftIcon />
          顧客一覧へ
        </Button>
      </div>

      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={event => {
          event.preventDefault();
          setSelection(null);
          setSurvivingId(null);
          setIsConfirming(false);
          setGroups([]);
          search({
            search: searchTerm.trim() || undefined,
            type: type === 'ALL' ? undefined : type,
          });
        }}
      >
        <div className="min-w-0 flex-1 space-y-2">
          <Label htmlFor="duplicate-search">連絡先で検索</Label>
          <Input
            id="duplicate-search"
            value={searchTerm}
            onChange={event => setSearchTerm(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="duplicate-type">種類</Label>
          <Select
            value={type}
            onValueChange={value => setType(value as ContactType | 'ALL')}
            items={{ ALL: 'すべて', ...contactLabels }}
          >
            <SelectTrigger id="duplicate-type">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">すべて</SelectItem>
              <SelectItem value="PHONE">電話</SelectItem>
              <SelectItem value="EMAIL">メール</SelectItem>
              <SelectItem value="LINE">LINE ID</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <Button type="submit" variant="outline">
          検索
        </Button>
      </form>
      <TableCard>
        {isLoading && groups.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : failed ? (
          // 読めなかった候補を空表示にすると「重複は無い」と嘘をつくことになる
          <RegionError
            message="重複候補の取得に失敗しました"
            onRetry={reload}
            className="justify-center p-8"
          />
        ) : groups.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">
            連絡先が重複している顧客はいません
          </div>
        ) : (
          groups.map(group => {
            const groupKey = JSON.stringify([group.matched_type, group.matched_value]);
            return (
              <div key={groupKey} className="border-b last:border-b-0">
                <div className="bg-muted/50 px-6 py-3">
                  <span className="text-sm">{contactLabels[group.matched_type]}</span>{' '}
                  <span className="break-all font-medium text-foreground">
                    {group.matched_value}
                  </span>
                  {/* 件数は total。桁外れのグループは行を並べないので、length を出すと
                      200 件のグループが 0 件と名乗る */}
                  <span className="ml-2 text-sm text-muted-foreground">{group.total} 件</span>
                </div>
                {group.customers.length === 0 ? (
                  <ExpandedCustomers type={group.matched_type} value={group.matched_value}>
                    {rows => renderRows(groupKey, rows)}
                  </ExpandedCustomers>
                ) : (
                  renderRows(groupKey, group.customers)
                )}
              </div>
            );
          })
        )}
        {hasMore && (
          <div className="flex justify-center border-t p-4">
            <Button variant="outline" onClick={loadMore} disabled={isLoading}>
              {isLoading ? '読み込み中...' : 'さらに読み込む'}
            </Button>
          </div>
        )}
      </TableCard>

      {/* 確認は候補の取り直しで消えないよう外殻の外に置く */}
      <CustomerMergeConfirmDialog
        open={isConfirming}
        survivingId={confirmation?.survivingId ?? ''}
        mergedId={confirmation?.mergedId ?? ''}
        onMerged={closeMergeAndReload}
        onClose={closeMergeAndReload}
      />
    </div>
  );
}

function ExpandedCustomers({
  type,
  value,
  children,
}: {
  type: ContactType;
  value: string;
  children: (rows: CustomerMergeComparisonResponse[]) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return open ? (
    <ExpandedCustomerRows type={type} value={value}>
      {children}
    </ExpandedCustomerRows>
  ) : (
    <div className="p-6">
      <p className="mb-3 text-sm text-muted-foreground">
        この連絡先を共有する顧客をページごとに確認できます。一致だけでは同一人物と判断できません。
      </p>
      <Button variant="outline" onClick={() => setOpen(true)}>
        顧客を表示
      </Button>
    </div>
  );
}

function ExpandedCustomerRows({
  type,
  value,
  children,
}: {
  type: ContactType;
  value: string;
  children: (rows: CustomerMergeComparisonResponse[]) => ReactNode;
}) {
  const list = useCursorList(cursor => customerApi.duplicateCustomers({ type, value, cursor }));
  if (list.isLoading) return <p className="p-6">読み込み中...</p>;
  if (list.failed)
    return (
      <RegionError message="候補顧客の取得に失敗しました" onRetry={list.reload} className="p-6" />
    );
  return (
    <>
      {list.rows.length === 0 ? <p className="p-6">該当する顧客はいません</p> : children(list.rows)}
      {list.hasMore && (
        <div className="p-4">
          <Button variant="outline" onClick={list.loadMore}>
            顧客をさらに読み込む
          </Button>
        </div>
      )}
    </>
  );
}
