import type { ComponentType } from 'react';

/** 公開站の固定ページ名（templates/<key>/<page>.tsx 契約）。 */
export type StoreSitePageName = 'page' | 'casts' | 'cast-detail' | 'schedule' | 'menu' | 'about';

// Turbopack は動的セグメントを 2 つ含む import 式をチャンク化できず
// 本番ビルドが panic するため、page 側は静的に分岐し、
// 動的セグメントは templateKey の 1 つに限定する。
function importPage(
  templateKey: string,
  page: StoreSitePageName
): Promise<{ default: ComponentType<Record<string, unknown>> }> {
  switch (page) {
    case 'casts':
      return import(`./${templateKey}/casts`);
    case 'cast-detail':
      return import(`./${templateKey}/cast-detail`);
    case 'schedule':
      return import(`./${templateKey}/schedule`);
    case 'menu':
      return import(`./${templateKey}/menu`);
    case 'about':
      return import(`./${templateKey}/about`);
    default:
      return import(`./${templateKey}/page`);
  }
}

/**
 * templateKey とページ名に対応する模版ページを動的に読み込む共有 helper。
 *
 * 未実装の templateKey（例: 過去に保存された modern / classic）でも公開サイトを
 * 404 にせず default 模版の同名ページへフォールバックする（issue #223）。
 */
export async function loadTemplatePage(
  templateKey: string,
  page: StoreSitePageName = 'page'
): Promise<ComponentType<Record<string, unknown>>> {
  try {
    const { default: TemplateComponent } = await importPage(templateKey, page);
    return TemplateComponent;
  } catch (e) {
    console.error(
      `模版「${templateKey}」のページ「${page}」読み込みに失敗したため default を表示します:`,
      e
    );
    const { default: DefaultPage } = await importPage('default', page);
    return DefaultPage;
  }
}
