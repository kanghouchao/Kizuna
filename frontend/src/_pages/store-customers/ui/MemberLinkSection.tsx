'use client';

import { useState } from 'react';
import { notify } from '@/shared/notify';
import { customerApi } from '@/entities/customer';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import {
  Badge,
  Button,
  ConfirmDialog,
  Input,
  RegionError,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 会員コードの桁数（数字のみ）。 */
const MEMBER_CODE_LENGTH = 12;

interface MemberLinkSectionProps {
  customerId: string;
}

/** 実行者と日時を 1 セルにまとめる（どちらも欠けうる — 実行者の削除で名前は NULL になる）。 */
function actorCell(name?: string, at?: string) {
  if (!at) return '-';
  return `${name || '不明'}・${new Date(at).toLocaleString('ja-JP')}`;
}

/** 顧客編集ページの会員紐づけ区画。紐づけ・変更・解除と、その履歴を扱う。 */
export function MemberLinkSection({ customerId }: MemberLinkSectionProps) {
  const { data, isLoading, failure, reload } = useResource(
    () => customerApi.memberLinkHistory(customerId),
    [customerId]
  );
  const history = data ?? [];
  const [memberCode, setMemberCode] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isConfirmingUnlink, setIsConfirmingUnlink] = useState(false);

  // 現に有効な区間は高々 1 件で、履歴は新しい順に返る
  const activeLink = history.find(row => row.status === 'ACTIVE');
  // 履歴が読めていない間は現況が不明。紐づけ POST は既存の有効区間を置き換えるため、
  // 未読込のまま操作させると読み取り失敗が誤解除に化ける — 読めるまで操作を止める
  const isHistoryReady = !isLoading && failure === null;

  const handleLink = async () => {
    try {
      setIsSubmitting(true);
      await customerApi.linkMember(customerId, memberCode);
      notify.success('会員を紐づけました');
      setMemberCode('');
      await reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '会員の紐づけに失敗しました'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleUnlink = async () => {
    setIsConfirmingUnlink(false);
    try {
      setIsSubmitting(true);
      await customerApi.unlinkMember(customerId);
      notify.success('会員の紐づけを解除しました');
      await reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '会員の紐づけ解除に失敗しました'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <TableCard>
        <div className="border-b bg-muted/50 px-6 py-4">
          <h2 className="text-lg font-medium text-foreground">会員紐づけ</h2>
        </div>

        <div className="space-y-4 px-6 py-4">
          <div className="flex items-center gap-2">
            {/* 失敗はこの区画で 1 度だけ名乗る（下の RegionError）。ここに書き足すと、
                回復手段を持たない二つ目の告知になる */}
            {!isHistoryReady ? (
              <span className="text-muted-foreground">
                {isLoading ? '読み込み中...' : '紐づけ状態は不明です'}
              </span>
            ) : activeLink ? (
              <>
                <Badge
                  variant="outline"
                  className="border-transparent bg-success/10 text-success-strong"
                >
                  紐づけ済み
                </Badge>
                <span className="text-foreground">{activeLink.member_code}</span>
              </>
            ) : (
              <span className="text-muted-foreground">未紐づけ</span>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Input
              type="text"
              value={memberCode}
              onChange={e => setMemberCode(e.target.value)}
              maxLength={MEMBER_CODE_LENGTH}
              className="w-full md:w-56"
              placeholder="会員コード（数字12桁）"
              aria-label="会員コード"
            />
            <Button
              onClick={() => void handleLink()}
              disabled={isSubmitting || !memberCode || !isHistoryReady}
            >
              紐づける
            </Button>
            {activeLink && (
              <Button
                variant="outline"
                onClick={() => setIsConfirmingUnlink(true)}
                disabled={isSubmitting || !isHistoryReady}
              >
                解除
              </Button>
            )}
          </div>
        </div>

        {isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : failure !== null ? (
          // 読めなかった履歴を残すと「紐づけ履歴がありません」に化ける。区画自身が失敗を名乗る
          <RegionError
            message="会員紐づけの履歴取得に失敗しました"
            onRetry={() => void reload()}
            className="justify-center p-8"
          />
        ) : history.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">紐づけ履歴がありません</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>会員コード</TableHead>
                <TableHead>状態</TableHead>
                <TableHead>紐づけ（実行者・日時）</TableHead>
                <TableHead>解除（実行者・日時）</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {history.map(row => (
                <TableRow key={row.id}>
                  <TableCell className="text-foreground">{row.member_code}</TableCell>
                  <TableCell>
                    {row.status === 'ACTIVE' ? (
                      <Badge
                        variant="outline"
                        className="border-transparent bg-success/10 text-success-strong"
                      >
                        紐づけ済み
                      </Badge>
                    ) : (
                      <span className="text-muted-foreground">解除済み</span>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {actorCell(row.linked_by_name, row.linked_at)}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {actorCell(row.released_by_name, row.released_at)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TableCard>

      {/* ダイアログは履歴の再取得で消えないよう外殻の外に置く */}
      <ConfirmDialog
        open={isConfirmingUnlink}
        title="会員の紐づけを解除しますか？"
        description="解除しても履歴は残ります。"
        confirmLabel="解除する"
        onConfirm={() => void handleUnlink()}
        onClose={() => setIsConfirmingUnlink(false)}
      />
    </>
  );
}
