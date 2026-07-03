// バックエンドのエラーレスポンス（{ error } または { message }）から表示用メッセージを取り出す。
// 各ページ・フォームで同型の抽出ロジックを複製しないこと（漂流の実績あり）。
export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: { error?: string; message?: string } } }).response
      ?.data;
    if (data?.error) return data.error;
    if (data?.message) return data.message;
  }
  return fallback;
}
