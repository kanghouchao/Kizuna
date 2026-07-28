'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useParams } from 'next/navigation';
import { PlusIcon, SearchIcon, SquarePenIcon, Trash2Icon, SettingsIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { CastResponse, castApi, castInvitationStatusLabel } from '@/entities/cast';
import { platformAuthApi } from '@/entities/user';
import { InvitationButton, InvitationModal, IssuedInvitation } from '@/features/cast-invitation';
import { storePath, useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import { toast } from 'react-hot-toast';
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

/** キャスト一覧ページ */
export default function CastListPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const [search, setSearch] = useState('');
  const [issuedInvitation, setIssuedInvitation] = useState<IssuedInvitation | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CastResponse | null>(null);
  // 能力による UI 出し分け（強制はサーバ側 @PreAuthorize — ここは導線の表示制御のみ）
  const [canInvite, setCanInvite] = useState(false);
  const [canManageFieldDefs, setCanManageFieldDefs] = useState(false);
  useEffect(() => {
    platformAuthApi
      .me()
      .then(me => {
        setCanInvite(me.capabilities.includes('CAST_INVITE'));
        setCanManageFieldDefs(me.capabilities.includes('CAST_FIELD_DEF_MANAGE'));
      })
      .catch(() => {
        // 取得失敗時は導線を出さない（fail-closed）。操作自体はサーバ側が拒否する。
      });
  }, []);
  const list = useListPage(
    page =>
      castApi.list({
        page,
        size: PAGE_SIZE,
        sort: 'displayOrder,asc',
        search: search || undefined,
      }),
    'キャスト一覧の取得に失敗しました'
  );
  const casts = list.rows;

  /** 招待発行成功時: モーダル表示と一覧の再取得を行う（isLoading に連動して行がアンマウントされても、
   *  モーダル state はページ層が持つためモーダルは表示され続ける） */
  const handleIssued = (result: IssuedInvitation) => {
    setIssuedInvitation(result);
    void list.reload();
  };

  /** キャストを削除する */
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await castApi.delete(deleteTarget.id);
      toast.success('キャストを削除しました');
      void list.reload();
    } catch {
      toast.error('キャストの削除に失敗しました');
    }
  };

  /** ステータスの表示ラベルと配色を返す */
  const statusLabel = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return { text: '有効', color: 'bg-success/10 text-success-strong' };
      case 'INACTIVE':
        return { text: '無効', color: 'bg-muted text-foreground' };
      default:
        return { text: status, color: 'bg-muted text-foreground' };
    }
  };

  return (
    <>
      <ListPage
        title="キャスト管理"
        description="キャスト情報の登録・編集ができます。"
        actions={
          <>
            {/* 定義管理ページ（/store/casts/fields）への入口。定義CRUDは CAST_FIELD_DEF_MANAGE 能力限定。 */}
            {canManageFieldDefs && (
              <Button asChild variant="outline">
                <Link href={storePath(storeId, '/casts/fields')}>
                  <SettingsIcon />
                  カスタムフィールド管理
                </Link>
              </Button>
            )}
            <Button asChild>
              <Link href={storePath(storeId, '/casts/create')}>
                <PlusIcon />
                新規キャスト登録
              </Link>
            </Button>
          </>
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
                  placeholder="名前で検索..."
                />
              </div>
              <Button type="submit" variant="outline">
                検索
              </Button>
            </>
          ),
        }}
        state={list}
        emptyMessage="キャストが登録されていません"
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>写真</TableHead>
              <TableHead>名前</TableHead>
              <TableHead>年齢</TableHead>
              <TableHead>スリーサイズ</TableHead>
              <TableHead>表示順</TableHead>
              <TableHead>ステータス</TableHead>
              <TableHead>招待状態</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {casts.map(cast => {
              const status = statusLabel(cast.status);
              const invitation = castInvitationStatusLabel(cast.invitation_status);
              return (
                <TableRow key={cast.id}>
                  <TableCell>
                    <div className="h-12 w-10 rounded overflow-hidden bg-muted relative">
                      {cast.photo_url ? (
                        <Image
                          src={cast.photo_url}
                          alt={cast.name}
                          fill
                          className="object-cover"
                          sizes="40px"
                        />
                      ) : (
                        <div className="flex items-center justify-center h-full text-foreground text-xs">
                          No
                        </div>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="font-medium text-foreground">{cast.name}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {cast.age ? `${cast.age}歳` : '-'}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {cast.bust && cast.waist && cast.hip
                      ? `B${cast.bust} W${cast.waist} H${cast.hip}`
                      : '-'}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{cast.display_order ?? 0}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className={`border-transparent ${status.color}`}>
                      {status.text}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className={`border-transparent ${invitation.color}`}>
                      {invitation.text}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      {canInvite && (
                        <InvitationButton
                          castId={cast.id}
                          status={cast.invitation_status}
                          onIssued={handleIssued}
                        />
                      )}
                      <Button asChild variant="ghost" size="icon-sm">
                        <Link href={storePath(storeId, `/casts/${cast.id}/edit`)}>
                          <SquarePenIcon />
                        </Link>
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => setDeleteTarget(cast)}>
                        <Trash2Icon />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </ListPage>

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <InvitationModal
        open={issuedInvitation !== null}
        link={
          issuedInvitation && typeof window !== 'undefined'
            ? `${window.location.origin}/platform/invite/${issuedInvitation.token}`
            : ''
        }
        expiresAt={issuedInvitation?.expiresAt ?? null}
        onClose={() => setIssuedInvitation(null)}
      />
      <ConfirmDialog
        open={deleteTarget !== null}
        title={deleteTarget ? `「${deleteTarget.name}」を削除しますか？` : ''}
        onConfirm={() => void handleDelete()}
        onClose={() => setDeleteTarget(null)}
      />
    </>
  );
}
