'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import {
  AdminMemberDetail,
  AdminMemberLedgerEntry,
  AdminMemberLink,
  MEMBER_RANK_LABELS,
  adminMemberApi,
} from '@/entities/member';
import { POINT_ENTRY_TYPE_LABELS, PointEntryType } from '@/entities/point';
import { hasPermission, readTokenClaims, useCursorList, useResource } from '@/shared/lib';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  RegionError,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { StaffWithdrawalDialog } from './StaffWithdrawalDialog';
import { formatDateTime, formatSignedPoints } from './format';

/** 明細の 1 回の読み込み件数。続きはカーソルで 1 回ずつ継ぎ足す。 */
const ENTRY_PAGE_SIZE = 20;

const LINK_STATUS_LABELS = { ACTIVE: '有効', RELEASED: '解除済み' } as const;
const LINK_REASON_LABELS = {
  MEMBER_CODE: '会員コード提示',
  MEMBER_REQUEST: '会員申請',
  MIGRATION: '移行',
} as const;

const BACK = { href: '/admin/members', label: '一覧に戻る' };

export function AdminMemberDetailPage() {
  const { id } = useParams<{ id: string }>();
  return <MemberDetail key={id} id={Number(id)} />;
}

function MemberDetail({ id }: { id: number }) {
  const member = useResource(() => adminMemberApi.get(id), [id]);
  // 退会操作は MEMBER_WITHDRAW 保持者にだけ見せる。cookie はクライアントでしか読めないので effect で判定する
  const [canWithdraw, setCanWithdraw] = useState(false);
  useEffect(() => {
    setCanWithdraw(hasPermission(readTokenClaims(), 'MEMBER_WITHDRAW'));
  }, []);
  const [withdrawalOpen, setWithdrawalOpen] = useState(false);

  if (member.isLoading) return <p role="status">読み込み中...</p>;
  if (member.failure === 'notFound')
    return <RegionError message="会員が見つかりません" fallback={BACK} />;
  if (member.failure)
    return (
      <RegionError
        message="会員情報を取得できません。閲覧権限を確認してください。"
        onRetry={member.reload}
      />
    );
  if (!member.data) return null;
  const data = member.data;
  const withdrawn = data.withdrawal !== undefined;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">会員詳細</h1>
        <div className="flex items-center gap-3">
          {/* 退会済みは操作の代わりに退会情報を読み取り専用で見せる（下の区画） */}
          {canWithdraw && !withdrawn && (
            <Button type="button" variant="destructive" onClick={() => setWithdrawalOpen(true)}>
              退会させる
            </Button>
          )}
          <Button variant="outline" render={<Link href={BACK.href} />}>
            {BACK.label}
          </Button>
        </div>
      </div>

      <Profile data={data} />
      {data.withdrawal && <Withdrawal data={data} />}
      <Points data={data} />
      <RankHistory data={data} />
      <Links data={data} />
      <LedgerEntries id={id} />

      {canWithdraw && (
        <StaffWithdrawalDialog
          open={withdrawalOpen}
          memberId={id}
          memberLabel={`${data.display_name ?? ''}（${data.member_code ?? ''}）`}
          onClose={() => setWithdrawalOpen(false)}
          onWithdrawn={() => void member.reload()}
        />
      )}
    </div>
  );
}

function Profile({ data }: { data: AdminMemberDetail }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          基本情報
        </CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-4 md:grid-cols-3">
          <div>
            <dt className="text-sm text-muted-foreground">会員コード</dt>
            <dd className="font-mono text-foreground">{data.member_code}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">表示名</dt>
            <dd className="text-foreground">{data.display_name}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">メールアドレス</dt>
            <dd className="text-foreground">{data.email}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">状態</dt>
            <dd>
              {data.withdrawal ? (
                <Badge variant="outline" className="border-transparent bg-muted text-foreground">
                  退会済み
                </Badge>
              ) : (
                <Badge
                  variant="outline"
                  className="border-transparent bg-success/10 text-success-strong"
                >
                  有効
                </Badge>
              )}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">ランク</dt>
            <dd className="text-foreground">{data.rank ? MEMBER_RANK_LABELS[data.rank] : '—'}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">LINE 連携</dt>
            <dd className="text-foreground">{data.line_linked ? '連携済み' : '未連携'}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">登録日時</dt>
            <dd className="text-foreground">{formatDateTime(data.registered_at)}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  );
}

function Withdrawal({ data }: { data: AdminMemberDetail }) {
  const withdrawal = data.withdrawal;
  if (!withdrawal) return null;
  const operator = withdrawal.by_self
    ? '本人'
    : (withdrawal.withdrawn_by_name ?? '（削除された職員）');
  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          退会情報
        </CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-2 gap-4 md:grid-cols-3">
          <div>
            <dt className="text-sm text-muted-foreground">退会日時</dt>
            <dd className="text-foreground">{formatDateTime(withdrawal.withdrawn_at)}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">操作者</dt>
            <dd className="text-foreground">{operator}</dd>
          </div>
          <div className="col-span-2 md:col-span-3">
            <dt className="text-sm text-muted-foreground">理由</dt>
            <dd className="whitespace-pre-wrap text-foreground">{withdrawal.reason}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  );
}

