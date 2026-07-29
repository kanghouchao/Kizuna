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
  if (!error || typeof error !== 'object' || !('response' in error)) return false;
  return (error as { response?: { status?: number } }).response?.status === 409;
}
