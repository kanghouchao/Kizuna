'use client';

import { useEffect, useState } from 'react';
import { notify } from '@/shared/notify';
import { customerApi } from '@/entities/customer';
import { PointAdjustmentDialog } from './PointAdjustmentDialog';
import {
  getApiErrorMessage,
  hasPermission,
  readTokenClaims,
  useCursorList,
  useResource,
} from '@/shared/lib';
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
  // 現況は履歴から推し量らず専用の読み口から取る。未紐づけは 404 で返るので、
  // 「取れなかった」と「紐づいていない」が failure の種別で分かれる。
  const {
    data: activeLink,
    isLoading: isLinkLoading,
    failure: linkFailure,
    reload: reloadLink,
  } = useResource(() => customerApi.memberLink(customerId), [customerId]);
  const {
    rows: history,
    isLoading,
    failed,
    hasMore,
    reload,
    loadMore,
  } = useCursorList(cursor => customerApi.memberLinkHistory(customerId, { cursor }));
  // 残高は顧客ではなく紐づく会員の台帳が持つため、紐づけ履歴とは別の読み口から取る
  const {
    data: balance,
    setData: setBalance,
    isLoading: isBalanceLoading,
    failure: balanceFailure,
    reload: reloadBalance,
  } = useResource(() => customerApi.memberPointBalance(customerId), [customerId]);
  const [memberCode, setMemberCode] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isConfirmingUnlink, setIsConfirmingUnlink] = useState(false);
  const [isAdjusting, setIsAdjusting] = useState(false);
  // 権限による UI 出し分け（強制はサーバ側 @PreAuthorize — ここは導線の表示制御のみ）。
  // token claim の authorities から読む。token 無し・壊れは導線を出さない（fail-closed）。
  const [canAdjust, setCanAdjust] = useState(false);
  useEffect(() => {
    setCanAdjust(hasPermission(readTokenClaims(), 'POINT_ADJUST'));
  }, []);

  // 紐づけ POST は既存の有効区間を置き換えるため、現況が不明なまま操作させると
  // 読み取り失敗が誤解除に化ける。404（未紐づけ）は現況が判った状態なので操作を許す。
  const isLinkReady = !isLinkLoading && (linkFailure === null || linkFailure === 'notFound');

  const handleLink = async () => {
    try {
      setIsSubmitting(true);
      await customerApi.linkMember(customerId, memberCode);
      notify.success('会員を紐づけました');
      setMemberCode('');
      // 紐づく先が変われば残高の指す台帳も変わる。履歴と一緒に取り直す
      reload();
      await Promise.all([reloadLink(), reloadBalance()]);
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
      reload();
      await Promise.all([reloadLink(), reloadBalance()]);
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
            {/* 履歴の失敗は下の RegionError が 1 度だけ名乗る。ここに書き足すと、
                回復手段を持たない二つ目の告知になる（残高は別の読み口で、自分の再試行を持つ） */}
            {!isLinkReady ? (
              <span className="text-muted-foreground">
                {isLinkLoading ? '読み込み中...' : '紐づけ状態は不明です'}
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

          <div className="flex flex-wrap items-center gap-3">
            <span className="text-muted-foreground">ポイント残高</span>
            {isBalanceLoading ? (
              <span className="text-muted-foreground">読み込み中...</span>
            ) : balanceFailure !== null ? (
              // 読めなかった残高を「—」で描くと、未紐づけ（台帳が無い）と区別できなくなる
              <RegionError
                message="ポイント残高の取得に失敗しました"
                onRetry={() => void reloadBalance()}
              />
            ) : (
              balance !== null && (
                <span className="text-foreground">
                  {/* 未紐づけの顧客には台帳そのものが無い。0 ポイントではないので数を出さない */}
                  {balance.linked && balance.balance !== undefined
                    ? `${balance.balance} ポイント`
                    : '—'}
                </span>
              )
            )}
            {activeLink && canAdjust && (
              <Button variant="outline" size="sm" onClick={() => setIsAdjusting(true)}>
                ポイント調整
              </Button>
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
              disabled={isSubmitting || !memberCode || !isLinkReady}
            >
              紐づける
            </Button>
            {activeLink && (
              <Button
                variant="outline"
                onClick={() => setIsConfirmingUnlink(true)}
                disabled={isSubmitting || !isLinkReady}
              >
                解除
              </Button>
            )}
          </div>
        </div>

        {isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : failed ? (
          // 読めなかった履歴を残すと「紐づけ履歴がありません」に化ける。区画自身が失敗を名乗る
          <RegionError
            message="会員紐づけの履歴取得に失敗しました"
            onRetry={() => reload()}
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

        {hasMore && (
          <div className="flex justify-center border-t p-4">
            <Button variant="outline" onClick={() => loadMore()} disabled={isLoading}>
              さらに読み込む
            </Button>
          </div>
        )}
      </TableCard>

      {/* ダイアログは履歴の再取得で消えないよう外殻の外に置く */}
      <PointAdjustmentDialog
        customerId={customerId}
        open={isAdjusting}
        onClose={() => setIsAdjusting(false)}
        // 応答が調整後の残高を持つので、取り直さず差し替える（読み込み表示で一瞬消えない）
        onAdjusted={setBalance}
      />

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