function Points({ data }: { data: AdminMemberDetail }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          ポイント残高
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-3xl font-bold tracking-wider text-foreground">
          {data.balance.toLocaleString('ja-JP')} pt
        </p>
        {data.balances.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>有効期限</TableHead>
                <TableHead className="text-right">残高</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.balances.map(balance => (
                <TableRow key={balance.expires_on ?? 'none'}>
                  <TableCell className="text-foreground">
                    {balance.expires_on ?? '期限なし'}
                  </TableCell>
                  <TableCell className="text-right text-foreground">
                    {balance.points.toLocaleString('ja-JP')} pt
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function RankHistory({ data }: { data: AdminMemberDetail }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          ランク履歴
        </CardTitle>
      </CardHeader>
      <CardContent>
        {data.rank_history.length === 0 ? (
          <p className="text-sm text-muted-foreground">昇格の履歴はありません。</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>昇格日時</TableHead>
                <TableHead>遷移</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.rank_history.map((transition, index) => (
                <TableRow key={index}>
                  <TableCell className="text-foreground">
                    {formatDateTime(transition.promoted_at)}
                  </TableCell>
                  <TableCell className="text-foreground">
                    {transition.previous_rank ? MEMBER_RANK_LABELS[transition.previous_rank] : '—'}{' '}
                    → {transition.new_rank ? MEMBER_RANK_LABELS[transition.new_rank] : '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function LinkRow({ link }: { link: AdminMemberLink }) {
  return (
    <TableRow>
      <TableCell className="text-foreground">{link.store_name ?? '（削除された店舗）'}</TableCell>
      <TableCell className="text-foreground">
        {link.customer_name ?? '（削除された顧客）'}
      </TableCell>
      <TableCell>
        {link.status === 'ACTIVE' ? (
          <Badge variant="outline" className="border-transparent bg-success/10 text-success-strong">
            {LINK_STATUS_LABELS.ACTIVE}
          </Badge>
        ) : (
          <Badge variant="outline" className="border-transparent bg-muted text-foreground">
            {LINK_STATUS_LABELS.RELEASED}
          </Badge>
        )}
      </TableCell>
      <TableCell className="text-muted-foreground">
        {link.reason ? LINK_REASON_LABELS[link.reason] : '—'}
      </TableCell>
      <TableCell className="text-muted-foreground">{formatDateTime(link.linked_at)}</TableCell>
      <TableCell className="text-muted-foreground">
        {link.released_at
          ? `${formatDateTime(link.released_at)} ${link.release_reason ?? ''}`
          : '—'}
      </TableCell>
    </TableRow>
  );
}

function Links({ data }: { data: AdminMemberDetail }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          店舗との関連
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 店舗ごとに最新の区間だけ。紐づけ直しの全履歴は店舗側の顧客詳細が持つ */}
        <p className="text-sm text-muted-foreground">
          店舗ごとの最新の関連です。紐づけの履歴は各店舗の顧客詳細で確認できます。
        </p>
        {data.links.length === 0 ? (
          <p className="text-sm text-muted-foreground">担当店舗での関連はありません。</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>店舗</TableHead>
                <TableHead>顧客</TableHead>
                <TableHead>状態</TableHead>
                <TableHead>成立根拠</TableHead>
                <TableHead>紐づけ日時</TableHead>
                <TableHead>解除</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.links.map(link => (
                <LinkRow key={link.id} link={link} />
              ))}
            </TableBody>
          </Table>
        )}
        {/* 閲覧者の店舗集合の外は展開しない。店舗数だけで「他にもある」事実を伝える */}
        {data.other_store_link_count > 0 && (
          <p className="text-sm text-muted-foreground">
            担当外の {data.other_store_link_count} 店舗に関連があります。
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function entryTypeLabel(entry: AdminMemberLedgerEntry): string {
  const type = entry.entry_type as PointEntryType | undefined;
  return type && type in POINT_ENTRY_TYPE_LABELS
    ? POINT_ENTRY_TYPE_LABELS[type]
    : (entry.entry_type ?? '—');
}

function LedgerEntries({ id }: { id: number }) {
  const entries = useCursorList<AdminMemberLedgerEntry>(cursor =>
    adminMemberApi.pointEntries(id, { cursor, size: ENTRY_PAGE_SIZE })
  );
  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          ポイント明細
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {entries.failed ? (
          <RegionError message="明細を取得できませんでした。" onRetry={entries.reload} />
        ) : entries.isLoading && entries.rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">読み込み中...</p>
        ) : entries.rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">ポイントの増減はまだありません。</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>日時</TableHead>
                <TableHead>種別</TableHead>
                <TableHead className="text-right">増減</TableHead>
                <TableHead>有効期限</TableHead>
                <TableHead>発生店舗</TableHead>
                <TableHead>理由</TableHead>
                <TableHead>実行者</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {/* 行は末尾に継ぎ足されるだけで並べ替えも削除も起きないため、位置がそのまま識別子になる */}
              {entries.rows.map((entry, index) => (
                <TableRow key={index}>
                  <TableCell className="text-foreground">
                    {formatDateTime(entry.occurred_at)}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant="outline"
                      className="border-transparent bg-muted text-foreground"
                    >
                      {entryTypeLabel(entry)}
                    </Badge>
                  </TableCell>
                  <TableCell
                    className={`text-right ${entry.amount > 0 ? 'text-primary-strong' : 'text-foreground'}`}
                  >
                    {formatSignedPoints(entry.amount)}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{entry.expires_on ?? '—'}</TableCell>
                  <TableCell className="text-muted-foreground">{entry.store_name ?? '—'}</TableCell>
                  <TableCell className="text-muted-foreground">{entry.reason ?? '—'}</TableCell>
                  <TableCell className="text-muted-foreground">{entry.actor_name ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {entries.hasMore && (
          <Button
            type="button"
            variant="outline"
            onClick={entries.loadMore}
            disabled={entries.isLoading}
          >
            {entries.isLoading ? '読み込み中...' : 'さらに読み込む'}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
