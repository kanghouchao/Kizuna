import { useId, type ReactNode } from 'react';
import {
  isDeduction,
  ORDER_FEE_LINE_KIND_LABELS,
  OrderFeeLine,
  OrderPreview,
  OrderSpecialService,
} from '@/entities/order';
import { cn } from '@/shared/lib/utils';
import {
  Badge,
  ChangeTable,
  type ChangeRow,
  KeyValueList,
  KeyValueRow,
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

const yen = (value: number) => `¥${value.toLocaleString()}`;
const points = (value: number) => `${value.toLocaleString()}pt`;

type Tone = 'success' | 'warning' | 'destructive' | 'neutral';
const TONE_CLASS: Record<Tone, string> = {
  success: 'bg-success/10 text-success-strong',
  warning: 'bg-warning/10 text-warning-strong',
  destructive: 'bg-destructive/10 text-destructive-strong',
  neutral: 'bg-muted text-foreground',
};

function Status({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <Badge variant="outline" className={cn('border-transparent', TONE_CLASS[tone])}>
      {children}
    </Badge>
  );
}

function consentStatus(item: OrderSpecialService): { label: string; tone: Tone } {
  if (item.requires_attention) return { label: '本人拒否・要対応', tone: 'destructive' };
  switch (item.current_consent_status) {
    case 'ACCEPTED':
      return { label: '受諾済み', tone: 'success' };
    case 'REJECTED':
      return { label: '拒否', tone: 'destructive' };
    case 'RECONFIRMATION_REQUIRED':
      return { label: '再受諾待ち（旧約定を保持）', tone: 'warning' };
    case 'NOT_ACCEPTED':
      return { label: '現在の提供資格なし（旧約定を保持）', tone: 'warning' };
  }
  return item.adoption_basis === 'HISTORICAL_CORRECTION'
    ? { label: '履歴訂正', tone: 'neutral' }
    : { label: '受諾時の約定', tone: 'neutral' };
}

// 差分を出す条件の比較値。表示の状態ラベルとは区分けが違うため、ラベルから組み立てない。
function specialServiceComparison(item: OrderSpecialService) {
  const status = item.requires_attention
    ? '本人拒否・要対応'
    : item.current_consent_status === 'ACCEPTED'
      ? '受諾済み'
      : item.current_consent_status === 'REJECTED'
        ? '拒否'
        : item.current_consent_status === 'RECONFIRMATION_REQUIRED'
          ? '再受諾待ち'
          : item.adoption_basis === 'HISTORICAL_CORRECTION'
            ? '履歴訂正'
            : item.current_consent_status == null && item.adoption_basis === 'ACCEPTED_TERMS'
              ? '受諾時の約定'
              : '未受諾';
  return `${item.name} 版${item.revision_number} 料金¥${item.price} 報酬¥${item.remuneration} 担当在籍 ${item.enrollment_id} / 受諾状態 ${status} / 受諾版 ${item.consent_version ?? 'なし'}`;
}

function previewChanged(before: OrderPreview, after: OrderPreview) {
  return (
    before.confirmation_token !== after.confirmation_token ||
    JSON.stringify(before.special_services.map(specialServiceComparison)) !==
      JSON.stringify(after.special_services.map(specialServiceComparison))
  );
}

const CONTACT_LABELS = { PHONE: '電話', EMAIL: 'メール', LINE: 'LINE' } as const;
const MARKETING_RESULTS = {
  ALLOWED: '販促許可を記録（同値行の共同制約を適用）',
  DENIED_PRESERVED: '既存の販促拒否を維持',
  NOT_REQUESTED: '販促状態を変更しない',
} as const;

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="space-y-2">
      <h3 id={id} className="text-sm font-semibold">
        {title}
      </h3>
      {children}
    </section>
  );
}

interface FeeRow {
  key: string;
  name: string;
  minutes?: number;
  revision?: number;
  amount: string;
  remuneration: number;
  deduction: boolean;
}

// 明細の BASE_COURSE 行はコースの写しなので、コースは preview.course から 1 行だけ出す。
const extraLines = (preview: OrderPreview) =>
  preview.fee_lines.filter(line => line.kind !== 'BASE_COURSE');
