'use client';

import Link from 'next/link';
import {
  PlusIcon,
  MagnifyingGlassIcon,
  PencilSquareIcon,
  TrashIcon,
} from '@heroicons/react/24/outline';
import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { CustomerResponse, customerApi } from '@/entities/customer';
import { toast } from 'react-hot-toast';
import { storePath } from '@/shared/lib';
import {
  Badge,
  Button,
  Card,
  CardContent,
  Input,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 顧客一覧ページ */
export default function CustomersPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const [customers, setCustomers] = useState<CustomerResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [rank, setRank] = useState('');
  const [classification, setClassification] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchCustomers = useCallback(
    async (pageNumber: number) => {
      try {
        setIsLoading(true);
        const response = await customerApi.list({
          page: pageNumber,
          size: 20,
          sort: 'createdAt,desc',
          search: search || undefined,
          rank: rank || undefined,
          classification: classification || undefined,
        });
        setCustomers(response.content);
        setTotalPages(response.total_pages);
        setPage(response.number);
      } catch {
        toast.error('顧客一覧の取得に失敗しました');
      } finally {
        setIsLoading(false);
      }
    },
    [search, rank, classification]
  );

  useEffect(() => {
    fetchCustomers(0);
    // 初回のみ取得。検索・絞り込みはボタン/Enter で明示的に実行する
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = () => {
    fetchCustomers(0);
  };

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`「${name}」を削除しますか？`)) return;
    try {
      await customerApi.delete(id);
      toast.success('顧客を削除しました');
      fetchCustomers(page);
    } catch {
      toast.error('顧客の削除に失敗しました');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">顧客管理</h1>
          <p className="text-sm text-gray-500 mt-1">顧客情報の登録・編集ができます。</p>
        </div>
        <Button asChild>
          <Link href={storePath(storeId, '/customers/create')}>
            <PlusIcon />
            新規顧客登録
          </Link>
        </Button>
      </div>

      {/* 検索・絞り込みバー */}
      <Card>
        <CardContent className="flex flex-col md:flex-row md:items-center gap-4">
          <div className="flex-1 relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <MagnifyingGlassIcon className="h-5 w-5 text-muted-foreground" />
            </div>
            <Input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
              className="pl-10"
              placeholder="名前・電話番号・LINE ID で検索..."
            />
          </div>
          <Input
            type="text"
            value={rank}
            onChange={e => setRank(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            className="w-full md:w-32"
            placeholder="ランク"
          />
          <Input
            type="text"
            value={classification}
            onChange={e => setClassification(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            className="w-full md:w-32"
            placeholder="区分"
          />
          <Button variant="outline" onClick={handleSearch}>
            検索
          </Button>
        </CardContent>
      </Card>

      {/* テーブル */}
      <Card className="py-0 overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : customers.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">顧客が登録されていません</div>
        ) : (
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
                        className="border-transparent bg-red-100 text-red-800"
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
                          <PencilSquareIcon />
                        </Link>
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => handleDelete(customer.id, customer.name)}
                      >
                        <TrashIcon />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      {/* ページネーション */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-4">
          <Button
            variant="outline"
            onClick={() => fetchCustomers(page - 1)}
            disabled={page <= 0 || isLoading}
          >
            前へ
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} / {totalPages} ページ
          </span>
          <Button
            variant="outline"
            onClick={() => fetchCustomers(page + 1)}
            disabled={page >= totalPages - 1 || isLoading}
          >
            次へ
          </Button>
        </div>
      )}
    </div>
  );
}
