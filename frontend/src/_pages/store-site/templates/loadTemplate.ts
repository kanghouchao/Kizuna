import type { ComponentType } from 'react';
import type { StoreSitePageName } from '../model/types';

type TemplatePage = ComponentType<Record<string, unknown>>;

// Turbopack は動的セグメントを 2 つ含む import 式をチャンク化できず
// 本番ビルドが panic するため、page 側は静的に分岐し、
// 動的セグメントは templateKey の 1 つに限定する。
function importPage(
  templateKey: string,
  page: StoreSitePageName
): Promise<{ default: TemplatePage }> {
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
    case 'reservation':
      return import(`./${templateKey}/reservation`);
    case 'reviews':
      return import(`./${templateKey}/reviews`);
    case 'news':
      return import(`./${templateKey}/news`);
    case 'news-detail':
      return import(`./${templateKey}/news-detail`);
    default:
      return import(`./${templateKey}/page`);
  }
}

const FALLBACK_TEMPLATE_KEY = 'default';

/**
 * templateKey とページ名に対応するテンプレートのページを読み込み、実際に描くテンプレートの標識と返す。
 * 未知の templateKey でも公開サイトを 404 にせず、default の同名ページへ落とす。
 */
export async function loadTemplatePage(
  templateKey: string,
  page: StoreSitePageName = 'page'
): Promise<{ templateKey: string; Page: TemplatePage }> {
  try {
    const { default: Page } = await importPage(templateKey, page);
    return { templateKey, Page };
  } catch (e) {
    console.error(
      `テンプレート「${templateKey}」のページ「${page}」の読み込みに失敗したため default を表示します:`,
      e
    );
    const { default: Page } = await importPage(FALLBACK_TEMPLATE_KEY, page);
    return { templateKey: FALLBACK_TEMPLATE_KEY, Page };
  }
}
