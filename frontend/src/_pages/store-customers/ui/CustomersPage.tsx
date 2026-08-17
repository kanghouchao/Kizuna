'use client';

import Link from 'next/link';
import { CopyIcon, PlusIcon, SearchIcon, SquarePenIcon, Trash2Icon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { CustomerResponse, customerApi } from '@/entities/customer';
import {
  hasPermission,
  readTokenClaims,
  storePath,
  useDeleteAction,
  useListPage,
} from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
  ConfirmDialog,
  Input,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 20;

/** 一覧の絞り込み条件（送信で確定した値） */
interface CustomerCriteria {
  search: string;
  rank: string;
  classification: string;
}

/** 顧客一覧ページ */
export default function CustomersPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const [search, setSearch] = useState('');
  const [rank, setRank] = useState('');
  const [classification, setClassification] = useState('');
  // 権限による UI 出し分け（強制はサーバ側 @PreAuthorize — ここは導線の表示制御のみ）。
  // token claim の authorities から読む。token 無し・壊れは導線を出さない（fail-closed）。
  const [canMerge, setCanMerge] = useState(false);
  useEffect(() => {
    setCanMerge(hasPermission(readTokenClaims(), 'CUSTOMER_MERGE'));
  }, []);

  const list = useListPage<CustomerResponse, CustomerCriteria>(
    (page, criteria) =>
      customerApi.list({
        page,
        size: PAGE_SIZE,
        // created_at は一意でない可能性があるため、offset ページングの境界を確定させる
        // 一意な副キーを添える（sort=prop1,prop2,direction は Spring Data の複数キー形式）
        sort: 'createdAt,id,desc',
        search: criteria.search || undefined,
        rank: criteria.rank || undefined,
        classification: criteria.classification || undefined,
      }),
    { search: '', rank: '', classification: '' }
  );
  const customers = list.rows;

  const deletion = useDeleteAction<CustomerResponse>({
    remove: customer => customerApi.delete(customer.id ?? ''),
    successMessage: '顧客を削除しました',
    errorMessage: '顧客の削除に失敗しました',
    onDeleted: list.reload,
  });

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
          onSearch: () => void list.search({ search, rank, classification }),
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
                  placeholder="名前・電話番号・LINE ID で検索..."
                />
              </div>
              <Input
                type="text"
                value={rank}
                onChange={e => setRank(e.target.value)}
                className="w-full md:w-32"
                placeholder="ランク"
              />
              <Input
                type="text"
                value={classification}
                onChange={e => setClassification(e.target.value)}
                className="w-full md:w-32"
                placeholder="区分"
              />
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
              <TableHead>名前</TableHead>
              <TableHead>電話番号</TableHead>
              <TableHead>LINE ID</TableHead>
              <TableHead>ランク</TableHead>
              <TableHead>区分</TableHead>
              <TableHead>会員</TableHead>
              <TableHead>NG</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {customers.map(customer => (
              <TableRow key={customer.id}>
                <TableCell className="font-medium text-foreground">{customer.name}</TableCell>
                <TableCell className="text-muted-foreground">
                  {customer.phone_number || '-'}
                </TableCell>
                <TableCell className="text-muted-foreground">{customer.line_id || '-'}</TableCell>
                <TableCell className="text-muted-foreground">{customer.rank || '-'}</TableCell>
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

      {/* ダイアログは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <ConfirmDialog
        open={deletion.target !== null}
        title={deletion.target ? `「${deletion.target.name}」を削除しますか？` : ''}
        onConfirm={() => void deletion.confirm()}
        onClose={deletion.cancel}
      />
    </>
  );
}
