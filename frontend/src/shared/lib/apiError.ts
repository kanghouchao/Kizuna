// バックエンドのエラーレスポンス（{ error } または { message }）から表示用メッセージを取り出す。
// 各ページ・フォームで同型の抽出ロジックを複製しないこと（漂流の実績あり）。
export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: { error?: string; message?: string } } }).response
      ?.data;
    if (typeof data?.error === 'string') return data.error;
    if (typeof data?.message === 'string') return data.message;
  }
  return fallback;
}

/**
 * 楽観ロック競合（409）か。version を往復する編集フォームが、競合を「保存失敗」一般と
 * 区別して一覧の再取得へ倒すために使う。再取得しないと編集対象が古い version を抱えたままになり、
 * 再試行も閉じ直しも同じ 409 を繰り返す。
 */
export function isConflict(error: unknown): boolean {
  return statusOf(error) === 409;
}

/**
 * 対象が存在しない（404）か。「見つからない」と「取得そのものの失敗」を同じ表示に潰さないために使う。
 * 潰すと、瞬断で消えたように見える画面から利用者が再試行できなくなる。
 */
export function isNotFound(error: unknown): boolean {
  return statusOf(error) === 404;
}

function statusOf(error: unknown): number | undefined {
  if (!error || typeof error !== 'object' || !('response' in error)) return undefined;
  return (error as { response?: { status?: number } }).response?.status;
}
