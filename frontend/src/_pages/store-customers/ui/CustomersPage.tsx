'use client';

import { contactLabels } from '../lib/contactLabels';

import Link from 'next/link';
import { CopyIcon, PlusIcon, SearchIcon, SquarePenIcon, Trash2Icon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import {
  CustomerMergeComparisonResponse,
  CustomerSummaryResponse,
  customerApi,
} from '@/entities/customer';
import {
  hasPermission,
  readTokenClaims,
  storePath,
  useDeleteAction,
  useListPage,
} from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import { CustomerMergePanel } from './CustomerMergePanel';
import { CustomerMergeConfirmDialog } from './CustomerMergeConfirmDialog';
import {
  Badge,
  Button,
  Checkbox,
  ConfirmDialog,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 20;

/** 見比べる対象は 2 行。3 行以上を一度に畳む導線は持たない（ADR 0010）。 */
const PAIR_SIZE = 2;

/** 一覧の絞り込み条件（送信で確定した値） */
interface CustomerCriteria {
  search: string;
  classification: string;
  sort: string;
}

const SORT_OPTIONS = [
  { value: 'createdAt,id,desc', label: '登録日時（新しい順）' },
  { value: 'lastVisitDate,desc', label: '最終来店日（新しい順）' },
  { value: 'lastVisitDate,asc', label: '最終来店日（古い順）' },
  { value: 'pointBalance,desc', label: '会員ポイント（多い順）' },
  { value: 'pointBalance,asc', label: '会員ポイント（少ない順）' },
];

/** 顧客一覧ページ */
export default function CustomersPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const [search, setSearch] = useState('');
  const [classification, setClassification] = useState('');
  const [sort, setSort] = useState(SORT_OPTIONS[0].value);
  // 権限による UI 出し分け（強制はサーバ側 @PreAuthorize — ここは導線の表示制御のみ）。
  // token claim の authorities から読む。token 無し・壊れは導線を出さない（fail-closed）。
  const [canMerge, setCanMerge] = useState(false);
  useEffect(() => {
    const claims = readTokenClaims();
    setCanMerge(
      hasPermission(claims, 'CUSTOMER_MERGE') && hasPermission(claims, 'CUSTOMER_MANAGE')
    );
  }, []);

  const list = useListPage<CustomerSummaryResponse, CustomerCriteria>(
    (page, criteria) =>
      customerApi.list({
        page,
        size: PAGE_SIZE,
        sort: criteria.sort,
        search: criteria.search || undefined,
        classification: criteria.classification || undefined,
      }),
    { search: '', classification: '', sort: SORT_OPTIONS[0].value }
  );
  const customers = list.rows;

  const deletion = useDeleteAction<CustomerSummaryResponse>({
    remove: customer => customerApi.delete(customer.id),
    successMessage: '顧客を削除しました',
    errorMessage: '顧客の削除に失敗しました',
    onDeleted: list.reload,
  });

  // 統合する 2 行の選択。行そのものではなく ID だけを持つので、ページ送りや検索で
  // 一覧が入れ替わっても選択は残る（統合したい 2 行が同じページに並ぶとは限らない）
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  // 統合の実行中は選択を変えさせない。変えると見比べる区画ごと消え、取り返しのつかない操作の
  // 確認が在途のまま画面から失せる
  const pair =
    selectedIds.length === PAIR_SIZE ? ([selectedIds[0], selectedIds[1]] as const) : null;

  const [mergeTarget, setMergeTarget] = useState<{
    surviving: CustomerMergeComparisonResponse;
    merged: CustomerMergeComparisonResponse;
  } | null>(null);
  const [mergeOpen, setMergeOpen] = useState(false);
  const isMerging = mergeOpen;

  const closeMergeAndReload = () => {
    setMergeOpen(false);
    setSelectedIds([]);
    list.reload();
  };

  const toggleSelected = (customerId: string) =>
    setSelectedIds(current =>
      current.includes(customerId)
        ? current.filter(id => id !== customerId)
        : current.length >= PAIR_SIZE
          ? current
          : [...current, customerId]
    );

  return (
    <>
      <ListPage
        title="顧客管理"
        description="顧客情報の登録・編集ができます。"
        actions={
          <>
            {canMerge && (
              <Button
                render={<Link href={storePath(storeId, '/customers/duplicates')} />}
                variant="outline"
              >
                <CopyIcon />
                重複候補
              </Button>
            )}
            <Button render={<Link href={storePath(storeId, '/customers/create')} />}>
              <PlusIcon />
              新規顧客登録
            </Button>
          </>
        }
        search={{
          onSearch: () => void list.search({ search, classification, sort }),
          content: (
            <>
              <div className="flex-1 relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <SearchIcon className="h-5 w-5 text-muted-foreground" />
                </div>
                <Input
                  type="text"
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  className="pl-10"
                  placeholder="名前・電話・メール・LINE ID で検索..."
                />
              </div>
              <Input
                type="text"
                value={classification}
                onChange={e => setClassification(e.target.value)}
                className="w-full md:w-32"
                placeholder="区分"
              />
              <Select
                items={SORT_OPTIONS}
                value={sort}
                onValueChange={value => {
                  if (!value) return;
                  setSort(value);
                  void list.search(previous => ({ ...previous, sort: value }));
                }}
              >
                <SelectTrigger aria-label="並び順" className="w-full md:w-56">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SORT_OPTIONS.map(option => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button type="submit" variant="outline">
                検索
              </Button>
            </>
          ),
        }}
        state={list}
        emptyMessage="顧客が登録されていません"
        errorMessage="顧客一覧の取得に失敗しました"
        onRetry={list.reload}
      >
        <Table>
          <TableHeader>
            <TableRow>
              {canMerge && <TableHead className="w-24">見比べる</TableHead>}
              <TableHead>名前</TableHead>
              <TableHead>電話番号</TableHead>
              <TableHead>メール</TableHead>
              <TableHead>LINE ID</TableHead>
              <TableHead>区分</TableHead>
              <TableHead>会員</TableHead>
              <TableHead>最終来店日</TableHead>
              <TableHead className="text-right">会員ポイント</TableHead>
              <TableHead>NG</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {customers.map(customer => (
              <TableRow key={customer.id}>
                {canMerge && (
                  <TableCell>
                    {/* flex の容器が要る。Checkbox の既定の描画要素は span で、素のテーブルセルに
                        置くと display:inline のまま size-4 が効かず 2px に潰れる */}
                    <div className="flex items-center">
                      {/* 名前を含む aria-label を持たせる。同型の選択が行の数だけ並ぶので、
                          「見比べる」だけでは読み上げでどの行か判らない */}
                      <Checkbox
                        aria-label={`${customer.name} を見比べる`}
                        checked={selectedIds.includes(customer.id ?? '')}
                        // 3 行目以降は組み合わせが決まらないので、2 行選んだ時点で塞ぐ
                        disabled={
                          isMerging ||
                          (!selectedIds.includes(customer.id ?? '') &&
                            selectedIds.length >= PAIR_SIZE)
                        }
                        onCheckedChange={() => toggleSelected(customer.id ?? '')}
                      />
                    </div>
                  </TableCell>
                )}
                <TableCell className="font-medium text-foreground">
                  {customer.name}
                  {customer.matched_contacts.map(contact => (
                    <div
                      key={contact.id}
                      className="max-w-64 break-all whitespace-normal text-sm font-normal"
                    >
                      一致: {contactLabels[contact.type]} {contact.value}
                    </div>
                  ))}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {customer.preferred_contacts.find(contact => contact.type === 'PHONE')?.value ||
                    '-'}
                </TableCell>
                <TableCell className="max-w-64 break-all text-muted-foreground">
                  {customer.preferred_contacts.find(contact => contact.type === 'EMAIL')?.value ||
                    '-'}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {customer.preferred_contacts.find(contact => contact.type === 'LINE')?.value ||
                    '-'}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {customer.classification || '-'}
                </TableCell>
                <TableCell>
                  {customer.member_linked ? (
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
                <TableCell className="whitespace-nowrap">
                  {customer.last_visit_date ?? '来店なし'}
                </TableCell>
                <TableCell className="text-right tabular-nums whitespace-nowrap">
                  {customer.point_balance === undefined
                    ? '未関連'
                    : `${customer.point_balance.toLocaleString('ja-JP')} pt`}
                </TableCell>
                <TableCell>
                  {customer.ng_type ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-destructive/10 text-destructive-strong"
                    >
                      {customer.ng_type}
                    </Badge>
                  ) : (
                    <span className="text-muted-foreground">-</span>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    <Button
                      render={<Link href={storePath(storeId, `/customers/${customer.id}/edit`)} />}
                      variant="ghost"
                      size="icon-sm"
                    >
                      <SquarePenIcon />
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => deletion.ask(customer)}>
                      <Trash2Icon />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {/* 見比べは一覧の外側に置く。外殻の children は loading / 空表示 / 失敗のときに隠れるので、
          中に置くと「2 行を選んでから 0 件になる検索をした」だけで、選択が残ったまま区画が消える */}
      {canMerge && selectedIds.length > 0 && (
        <div className="mt-6 space-y-6">
          <div className="flex items-center justify-between rounded-md border bg-muted/30 px-4 py-3">
            <p className="text-sm text-muted-foreground">
              {selectedIds.length} 件を選択中
              {pair === null && '（統合するにはもう 1 行選んでください）'}
            </p>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={isMerging}
              onClick={() => setSelectedIds([])}
            >
              選択を解除
            </Button>
          </div>
          {pair && (
            <CustomerMergePanel
              customerIds={[pair[0], pair[1]]}
              onMerge={(surviving, merged) => {
                setMergeTarget({ surviving, merged });
                setMergeOpen(true);
              }}
              onClear={() => setSelectedIds([])}
              isSubmitting={isMerging}
            />
          )}
        </div>
      )}

      {/* ダイアログは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <CustomerMergeConfirmDialog
        open={mergeOpen}
        survivingId={mergeTarget?.surviving.id ?? ''}
        mergedId={mergeTarget?.merged.id ?? ''}
        onMerged={closeMergeAndReload}
        onClose={closeMergeAndReload}
      />
      <ConfirmDialog
        open={deletion.open}
        title={deletion.target ? `「${deletion.target.name}」を削除しますか？` : ''}
        onConfirm={() => void deletion.confirm()}
        onClose={deletion.cancel}
      />
    </>
  );
}