const lineName = (line: OrderFeeLine) => line.name ?? ORDER_FEE_LINE_KIND_LABELS[line.kind];
const lineAmount = (line: OrderFeeLine) =>
  `${isDeduction(line.kind) ? '-' : ''}${yen(line.amount)}`;

function feeRows(preview: OrderPreview): FeeRow[] {
  const { course } = preview;
  return [
    {
      key: 'course',
      name: course.name,
      minutes: course.duration_minutes,
      revision: course.revision_number,
      amount: yen(course.price),
      remuneration: course.remuneration,
      deduction: false,
    },
    ...extraLines(preview).map((line, i) => ({
      key: `line:${i}`,
      name: lineName(line),
      minutes: line.duration_minutes,
      revision: line.revision_number,
      amount: lineAmount(line),
      remuneration: line.remuneration,
      deduction: isDeduction(line.kind),
    })),
  ];
}

function FeeTable({ preview }: { preview: OrderPreview }) {
  const number = 'text-right tabular-nums';
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>項目</TableHead>
          <TableHead className="text-right">時間</TableHead>
          <TableHead>版</TableHead>
          <TableHead className="text-right">料金</TableHead>
          <TableHead className="text-right">固定報酬</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {feeRows(preview).map(row => (
          <TableRow key={row.key}>
            <TableHead scope="row" className="font-normal whitespace-normal wrap-anywhere">
              {row.name}
            </TableHead>
            <TableCell className={number}>
              {row.minutes === undefined ? '—' : `${row.minutes}分`}
            </TableCell>
            <TableCell>{row.revision === undefined ? '—' : `版${row.revision}`}</TableCell>
            <TableCell className={cn(number, row.deduction && 'text-destructive-strong')}>
              {row.amount}
            </TableCell>
            <TableCell className={number}>{yen(row.remuneration)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
      <TableFooter>
        <TableRow>
          <TableHead scope="row">合計（請求額）</TableHead>
          <TableCell className={number}>{preview.total_duration_minutes}分</TableCell>
          <TableCell />
          <TableCell className={cn(number, 'font-semibold')}>{yen(preview.total_fee)}</TableCell>
          <TableCell className={number}>{yen(preview.total_remuneration)}</TableCell>
        </TableRow>
      </TableFooter>
    </Table>
  );
}

function feeLineText(line: OrderFeeLine | undefined) {
  if (!line) return '—';
  return [
    lineName(line),
    line.duration_minutes !== undefined && `${line.duration_minutes}分`,
    line.revision_number !== undefined && `版${line.revision_number}`,
    `料金 ${lineAmount(line)}`,
    `固定報酬 ${yen(line.remuneration)}`,
  ]
    .filter(Boolean)
    .join(' / ');
}

function specialServiceText(item: OrderSpecialService | undefined) {
  if (!item) return '—';
  return [
    `版${item.revision_number} / 料金 ${yen(item.price)} / 固定報酬 ${yen(item.remuneration)} / 担当在籍 ${item.enrollment_id}`,
    `受諾状態 ${consentStatus(item).label} / 受諾版 ${item.consent_version ?? 'なし'}`,
  ].join('\n');
}

const memberText = (value: NonNullable<OrderPreview['points']>) =>
  value.member_linked ? (value.member_code ?? '—') : '会員紐づけなし';

function changeRows(before: OrderPreview, after: OrderPreview): ChangeRow[] {
  const plain = (label: string, read: (value: OrderPreview) => string) => ({
    label,
    before: read(before),
    after: read(after),
  });
  const was = extraLines(before);
  const now = extraLines(after);
  const services = new Map<string, string>();
  for (const item of [...before.special_services, ...after.special_services])
    services.set(item.service_id, item.name);
  return [
    plain('コース', v => `${v.course.name} / 版${v.course.revision_number}`),
    plain('コース時間', v => `${v.course.duration_minutes}分`),
    plain('コース料金', v => yen(v.course.price)),
    plain('コースの固定報酬', v => yen(v.course.remuneration)),
    ...Array.from({ length: Math.max(was.length, now.length) }, (_, i) => ({
      key: `line:${i}`,
      label: `明細${i + 1}`,
      before: feeLineText(was[i]),
      after: feeLineText(now[i]),
    })),
    ...[...services].map(([id, name]) => ({
      key: `special:${id}`,
      label: name,
      before: specialServiceText(before.special_services.find(item => item.service_id === id)),
      after: specialServiceText(after.special_services.find(item => item.service_id === id)),
    })),
    plain('総時間', v => `${v.total_duration_minutes}分`),
    plain('固定報酬合計', v => yen(v.total_remuneration)),
    plain('請求額', v => yen(v.total_fee)),
    plain('利用上限・通常付与基準', v => yen(v.point_basis_amount)),
    plain('会員', v => (v.points ? memberText(v.points) : '—')),
    plain('利用資格', v =>
      v.points ? (v.points.redemption_eligible ? '利用可能' : '利用不可') : '—'
    ),
    plain('利用', v => (v.points ? points(v.points.use_points) : '—')),
    plain('付与予定', v => (v.points ? points(v.points.grant_points) : '—')),
  ];
}

/** 採用条件の確認の本文。前回の確認と差があるときだけ対照表を添える。 */
export function OrderPreviewDetails({
  preview,
  previous,
}: {
  preview: OrderPreview;
  previous: OrderPreview | null;
}) {
  const specials = preview.special_services;
  return (
    <div className="space-y-5">
      {preview.requires_attention && (
        <p
          role="alert"
          className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive-strong"
        >
          拒否項目が残っています。保存後も開始・完了には修復が必要です。
        </p>
      )}
      <Section title="料金明細">
        <FeeTable preview={preview} />
      </Section>
      {specials.length > 0 && (
        <Section title="特殊サービス">
          <ul className="divide-y text-sm">
            {specials.map(item => {
              const status = consentStatus(item);
              return (
                <li key={item.service_id} className="flex items-center justify-between gap-3 py-2">
                  <span className="min-w-0 wrap-anywhere">
                    {item.name} 版{item.revision_number}
                  </span>
                  <Status tone={status.tone}>{status.label}</Status>
                </li>
              );
            })}
          </ul>
        </Section>
      )}
      <Section title="ポイント">
        <KeyValueList>
          {preview.points && (
            <>
              <KeyValueRow label="会員">{memberText(preview.points)}</KeyValueRow>
              <KeyValueRow label="利用資格">
                {preview.points.redemption_eligible ? (
                  <Status tone="success">利用可能</Status>
                ) : (
                  <Status tone="neutral">利用不可</Status>
                )}
              </KeyValueRow>
              <KeyValueRow label="現在残高" money>
                {preview.points.point_balance === undefined
                  ? '—'
                  : points(preview.points.point_balance)}
              </KeyValueRow>
              <KeyValueRow label="利用" money>
                {points(preview.points.use_points)}
              </KeyValueRow>
              <KeyValueRow label="付与予定" money>
                {points(preview.points.grant_points)}
              </KeyValueRow>
              <KeyValueRow label="利用単位" money>
                {points(preview.points.usage_unit)}
              </KeyValueRow>
            </>
          )}
          <KeyValueRow label="利用上限・通常付与基準" money>
            {yen(preview.point_basis_amount)}
          </KeyValueRow>
        </KeyValueList>
      </Section>
      {preview.contact_imports && (
        <Section title="連絡先の取り込み">
          {preview.contact_imports.length === 0 ? (
            <p className="text-sm">台帳へ取り込まない</p>
          ) : (
            <KeyValueList>
              {preview.contact_imports.map(item => (
                <KeyValueRow key={item.type} label={CONTACT_LABELS[item.type]} long>
                  {`${item.value}\n${MARKETING_RESULTS[item.marketing_result]}`}
                </KeyValueRow>
              ))}
            </KeyValueList>
          )}
        </Section>
      )}
      {previous && previewChanged(previous, preview) && (
        <Section title="前回の確認からの変更">
          <p role="status" className="text-sm">
            前回の確認内容から変更があります。変わった行を確認してください。
          </p>
          <ChangeTable rows={changeRows(previous, preview)} />
        </Section>
      )}
    </div>
  );
}
