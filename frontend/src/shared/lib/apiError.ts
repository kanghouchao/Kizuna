/**
 * 画面が持っているデータでは要求を組めないときの失敗。後端まで行かずにここで止める。
 *
 * <p>応答 DTO の項目はすべて可選なので、識別子を `?? ''` で素通しすると単数の操作が一覧の URI
 * へ飛ぶ。届いた先の 404/405 は「保存に失敗しました」と見分けが付かず、原因が画面側にあることも
 * 消える。文言は {@link getApiErrorMessage} がそのまま出す。
 */
export class ClientDataError extends Error {}

/**
 * URI へ載せる識別子を取り出す。無いまま組ませない。呼ぶのは非同期の try の内側 —
 * 押しどころの側で解くと、投げた失敗がその関数の catch を素通りして外へ出る。
 *
 * <p>取れないのは応答が壊れているか画面が古いときだけなので、復旧は読み直しである。
 */
export function requireId(id: string | undefined, label: string): string {
  if (!id) {
    throw new ClientDataError(`${label}の識別子が取得できていません。画面を読み直してください`);
  }
  return id;
}

// バックエンドのエラーレスポンス（{ error } または { message }）から表示用メッセージを取り出す。
// 各ページ・フォームで同型の抽出ロジックを複製しないこと（漂流の実績あり）。
export function getApiErrorMessage(error: unknown, fallback: string): string {
  // 自分で投げた失敗は文言そのものが宛先。後端の応答を探しに行っても何も無い
  if (error instanceof ClientDataError) return error.message;
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
