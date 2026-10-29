import { useEffect } from 'react';
import {
  advertisingApi,
  type RecruitmentEffectReport,
  type RecruitmentEffectSummary,
  type RecruitmentRetention,
} from '../api/advertising';
import { useListPage } from '@/shared/lib';
import { fromSpringPage } from '@/shared/api';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';

const statusLabels: Record<RecruitmentEffectSummary['calculation_status'], string> = {
  CALCULATED: '記録値から算出',
  NO_COST_RECORDS: '採用広告費が未登録',
  NO_HIRES: '入店人数が0人',
};
const checkpoints = [2, 3, 4] as const;
function yen(amount: number | null) {
  return amount === null ? '未登録' : `${amount.toLocaleString()}円`;
}
function ratio(value: string | null) {
  if (value === null) return '未算出';
  const [integer, fraction] = value.split('.');
  return `約 ${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}円`;
}
function retention(value: RecruitmentRetention | undefined) {
  if (!value || value.judgeable_count === 0) return '判定時点前';
  return `${value.retained_count.toLocaleString()}人 / 可判定 ${value.judgeable_count.toLocaleString()}人`;
}

export function RecruitmentEffectPage({ month }: { month: string }) {
  const list = useListPage<RecruitmentEffectSummary, void, Omit<RecruitmentEffectReport, 'rows'>>(
    async page => {
      const { rows, ...metadata } = await advertisingApi.recruitmentEffects(month, page);
      return { ...fromSpringPage(rows), metadata };
    },
    undefined
  );
  const reload = list.reload;
  useEffect(() => {
    const refresh = () => void reload();
    window.addEventListener('advertising-operation', refresh);
    return () => window.removeEventListener('advertising-operation', refresh);
  }, [reload]);
  const visible = !list.isLoading && !list.failed ? list.metadata : null;
  return (
    <div className="space-y-6">
      <ListPage
        title="採用効果（媒体別の入店・定着）"
        description={`${month} の登録採用広告費と、同じ保存媒体名で当月に入店した人数・入店後の定着人数を比較します。`}
        state={list}
        emptyMessage={
          visible && list.total > 0
            ? 'このページに該当する媒体はありません'
            : 'この月に比較対象の媒体記録はありません'
        }
        errorMessage="採用効果を取得できませんでした。"
        onRetry={() => void list.reload()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-40">媒体</TableHead>
              <TableHead className="text-right">登録採用広告費</TableHead>
              <TableHead className="text-right">入店人数</TableHead>
              <TableHead className="text-right">一人あたり記録費用</TableHead>
              <TableHead>算出状況</TableHead>
              {checkpoints.map(n => (
                <TableHead key={n} className="text-right">
                  {n}か月後の定着
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(row => (
              <TableRow key={row.media_name}>
                <TableCell className="min-w-40 max-w-64 whitespace-normal break-all">
                  <span className="whitespace-pre-wrap">{row.media_name}</span>
                  {row.media_name !== row.media_name.trim() && (
                    <p className="text-sm">前後に空白あり</p>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {yen(row.recorded_recruitment_amount)}
                  <p className="text-sm">費用 {row.cost_entry_count.toLocaleString()}行</p>
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {row.hire_count.toLocaleString()}人
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {ratio(row.cost_per_hire)}
                </TableCell>
                <TableCell>{statusLabels[row.calculation_status]}</TableCell>
                {checkpoints.map(n => (
                  <TableCell key={n} className="text-right tabular-nums">
                    {retention(row.retention.find(r => r.months === n))}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      {visible && (
        <section className="rounded-lg border p-4 space-y-2" aria-label="採用効果の月合計">
          <div className="flex flex-wrap gap-8">
            <p>
              登録採用広告費：<strong>{yen(visible.recorded_recruitment_amount)}</strong>（
              {visible.cost_entry_count.toLocaleString()}行）
            </p>
            <p>
              入店人数：<strong>{visible.hire_count.toLocaleString()}人</strong>
            </p>
          </div>
          <p>
            媒体未入力の入店：
            <strong>{visible.unnamed_media_hire_count.toLocaleString()}人</strong>
            。応募元が媒体でないか媒体名が空の入店で、上記人数の内数です。媒体への割当てと単価算出から除外しています。
          </p>
        </section>
      )}
      <section className="rounded-lg border p-4 space-y-2" aria-label="採用効果の算出条件">
        <p>
          入店人数は、採用確定済みの応募者から登録した在籍のうち、入店日が対象月にあるものです。応募者の氏名・連絡先は表示しません。
        </p>
        <p>
          応募者の応募元媒体名と採用広告費の媒体名を、空白・大小文字・全半角の違いを含めて完全一致だけで比較します。別名を統合せず、媒体未入力を推測で割り当てません。
        </p>
        <p>
          採用広告費が未登録か入店人数が0人なら一人あたり記録費用は未算出です。単価は登録額を人数で割り、小数第3位を四捨五入しています。
        </p>
        <p>
          定着は入店日からNか月後の時点で退店していない在籍です（在籍停止中は在籍として数えます）。その時点が
          {visible ? ` ${visible.retention_as_of} ` : '本日'}
          より後の在籍は判定対象外とし、可判定人数を併記します。
        </p>
        <p>
          記録同士の比較であり、実際の採用単価・広告による因果・ROIを示しません。未登録は実費0の証明ではありません。
        </p>
      </section>
    </div>
  );
}
