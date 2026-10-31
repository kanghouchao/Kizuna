'use client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useParams, useRouter } from 'next/navigation';
import { Article, ArticleInput, articleApi } from '@/entities/content/article';
import {
  PublicationFields,
  PublicationFormValues,
  fromPublicationFormValues,
  publicationSummary,
  toPublicationFormValues,
} from '@/features/publication-form';
import { notify } from '@/shared/notify';
import { getApiErrorMessage, isConflict, isNotFound, storePath, useResource } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ConfirmDialog,
  Form,
  RegionError,
} from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';
import { ArticleAccess } from './ArticleAccess';
import { ArticleForm } from './ArticleForm';
import { DisplayedBadge } from './DisplayedBadge';

export function ArticleEditPage() {
  return (
    <ArticleAccess>
      <ArticleEdit />
    </ArticleAccess>
  );
}

function ArticleEdit() {
  const id = useParams<{ id: string }>().id;
  const router = useRouter();
  const detail = useResource(() => articleApi.get(id), [id]);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // 本文と公開設定は別々に保存するが版は一つなので、どちらの書き込みも表示中の版を送り、
  // 応答で差し替えて次の書き込みに最新の版を渡す。
  const write = async (
    action: (article: Article) => Promise<Article | null>,
    success: string,
    failure: string
  ) => {
    if (!detail.data || busy) return;
    setBusy(true);
    try {
      const result = await action(detail.data);
      notify.success(success);
      if (result) detail.setData(result);
    } catch (error) {
      notify.error(getApiErrorMessage(error, failure));
      if (isConflict(error)) setConflict(true);
      else if (isNotFound(error)) void detail.reload();
    } finally {
      setBusy(false);
    }
  };

  if (detail.isLoading) return <p className="text-muted-foreground">読み込み中...</p>;
  if (detail.failure !== null || !detail.data)
    return (
      <div className="space-y-6">
        <PageHeader title="記事を編集" />
        {detail.failure === 'notFound' ? (
          <RegionError
            message="記事が見つかりません"
            fallback={{ href: storePath('/articles'), label: '記事一覧へ' }}
          />
        ) : (
          <RegionError message="記事を取得できませんでした" onRetry={() => void detail.reload()} />
        )}
      </div>
    );

  const article = detail.data;
  const locked = busy || conflict;
  return (
    <div className="space-y-6">
      <PageHeader
        title="記事を編集"
        actions={
          <Button variant="destructive" disabled={locked} onClick={() => setDeleting(true)}>
            記事を削除
          </Button>
        }
      />
      {conflict && (
        <div role="alert" className="space-y-3 rounded-lg border p-4">
          <p>記事が更新されています。最新の内容を読み込んでから、改めて操作してください。</p>
          <Button
            variant="outline"
            onClick={() => {
              setConflict(false);
              void detail.reload();
            }}
          >
            最新の内容を読み込む
          </Button>
        </div>
      )}
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            記事
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ArticleForm
            initial={article}
            busy={locked}
            submitLabel="記事を保存"
            onSubmit={(input: ArticleInput) =>
              write(
                current => articleApi.update(id, { ...input, version: current.version }),
                '記事を保存しました',
                '記事を保存できませんでした'
              )
            }
          />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            公開設定
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <DisplayedBadge displayed={article.displayed} />
            <p className="text-sm text-muted-foreground">
              {publicationSummary(article.publication)}
            </p>
          </div>
          <PublicationForm
            article={article}
            busy={locked}
            onSubmit={values =>
              write(
                current =>
                  articleApi.updatePublication(id, {
                    ...fromPublicationFormValues(values),
                    version: current.version,
                  }),
                '公開設定を保存しました',
                '公開設定を保存できませんでした'
              )
            }
          />
        </CardContent>
      </Card>
      <ConfirmDialog
        open={deleting}
        title="記事を削除"
        description={`「${article.title}」を削除します。元に戻すことはできません。`}
        onClose={() => setDeleting(false)}
        onConfirm={() =>
          void write(
            async current => {
              await articleApi.remove(id, current.version);
              router.push(storePath('/articles'));
              return null;
            },
            '記事を削除しました',
            '記事を削除できませんでした'
          )
        }
      />
    </div>
  );
}

function PublicationForm({
  article,
  busy,
  onSubmit,
}: {
  article: Article;
  busy: boolean;
  onSubmit: (values: PublicationFormValues) => Promise<void>;
}) {
  const form = useForm<PublicationFormValues>({
    defaultValues: toPublicationFormValues(article.publication),
  });
  return (
    <Form {...form}>
      <form className="space-y-6" noValidate onSubmit={form.handleSubmit(onSubmit)}>
        <PublicationFields busy={busy} />
        <Button type="submit" disabled={busy}>
          公開設定を保存
        </Button>
      </form>
    </Form>
  );
}
