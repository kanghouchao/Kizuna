import {
  apiClient,
  fromSpringPage,
  fromCursorPage,
  PageResult,
  CursorPageResult,
} from '@/shared/api';
import {
  Survey,
  SurveySummary,
  Revision,
  RevisionSummary,
  Answer,
  AnswerSummary,
  Counts,
  SurveyHistory,
  SurveySearch,
  AnswerSearch,
  SurveyCommand,
  WriteResponse,
} from '../model/types';
const surveys = '/store/surveys';
const responses = '/store/survey-responses';
const revisionPath = (sid: string, rid: string) => `${surveys}/${sid}/revisions/${rid}`;
export const surveyApi = {
  list: async (page: number, search: SurveySearch): Promise<PageResult<SurveySummary>> =>
    fromSpringPage((await apiClient.get(surveys, { params: { page, size: 20, ...search } })).data),
  survey: async (id: string): Promise<Survey> => (await apiClient.get(`${surveys}/${id}`)).data,
  revisions: async (sid: string, page: number): Promise<PageResult<RevisionSummary>> =>
    fromSpringPage(
      (await apiClient.get(`${surveys}/${sid}/revisions`, { params: { page, size: 20 } })).data
    ),
  revision: async (sid: string, rid: string): Promise<Revision> =>
    (await apiClient.get(revisionPath(sid, rid))).data,
  answers: async (
    sid: string,
    rid: string,
    page: number,
    search: AnswerSearch
  ): Promise<PageResult<AnswerSummary>> =>
    fromSpringPage(
      (
        await apiClient.get(`${revisionPath(sid, rid)}/responses`, {
          params: { page, size: 20, ...search },
        })
      ).data
    ),
  answer: async (aid: string): Promise<Answer> => (await apiClient.get(`${responses}/${aid}`)).data,
  counts: async (sid: string, rid: string): Promise<Counts> =>
    (await apiClient.get(`${revisionPath(sid, rid)}/response-counts`)).data,
  history: async (
    target: { sid: string; rid: string } | { aid: string },
    cursor?: string
  ): Promise<CursorPageResult<SurveyHistory>> =>
    fromCursorPage(
      (
        await apiClient.get(
          `${'aid' in target ? `${responses}/${target.aid}` : revisionPath(target.sid, target.rid)}/history`,
          { params: { size: 20, cursor } }
        )
      ).data
    ),
  write: async (command: SurveyCommand): Promise<WriteResponse> => {
    if (command.kind === 'REPLACE')
      return (await apiClient.put(revisionPath(command.sid, command.rid), command.input)).data;
    let path: string;
    switch (command.kind) {
      case 'CREATE':
        path = surveys;
        break;
      case 'REVISE':
        path = `${surveys}/${command.sid}/revisions`;
        break;
      case 'OPEN':
        path = `${revisionPath(command.sid, command.rid)}/openings`;
        break;
      case 'CLOSE':
        path = `${revisionPath(command.sid, command.rid)}/closures`;
        break;
      case 'RECEIVE':
        path = `${revisionPath(command.sid, command.rid)}/responses`;
        break;
      case 'CORRECT':
        path = `${responses}/${command.aid}/corrections`;
        break;
      case 'WITHDRAW':
        path = `${responses}/${command.aid}/withdrawals`;
        break;
    }
    return (await apiClient.post(path, command.input)).data;
  },
};
