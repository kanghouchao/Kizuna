import type { Publication } from '@/shared/api';

export const ARTICLE_TITLE_MAX = 100;
export const ARTICLE_BODY_MAX = 10000;

export interface ArticleSummary {
  id: string;
  title: string;
  cover_image_url?: string;
  publication: Publication;
  displayed: boolean;
  updated_at: string;
  version: number;
}

export interface Article extends ArticleSummary {
  body: string;
}

export interface ArticleInput {
  title: string;
  body: string;
  /** 表紙を付けないときは null。 */
  cover_image_url: string | null;
}
