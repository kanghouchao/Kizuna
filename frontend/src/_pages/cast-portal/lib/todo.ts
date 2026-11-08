import type { OwnServiceConditionSummary } from '@/entities/service';
import type { CastShiftRequestItem } from '@/entities/shift';
import { formatDate } from '@/shared/lib';
import type { StatusWord } from '@/shared/ui';
import type { Day, Statement } from '@/widgets/remuneration-statement';

export interface StoreConditions {
  storeName: string;
  conditions: OwnServiceConditionSummary[];
}

export interface TodoItem {
  key: string;
  title: string;
  storeName: string;
  status: StatusWord;
  href: string;
}

const AWAITING_CONSENT = new Set(['NOT_ACCEPTED', 'RECONFIRMATION_REQUIRED']);

/** 受諾待ちのサービス条件。処理するまで残るのでタブの件数に数える。 */
export function consentTodos(stores: StoreConditions[]): TodoItem[] {
  return stores.flatMap(({ storeName, conditions }) =>
    conditions
      .filter(item => item.consent_status && AWAITING_CONSENT.has(item.consent_status))
      .map(item => ({
        key: `consent-${item.store_id}-${item.id}`,
        title: item.name,
        storeName,
        status: '受諾待ち' as const,
        href: '/cast/service-conditions',
      }))
  );
}

const AWAITING_ACCEPTANCE: Partial<Record<Day['acceptance_state'], StatusWord>> = {
  NOT_ACCEPTED: '承諾待ち',
  CHANGED_AFTER_ACCEPTANCE: '再承諾が必要',
};

/** 明細の承諾を待つ日。処理するまで残るのでタブの件数に数える。 */
export function acceptanceTodos(statements: Statement[], today: string): TodoItem[] {
  return statements.flatMap(statement =>
    statement.days.flatMap(day => {
      const status = AWAITING_ACCEPTANCE[day.acceptance_state];
      if (!status) return [];
      return [
        {
          key: `acceptance-${statement.store_id}-${day.business_date}`,
          title: `${formatDate(day.business_date, today)}の報酬`,
          storeName: statement.store_name,
          status,
          href: '/cast/remunerations/monthly',
        },
      ];
    })
  );
}

const RESULT_DAYS = 7;

/**
 * 申請の結果を出し始める境目。応答に判定日時が無いので、提出から 7 日を結果を出す期間とする。
 * 申請の一覧は新しい順なので、この時刻より古い行が出たら先のページは読まなくてよい。
 */
export function resultSince(now: Date): Date {
  return new Date(now.getTime() - RESULT_DAYS * 24 * 60 * 60 * 1000);
}

const RESULT_WORDS: Partial<Record<NonNullable<CastShiftRequestItem['status']>, StatusWord>> = {
  APPROVED: '承認',
  DECLINED: '却下',
};

/** 出勤希望・変更申請の結果。見るだけで済むのでタブの件数には数えない。 */
export function requestResultTodos(
  requests: CastShiftRequestItem[],
  since: Date,
  today: string
): TodoItem[] {
  return requests.flatMap(request => {
    const status = request.status && RESULT_WORDS[request.status];
    if (!status || !request.work_date || !request.created_at) return [];
    if (new Date(request.created_at) < since) return [];
    const kind = request.type === 'CHANGE' ? '変更申請' : '出勤希望';
    return [
      {
        key: `request-${request.id}`,
        title: `${formatDate(request.work_date, today)}の${kind}`,
        storeName: request.store_name ?? '',
        status,
        href: '/cast/requests',
      },
    ];
  });
}
