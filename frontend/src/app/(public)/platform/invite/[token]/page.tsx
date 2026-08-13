import { redirect } from 'next/navigation';

/**
 * 配布済み招待リンク（トークンをパスに載せた旧形式）の受け口。
 *
 * 招待は発行から 72 時間有効なので、形式を変えた時点で配布済みのリンクが客先に残っている。
 * この受け口が無いとその全てが即 404 になるため、フラグメント形式へ送り直す。旧形式のリンクを
 * 配った最後の時点から招待の有効期間が過ぎれば到達しうるリンクは残らないので、その後は削除してよい。
 *
 * 送り先はマウント時にフラグメントを読んで要求の本文へ載せ替えるため、旧形式で届いた招待でも
 * トークンをパスに載せた API 要求は出ない。転送はサーバ側で行う — フラグメントはリダイレクト先
 * URL の一部としてブラウザが保持し、次の要求では送られない。
 */
export default async function CastInviteLegacyRoute({
  params,
}: {
  params: Promise<{ token: string }>;
}): Promise<never> {
  const { token } = await params;
  // ルート引数は Next が復号済みなので、フラグメントへ載せる際に符号化し直す。
  redirect(`/platform/invite#${encodeURIComponent(token)}`);
}
