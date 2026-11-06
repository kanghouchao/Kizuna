'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { SurveySearch, surveyApi } from '@/entities/survey';
import { useListPage } from '@/shared/lib';
import {
  Button,
  Input,
  Label,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { revisionHref, surveyCreateHref, writtenHref } from '../lib/surveyHref';
import { useSurveyWrite } from '../lib/useSurveyWrite';
import { SurveyCapabilities, SurveyDenied, SurveyGate } from './SurveyGate';
import { Choice, revisionLabels, useGuardedRead } from './surveyUi';
import { SurveyWriteFeedback } from './SurveyWriteFeedback';

export function SurveysPage() {
  return <SurveyGate target="catalogue">{caps => <Catalogue {...caps} />}</SurveyGate>;
}

function Catalogue({ canManage }: SurveyCapabilities) {
  const router = useRouter();
  const write = useSurveyWrite(result => router.push(writtenHref(result)));
  const guard = useGuardedRead(write.deny);
  const [search, setSearch] = useState<SurveySearch>({});
  const list = useListPage(
    (page, criteria: SurveySearch) => guard(() => surveyApi.list(page, criteria)),
    {}
  );
  if (write.denied) return <SurveyDenied message={write.denied} />;
  return (
    <div className="space-y-6">
      <SurveyWriteFeedback write={write} busy={write.busy} onRefresh={write.settle} />
      <ListPage
        title="アンケート管理"
        description="設問を版管理し、スタッフが既に受け取った回答を記録します。保持期限は未設定です。"
        actions={
          canManage && <Button render={<Link href={surveyCreateHref} />}>アンケートを作成</Button>
        }
        state={list}
        search={{
          content: (
            <>
              <div className="space-y-2">
                <Label htmlFor="survey-search">最新版の題名</Label>
                <Input
                  id="survey-search"
                  value={search.q ?? ''}
                  maxLength={120}
                  onChange={e => setSearch({ ...search, q: e.target.value })}
                />
              </div>
              <Choice
                id="survey-status"
                label="最新版の状態"
                value={search.latest_status ?? ''}
                optional
                items={revisionLabels}
                onChange={v =>
                  setSearch({
                    ...search,
                    latest_status: (v as SurveySearch['latest_status']) || undefined,
                  })
                }
              />
              <Choice
                id="survey-sort"
                label="並び順"
                value={search.sort ?? 'CREATED_DESC'}
                items={{ CREATED_DESC: '作成が新しい順', CREATED_ASC: '作成が古い順' }}
                onChange={v => setSearch({ ...search, sort: v as SurveySearch['sort'] })}
              />
              <Button type="submit">検索</Button>
            </>
          ),
          onSearch: () => void list.search({ ...search, q: search.q?.trim() || undefined }),
        }}
        emptyMessage="アンケートはありません"
        errorMessage="アンケート一覧を取得できませんでした"
        onRetry={() => void list.reload()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>最新版の題名</TableHead>
              <TableHead>版</TableHead>
              <TableHead>状態</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(row => (
              <TableRow key={row.id}>
                <TableCell className="max-w-80 break-words">{row.latest_title}</TableCell>
                <TableCell>{row.latest_revision_number}</TableCell>
                <TableCell>
                  {revisionLabels[row.latest_status]}
                  {row.open_revision_id &&
                    row.open_revision_id !== row.latest_revision_id &&
                    '（旧版を受付中）'}
                </TableCell>
                <TableCell>
                  <Button
                    variant="outline"
                    render={<Link href={revisionHref(row.id, row.latest_revision_id)} />}
                  >
                    版と回答
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
    </div>
  );
}
