'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  applicantApi,
  applicantStatusLabels,
  applicantStatusClasses,
  receptionChannelLabels,
  sourceTypeLabels,
  type ApplicantStatus,
  type ApplicantSummary,
} from '@/entities/applicant';
import { hasPermission, readTokenClaims, storePath, useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
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

export default function ApplicantsPage() {
  const { storeId } = useParams<{ storeId: string }>();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ApplicantStatus | ''>('');
  const [canManage, setCanManage] = useState(false);
  useEffect(() => {
    const claims = readTokenClaims();
    setCanManage(
      hasPermission(claims, 'RECRUITMENT_VIEW') && hasPermission(claims, 'RECRUITMENT_MANAGE')
    );
  }, []);
  const list = useListPage<ApplicantSummary, { search: string; status: ApplicantStatus | '' }>(
    (page, criteria) =>
      applicantApi.list({
        page,
        size: 20,
        search: criteria.search || undefined,
        status: criteria.status || undefined,
      }),
    { search: '', status: '' }
  );
  return (
    <ListPage
      title="応募者管理"
      description="受付・面接・選考の記録を担当店舗で管理します。"
      actions={
        canManage && (
          <Button render={<Link href={storePath(storeId, '/applicants/new')} />}>
            応募者を登録
          </Button>
        )
      }
      search={{
        onSearch: () => list.search({ search, status }),
        content: (
          <>
            <Input
              aria-label="応募者氏名"
              placeholder="氏名で検索"
              value={search}
              maxLength={100}
              onChange={e => setSearch(e.target.value)}
              className="max-w-sm"
            />
            <Select
              value={status || '__all__'}
              onValueChange={v => setStatus(v === '__all__' ? '' : (v as ApplicantStatus))}
              items={{ __all__: 'すべての状態', ...applicantStatusLabels }}
            >
              <SelectTrigger aria-label="選考状態" className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">すべての状態</SelectItem>
                {Object.entries(applicantStatusLabels).map(([v, label]) => (
                  <SelectItem key={v} value={v}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button type="submit">検索</Button>
          </>
        ),
      }}
      state={{ ...list, onPageChange: list.onPageChange }}
      onRetry={list.reload}
      emptyMessage="応募者は登録されていません"
      errorMessage="応募者一覧の取得に失敗しました"
    >
      <Table>
        <TableHeader>
          <TableRow>
            {['氏名', '選考状態', '受付チャネル', '応募元', '担当者', '受付日', '詳細'].map(
              label => (
                <TableHead key={label}>{label}</TableHead>
              )
            )}
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(row => (
            <TableRow key={row.id}>
              <TableCell className="max-w-48 truncate font-medium">{row.name}</TableCell>
              <TableCell>
                <Badge variant="outline" className={applicantStatusClasses[row.status]}>
                  {applicantStatusLabels[row.status]}
                </Badge>
              </TableCell>
              <TableCell>{receptionChannelLabels[row.channel]}</TableCell>
              <TableCell className="max-w-48 truncate">
                {row.source_media || sourceTypeLabels[row.source_type]}
              </TableCell>
              <TableCell className="max-w-40 truncate">{row.assignee || '未設定'}</TableCell>
              <TableCell>{new Date(row.created_at).toLocaleDateString('ja-JP')}</TableCell>
              <TableCell>
                <Button
                  variant="outline"
                  render={<Link href={storePath(storeId, `/applicants/${row.id}`)} />}
                >
                  詳細
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ListPage>
  );
}
