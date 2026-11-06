import type { WriteResponse } from '@/entities/survey';
import { storePath } from '@/shared/lib';

export const surveyListHref = storePath('/surveys');

export const surveyCreateHref = storePath('/surveys/create');

export const revisionHref = (sid: string, rid: string) =>
  storePath(`/surveys/${encodeURIComponent(sid)}/revisions/${encodeURIComponent(rid)}`);

export const revisionEditHref = (sid: string, rid: string) =>
  storePath(`/surveys/${encodeURIComponent(sid)}/revisions/${encodeURIComponent(rid)}/edit`);

export const reviseHref = (sid: string, from: string) =>
  storePath(`/surveys/${encodeURIComponent(sid)}/revisions/new?from=${encodeURIComponent(from)}`);

export const receiveHref = (sid: string, rid: string) =>
  storePath(
    `/surveys/${encodeURIComponent(sid)}/revisions/${encodeURIComponent(rid)}/responses/new`
  );

export const answerHref = (aid: string) =>
  storePath(`/surveys/responses/${encodeURIComponent(aid)}`);

export const correctHref = (aid: string) =>
  storePath(`/surveys/responses/${encodeURIComponent(aid)}/correct`);

/** 書き込みの結果の版・回答の画面。 */
export const writtenHref = (result: WriteResponse) =>
  'answer' in result
    ? answerHref(result.answer.id)
    : revisionHref(result.revision.survey_id, result.revision.id);
