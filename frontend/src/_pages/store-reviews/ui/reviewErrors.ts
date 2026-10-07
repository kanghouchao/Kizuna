export function accessError(error: unknown): string | null {
  if (!error || typeof error !== 'object' || !('response' in error)) return null;
  const status = (error as { response?: { status?: number } }).response?.status;
  if (status === 401) return 'ログイン状態を確認してください';
  if (status === 403) return '現在の操作権限がありません';
  return null;
}
