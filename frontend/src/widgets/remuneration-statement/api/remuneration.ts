import type { AxiosRequestConfig } from 'axios';
import { apiClient, type Page } from '@/shared/api';
import type {
  StatementScope,
  Statement,
  GuaranteeList,
  Bonus,
  Changes,
  GuaranteeInput,
  BonusInput,
  CancellationInput,
  Acceptance,
  AcceptanceInput,
} from '../model/types';
const guaranteeBase = '/remuneration-guarantees';
const bonusBase = '/bonus-awards';
export const remunerationApi = {
  async statement(
    scope: StatementScope,
    month: string,
    orderPage: number,
    bonusPage: number,
    size = 20
  ): Promise<Statement> {
    const path =
      scope.scope === 'store' ? '/remuneration-statements' : '/me/remuneration-statements';
    return (
      await apiClient.get<Statement>(path, {
        params: {
          month,
          order_page: orderPage,
          order_size: size,
          bonus_page: bonusPage,
          bonus_size: size,
          ...('personId' in scope ? { person_id: scope.personId } : {}),
          ...('storeId' in scope ? { store_id: scope.storeId } : {}),
        },
      })
    ).data;
  },
  async accept(body: AcceptanceInput): Promise<Acceptance> {
    return (await apiClient.post<Acceptance>('/me/statement-acceptances', body)).data;
  },
  async guarantees(personId: number, page: number): Promise<GuaranteeList> {
    return (
      await apiClient.get<GuaranteeList>(guaranteeBase, {
        params: { person_id: personId, page, size: 20 },
      })
    ).data;
  },
  async bonuses(personId: number, month: string, page: number): Promise<Page<Bonus>> {
    return (
      await apiClient.get<Page<Bonus>>(bonusBase, {
        params: { person_id: personId, month, page, size: 20 },
      })
    ).data;
  },
  async createGuarantee(personId: number, body: GuaranteeInput, config?: AxiosRequestConfig) {
    await apiClient.post(guaranteeBase, { ...body, person_id: personId }, config);
  },
  async correctGuarantee(
    id: string,
    body: GuaranteeInput & { correction_reason: string },
    config?: AxiosRequestConfig
  ) {
    await apiClient.post(`${guaranteeBase}/${id}/corrections`, body, config);
  },
  async cancelGuarantee(id: string, body: CancellationInput, config?: AxiosRequestConfig) {
    await apiClient.post(`${guaranteeBase}/${id}/cancellation`, body, config);
  },
  async createBonus(personId: number, body: BonusInput, config?: AxiosRequestConfig) {
    await apiClient.post(bonusBase, { ...body, person_id: personId }, config);
  },
  async correctBonus(
    id: string,
    body: BonusInput & { expected_version: number; correction_reason: string },
    config?: AxiosRequestConfig
  ) {
    await apiClient.post(`${bonusBase}/${id}/corrections`, body, config);
  },
  async cancelBonus(id: string, body: CancellationInput, config?: AxiosRequestConfig) {
    await apiClient.post(`${bonusBase}/${id}/cancellation`, body, config);
  },
  async changes(kind: 'guarantee' | 'bonus', id: string, page = 0): Promise<Changes> {
    return (
      await apiClient.get<Changes>(
        `${kind === 'guarantee' ? guaranteeBase : bonusBase}/${id}/changes`,
        { params: { page, size: 20 } }
      )
    ).data;
  },
};
