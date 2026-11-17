'use client';
import { useRouter } from 'next/navigation';
import { ArticleInput, articleApi } from '@/entities/content/article';
import { storePath, useWrite } from '@/shared/lib';
import { Card, CardContent } from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import { ArticleAccess } from './ArticleAccess';
import { ArticleForm } from './ArticleForm';
import { useCurrentStoreId } from '@/entities/user';

export function ArticleCreatePage() {
  return (
    <ArticleAccess>
      <ArticleCreate />
    </ArticleAccess>
  );
}

function ArticleCreate() {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const { busy, write } = useWrite();
  const submit = async (input: ArticleInput) => {
    await write(() => articleApi.create(input), {
      success: '記事を作成しました',
      failure: '記事を作成できませんでした',
      onSuccess: ({ id }) => router.push(storePath(storeId, `/articles/${id}/edit`)),
    });
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
