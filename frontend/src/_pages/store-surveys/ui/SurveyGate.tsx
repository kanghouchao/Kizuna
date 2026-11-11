import { Fragment, ReactNode } from 'react';
import { MePending, hasPermission, useMe, useStoreContext } from '@/entities/user';
import { DeniedPage } from '@/widgets/admin-shell';

export interface SurveyCapabilities {
  canManage: boolean;
  canRecord: boolean;
}

/** 閲覧権限で入口を閉じ、店舗や対象が変われば画面の状態を作り直す。 */
export function SurveyGate({
  target,
  manage = false,
  record = false,
  children,
}: {
  target: string;
  /** 定義を編集する画面。管理権限が無ければ入口を閉じる。 */
  manage?: boolean;
  /** 回答を受付・訂正する画面。記録権限が無ければ入口を閉じる。 */
  record?: boolean;
  children: (capabilities: SurveyCapabilities) => ReactNode;
}) {
  const { me } = useMe();
  const storeId = useStoreContext().currentStoreId;
  if (me === null) return <MePending />;
  if (me.user_type !== 'STAFF' || !hasPermission(me, 'SURVEY_VIEW')) return <DeniedPage />;
  if (manage && !hasPermission(me, 'SURVEY_MANAGE')) return <DeniedPage />;
  if (record && !hasPermission(me, 'SURVEY_RECORD')) return <DeniedPage />;
  return (
    <Fragment key={`${storeId}:${target}`}>
      {children({
        canManage: hasPermission(me, 'SURVEY_MANAGE'),
        canRecord: hasPermission(me, 'SURVEY_RECORD'),
      })}
    </Fragment>
  );
}

export function SurveyDenied({ message }: { message: string }) {
  return <div role="alert">{message}。再ログインまたは権限を確認してください。</div>;
}
