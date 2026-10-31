'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArticleInput, articleApi } from '@/entities/content/article';
import { notify } from '@/shared/notify';
import { getApiErrorMessage, storePath } from '@/shared/lib';
import { Card, CardContent } from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import { ArticleAccess } from './ArticleAccess';
import { ArticleForm } from './ArticleForm';

export function ArticleCreatePage() {
  return (
    <ArticleAccess>
      <ArticleCreate />
    </ArticleAccess>
  );
}

function ArticleCreate() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const submit = async (input: ArticleInput) => {
    setBusy(true);
    try {
      const { id } = await articleApi.create(input);
      notify.success('記事を作成しました');
      router.push(storePath(`/articles/${id}/edit`));
    } catch (error) {
      notify.error(getApiErrorMessage(error, '記事を作成できませんでした'));
      setBusy(false);
    }
  };
  return (
    <div className="space-y-6">
      <PageHeader
        title="記事を作成"
        description="記事は非公開で作成されます。公開設定は作成後の編集画面で変更できます。"
      />
      <Card>
        <CardContent>
          <ArticleForm busy={busy} submitLabel="記事を作成" onSubmit={submit} />
        </CardContent>
      </Card>
    </div>
  );
}
