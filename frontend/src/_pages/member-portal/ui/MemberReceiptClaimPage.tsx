'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { memberReceiptApi } from '@/entities/order';
import { getApiErrorMessage, isNotFound } from '@/shared/lib';
import { Button, Card, CardContent, CardHeader, CardTitle, RegionError } from '@/shared/ui';

/**
 * 申領の失敗。理由が変わらない失敗（＝サーバが同形で返す申領不能）だけは再試行を出さない。
 *
 * <p>逃げ道は失敗の意味で分かれる。結果が不明なときだけは来店履歴へ送る — 確かめる先がそこしか無い。
 */
type ClaimFailure = { message: string; retryable: boolean; verifyInHistory?: boolean };

/**
 * 伝票トークンの申領画面。店舗が完了時に提示した QR から来て、その来店を本人の履歴とポイントへ取り込む。
 *
 * トークンはフラグメントで届く（申領 URL の組み立ては店舗側の `receiptClaimUrl`）。パスも問い合わせ文字列も
 * リクエストターゲットとして送られ、リバースプロキシとアプリのアクセスログに 90 日有効の生値が残るため、
 * サーバへ送られないフラグメントで受け取り、この画面が申領要求の本体へ載せ替える。
 *
 * 未ログインで開いた場合はシェルがログインへ送り、フラグメントごと戻り先を預ける（`rememberMemberReturnPath`）。
 * この画面自身は認証を判定しない — 戻ってきた時点では常にログイン済みで、フラグメントも復元されている。
 */
export function MemberReceiptClaimPage() {
  const [token, setToken] = useState<string | null>(null);
  const [grantedPoints, setGrantedPoints] = useState<number | null>(null);
  const [failure, setFailure] = useState<ClaimFailure | null>(null);
  // 申領は取り返しがつかず、二度目は同形のエラーで返る。効果が二度走る場面（StrictMode の
  // 二重 mount）で二度投げると、成立した申領が失敗の画面に化ける。
  const claiming = useRef(false);
  // 何度目の要求か。応答を取り落とした後の再試行は、サーバ側で既に成立している可能性がある。
  const attempts = useRef(0);

  const claim = useCallback(async (raw: string) => {
    if (claiming.current) {
      return;
    }
    claiming.current = true;
    attempts.current += 1;
    const isRetry = attempts.current > 1;
    setFailure(null);
    try {
      const claimed = await memberReceiptApi.claim(raw);
      setGrantedPoints(claimed.granted_points);
    } catch (error) {
      // 応答だけを取り落とした要求（サーバは申領済み）の再試行は、使用済みとして同形の 404 で返る。
      // 不在・期限切れと区別が付かないのはサーバの意図（受注の存在を漏らさない）なので、区別できない
      // ことを引き受けて「確かめる先」を出す — 言い切ると、取り込めている来店を失敗と読ませてしまう。
      const indeterminate = isRetry && isNotFound(error);
      setFailure({
        // 申領できない理由（無効・期限切れ・使用済み）はサーバが同形の 1 文言に畳んでいる。
        // 画面で言い換えると、その文言だけが理由を語れる意味が失われる。
        message: indeterminate
          ? '取り込めたかどうかを確認できませんでした。来店履歴をご確認ください。'
          : getApiErrorMessage(error, '取り込めませんでした。通信の状態をご確認ください。'),
        retryable: !isNotFound(error),
        verifyInHistory: indeterminate,
      });
    } finally {
      claiming.current = false;
    }
  }, []);

  useEffect(() => {
    let raw: string;
    try {
      raw = decodeURIComponent(window.location.hash.slice(1));
    } catch {
      // 壊れたパーセント符号化（単独の % など）。復元できない以上、伝票は特定できない。
      raw = '';
    }
    if (!raw) {
      setFailure({
        message: '伝票の QR を読み取り直してください。',
        retryable: false,
      });
      return;
    }
    setToken(raw);
    void claim(raw);
    // フラグメントは入場時の一度だけ読む（token はこの効果自身の setToken 由来のため、依存に入れると再申領が回る）
  }, [claim]);

  return (
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">伝票の取り込み</h1>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            来店の取り込み
          </CardTitle>
        </CardHeader>
        <CardContent>
          {failure !== null ? (
            failure.retryable && token !== null ? (
              <RegionError message={failure.message} onRetry={() => void claim(token)} />
            ) : (
              // 押しても結果の変わらない失敗に再試行を出さない（DESIGN.md「領域内エラー態」）。
              // 逃げ道は、結果が不明なら確かめられる来店履歴、そうでなければホーム
              // （伝票そのものの取り直しは店舗に尋ねるしかない）
              <RegionError
                message={failure.message}
                fallback={
                  failure.verifyInHistory
                    ? { href: '/member/visits/', label: '来店履歴へ' }
                    : { href: '/member/', label: 'ホームへ' }
                }
              />
            )
          ) : grantedPoints === null ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : (
            <div className="flex flex-col items-center gap-3">
              <p className="text-sm font-medium text-foreground">来店履歴に取り込みました</p>
              {/* 「取り込めた」ことと「ポイントが付いた」ことは別で、前者だけでも成立する。
                  付与の無い伝票（0 円完了）に大きな「+0 pt」を出すと、成立した申領が失敗に見える */}
              {grantedPoints > 0 ? (
                <>
                  <p className="text-3xl font-bold tracking-wider text-primary-strong">
                    +{grantedPoints.toLocaleString('ja-JP')} pt
                  </p>
                  <p className="text-sm text-muted-foreground">
                    この来店が履歴に並び、ポイントは残高に加わりました。
                  </p>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">
                  この来店が履歴に並びました。この伝票にポイントの付与はありません。
                </p>
              )}
            </div>
          )}
        </CardContent>
      </Card>
      <Button render={<Link href="/member/visits/" />} className="mt-4 w-full">
        来店履歴を見る
      </Button>
      <Button render={<Link href="/member/" />} variant="outline" className="mt-3 w-full">
        ホームへ戻る
      </Button>
    </div>
  );
}
