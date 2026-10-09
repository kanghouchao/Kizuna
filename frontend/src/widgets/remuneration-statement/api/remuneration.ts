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
} from '../model/types';
const guaranteeBase = '/store/remuneration-guarantees';
const bonusBase = '/store/bonus-awards';
export const remunerationApi = {
  async statement(
    scope: StatementScope,
    month: string,
    orderPage: number,
    bonusPage: number
  ): Promise<Statement> {
    const path =
      scope.scope === 'store'
        ? '/store/remuneration-statements'
        : scope.scope === 'platform'
          ? '/platform/remuneration-statements'
          : '/platform/me/remuneration-statements';
    return (
      await apiClient.get<Statement>(path, {
        params: {
          month,
          order_page: orderPage,
          bonus_page: bonusPage,
          size: 20,
          ...('personId' in scope ? { person_id: scope.personId } : {}),
          ...('storeId' in scope ? { store_id: scope.storeId } : {}),
        },
      })
    ).data;
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
  async createGuarantee(personId: number, body: GuaranteeInput) {
    await apiClient.post(guaranteeBase, { ...body, person_id: personId });
  },
  async correctGuarantee(id: string, body: GuaranteeInput & { correction_reason: string }) {
    await apiClient.post(`${guaranteeBase}/${id}/corrections`, body);
  },
  async cancelGuarantee(id: string, body: CancellationInput) {
    await apiClient.post(`${guaranteeBase}/${id}/cancellation`, body);
  },
  async createBonus(personId: number, body: BonusInput) {
    await apiClient.post(bonusBase, { ...body, person_id: personId });
  },
  async correctBonus(
    id: string,
    body: BonusInput & { expected_version: number; correction_reason: string }
  ) {
    await apiClient.post(`${bonusBase}/${id}/corrections`, body);
  },
  async cancelBonus(id: string, body: CancellationInput) {
    await apiClient.post(`${bonusBase}/${id}/cancellation`, body);
  },
  async changes(kind: 'guarantee' | 'bonus', id: string, cursor?: string): Promise<Changes> {
    return (
      await apiClient.get<Changes>(
        `${kind === 'guarantee' ? guaranteeBase : bonusBase}/${id}/changes`,
        { params: { cursor, size: 20 } }
      )
    ).data;
  },
};
