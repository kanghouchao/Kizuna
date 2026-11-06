import { storePath } from '@/shared/lib';

export const surveyListHref = storePath('/surveys');

export const revisionHref = (sid: string, rid: string) =>
  storePath(`/surveys/${encodeURIComponent(sid)}/revisions/${encodeURIComponent(rid)}`);

export const answerHref = (aid: string) =>
  storePath(`/surveys/responses/${encodeURIComponent(aid)}`);
