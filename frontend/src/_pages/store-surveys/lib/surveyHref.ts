import type { WriteResponse } from '@/entities/survey';
import { storePath } from '@/shared/lib';

export const surveyListHref = (storeId: string) => storePath(storeId, '/surveys');

export const surveyCreateHref = (storeId: string) => storePath(storeId, '/surveys/create');

export const revisionHref = (storeId: string, sid: string, rid: string) =>
  storePath(storeId, `/surveys/${encodeURIComponent(sid)}/revisions/${encodeURIComponent(rid)}`);

export const revisionEditHref = (storeId: string, sid: string, rid: string) =>
  storePath(
    storeId,
    `/surveys/${encodeURIComponent(sid)}/revisions/${encodeURIComponent(rid)}/edit`
  );

export const reviseHref = (storeId: string, sid: string, from: string) =>
  storePath(
    storeId,
    `/surveys/${encodeURIComponent(sid)}/revisions/new?from=${encodeURIComponent(from)}`
  );

export const receiveHref = (storeId: string, sid: string, rid: string) =>
  storePath(
    storeId,
    `/surveys/${encodeURIComponent(sid)}/revisions/${encodeURIComponent(rid)}/responses/new`
  );

export const answerHref = (storeId: string, aid: string) =>
  storePath(storeId, `/surveys/responses/${encodeURIComponent(aid)}`);

export const correctHref = (storeId: string, aid: string) =>
  storePath(storeId, `/surveys/responses/${encodeURIComponent(aid)}/correct`);

/** 書き込みの結果の版・回答の画面。 */
export const writtenHref = (storeId: string, result: WriteResponse) =>
  'answer' in result
    ? answerHref(storeId, result.answer.id)
    : revisionHref(storeId, result.revision.survey_id, result.revision.id);
