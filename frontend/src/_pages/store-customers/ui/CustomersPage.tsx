'use client';

import Link from 'next/link';
import { PlusIcon, SearchIcon, SquarePenIcon, Trash2Icon } from 'lucide-react';
import { useState } from 'react';
import { useParams } from 'next/navigation';
import { CustomerResponse, customerApi } from '@/entities/customer';
import { toast } from 'react-hot-toast';
import { storePath, useListPage } from '@/shared/lib';
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

/** 顧客一覧ページ */
export default function CustomersPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const [search, setSearch] = useState('');
  const [rank, setRank] = useState('');
  const [classification, setClassification] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<CustomerResponse | null>(null);

  const list = useListPage(
    page =>
      customerApi.list({
        page,
        size: PAGE_SIZE,
        sort: 'createdAt,desc',
        search: search || undefined,
        rank: rank || undefined,
        classification: classification || undefined,
      }),
    '顧客一覧の取得に失敗しました'
  );
  const customers = list.rows;

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await customerApi.delete(deleteTarget.id);
      toast.success('顧客を削除しました');
      void list.onPageChange(list.page);
    } catch {
      toast.error('顧客の削除に失敗しました');
    }
  };

  return (
    <>
      <ListPage
        title="顧客管理"
        description="顧客情報の登録・編集ができます。"
        actions={
          <Button asChild>
            <Link href={storePath(storeId, '/customers/create')}>
              <PlusIcon />
              新規顧客登録
            </Link>
          </Button>
        }
        search={{
          onSearch: list.search,
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
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名前</TableHead>
              <TableHead>電話番号</TableHead>
              <TableHead>LINE ID</TableHead>
              <TableHead>ランク</TableHead>
              <TableHead>区分</TableHead>
              <TableHead>ポイント</TableHead>
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
                <TableCell className="text-muted-foreground">{customer.points ?? 0}</TableCell>
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
                    <Button asChild variant="ghost" size="icon-sm">
                      <Link href={storePath(storeId, `/customers/${customer.id}/edit`)}>
                        <SquarePenIcon />
                      </Link>
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => setDeleteTarget(customer)}
                    >
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
        open={deleteTarget !== null}
        title={deleteTarget ? `「${deleteTarget.name}」を削除しますか？` : ''}
        onConfirm={() => void handleDelete()}
        onClose={() => setDeleteTarget(null)}
      />
    </>
  );
}
