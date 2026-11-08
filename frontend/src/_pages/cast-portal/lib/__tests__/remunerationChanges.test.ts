import type { SelfRemunerationItem, SelfRemunerationSnapshot } from '@/entities/order';
import { remunerationChanges } from '../remunerationChanges';

const course: SelfRemunerationItem = {
  kind: 'COURSE',
  name: '60分コース',
  price: 12000,
  remuneration: 7000,
  line_id: 'line-course',
};
const snapshot = (over: Partial<SelfRemunerationSnapshot> = {}): SelfRemunerationSnapshot => ({
  items: [course],
  agreed_remuneration: 7000,
  accrued_remuneration: 7000,
  completion_invalidated: false,
  ...over,
});

describe('remunerationChanges', () => {
  it('無効化は状態と発生済み報酬の差だけを出し、変わらない約定報酬は出さない', () => {
    expect(
      remunerationChanges(
        snapshot(),
        snapshot({ accrued_remuneration: 0, completion_invalidated: true })
      )
    ).toEqual([
      { kind: 'text', label: '状態', text: '有効 → 無効化' },
      { kind: 'changed', label: '発生済み報酬', before: 7000, after: 0 },
    ]);
  });

  it('同じ明細行の料金・固定報酬の差を明細名付きで出す', () => {
    const extended = { ...course, price: 15000, remuneration: 9000 };
    expect(
      remunerationChanges(
        snapshot(),
        snapshot({ items: [extended], agreed_remuneration: 9000, accrued_remuneration: 9000 })
      )
    ).toEqual([
      { kind: 'changed', label: '約定報酬', before: 7000, after: 9000 },
      { kind: 'changed', label: '発生済み報酬', before: 7000, after: 9000 },
      { kind: 'changed', item: '60分コース', label: 'お客様料金', before: 12000, after: 15000 },
      { kind: 'changed', item: '60分コース', label: '固定報酬', before: 7000, after: 9000 },
    ]);
  });

  it('増えた明細と消えた明細を固定報酬の額で出す', () => {
    const extension: SelfRemunerationItem = {
      kind: 'EXTENSION',
      name: '延長30分',
      price: 5000,
      remuneration: 3000,
      line_id: 'line-ext',
    };
    expect(
      remunerationChanges(snapshot({ items: [course, extension] }), snapshot({ items: [course] }))
    ).toEqual([{ kind: 'removed', item: '延長30分', label: '削除', amount: 3000 }]);
    expect(
      remunerationChanges(snapshot({ items: [course] }), snapshot({ items: [course, extension] }))
    ).toEqual([{ kind: 'added', item: '延長30分', label: '追加', amount: 3000 }]);
  });

  it('行 ID の無い明細は種別とサービスで突き合わせる', () => {
    const special = {
      kind: 'SPECIAL_SERVICE' as const,
      name: 'オプション',
      price: 2000,
      remuneration: 1000,
      service_id: 's1',
    };
    expect(
      remunerationChanges(
        snapshot({ items: [special] }),
        snapshot({ items: [{ ...special, remuneration: 1500 }] })
      )
    ).toEqual([
      { kind: 'changed', item: 'オプション', label: '固定報酬', before: 1000, after: 1500 },
    ]);
  });

  it('行 ID の無い同じ種類の明細が二つあっても、順に突き合わせて取り違えない', () => {
    const ext = { kind: 'EXTENSION' as const, name: '延長30分', price: 5000, remuneration: 3000 };
    expect(
      remunerationChanges(
        snapshot({ items: [ext, ext] }),
        snapshot({ items: [ext, { ...ext, remuneration: 3500 }] })
      )
    ).toEqual([
      { kind: 'changed', item: '延長30分', label: '固定報酬', before: 3000, after: 3500 },
    ]);
  });
});
