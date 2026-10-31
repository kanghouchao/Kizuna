import { apiClient, fromSpringPage, PageResult, Publication } from '@/shared/api';
import { Article, ArticleInput, ArticleSummary } from '../model/types';

const path = '/articles';

export const articleApi = {
  list: async (page: number): Promise<PageResult<ArticleSummary>> =>
    fromSpringPage((await apiClient.get(path, { params: { page, size: 20 } })).data),
  get: async (id: string): Promise<Article> => (await apiClient.get(`${path}/${id}`)).data,
  create: async (input: ArticleInput): Promise<{ id: string }> =>
    (await apiClient.post(path, input)).data,
  update: async (id: string, input: ArticleInput & { version: number }): Promise<Article> =>
    (await apiClient.put(`${path}/${id}`, input)).data,
  updatePublication: async (
    id: string,
    input: Publication & { version: number }
  ): Promise<Article> => (await apiClient.put(`${path}/${id}/publication`, input)).data,
  remove: async (id: string, version: number): Promise<void> => {
    await apiClient.delete(`${path}/${id}`, { params: { version } });
  },
};
