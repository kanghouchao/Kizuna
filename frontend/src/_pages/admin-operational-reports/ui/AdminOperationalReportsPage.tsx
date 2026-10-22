'use client';
import Link from 'next/link';
import { storePath } from '@/shared/lib';
import { Button, Form } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { useOperationalReportPage, ReportSearch, ReportTable } from '@/widgets/operational-report';
export default function AdminOperationalReportsPage() {
  return <ReportPage />;
}
function ReportPage() {
  const model = useOperationalReportPage('admin');
  const { form, access, query, report, result, paging, submit, setQuery } = model;
  if (!access.ready) return <p>読み込み中...</p>;
  if (!access.view) return <p role="alert">運営金額集計を閲覧する権限がありません。</p>;
  // 集合全体を読める者は店舗級の集計も必ず読める（店舗級の端点が ORDER_SET_VIEW の族を受け入れる）ので、
  // 入口の可視条件はこの画面の閲覧条件と同じでよい。
  return (
    <Form {...form}>
      <ListPage
        title="運営金額集計"
        description="原営業日の完了受注から、現在の有効請求額と発生済み固定報酬を集計します。"
        actions={
          <Button variant="outline" render={<Link href={storePath('/operational-reports')} />}>
            現在店舗の集計
          </Button>
        }
        search={{ onSearch: () => void submit(), content: <ReportSearch model={model} /> }}
        state={{
          ...paging,
          isLoading: report.isLoading,
          failed: report.failure !== null,
          onPageChange: result
            ? page => setQuery(current => current && { ...current, page })
            : undefined,
        }}
        emptyMessage={query ? '対象期間の集計対象はありません' : '期間と集計単位を指定してください'}
        errorMessage="集計を取得できませんでした。権限と条件を確認し、再試行してください。"
        onRetry={() => void report.reload()}
      >
        <ReportTable model={model} />
      </ListPage>
    </Form>
  );
}
