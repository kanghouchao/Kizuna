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

  it('増えた明細と消えた明細をお客様料金と固定報酬に分けて出す', () => {
    const extension: SelfRemunerationItem = {
      kind: 'EXTENSION',
      name: '延長30分',
      price: 5000,
      remuneration: 3000,
      line_id: 'line-ext',
    };
    expect(
      remunerationChanges(snapshot({ items: [course, extension] }), snapshot({ items: [course] }))
    ).toEqual([
      { kind: 'removed', item: '延長30分', label: 'お客様料金', amount: 5000 },
      { kind: 'removed', item: '延長30分', label: '固定報酬', amount: 3000 },
    ]);
    expect(
      remunerationChanges(snapshot({ items: [course] }), snapshot({ items: [course, extension] }))
    ).toEqual([
      { kind: 'added', item: '延長30分', label: 'お客様料金', amount: 5000 },
      { kind: 'added', item: '延長30分', label: '固定報酬', amount: 3000 },
    ]);
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

describe('remunerationChanges の金額以外の約定条件', () => {
  const v1: SelfRemunerationItem = {
    kind: 'COURSE',
    name: '90分コース',
    price: 12000,
    remuneration: 7000,
    service_id: 'svc',
    duration_minutes: 90,
    revision_number: 1,
    adoption_basis: 'CURRENT_SETTING',
  };
  const base = snapshot({ items: [v1] });

  it('同じサービスの別の版へ訂正され、金額が同じでも名称・時間・条件・版の差を出す', () => {
    const v2 = {
      ...v1,
      name: '新90分コース',
      duration_minutes: 100,
      revision_number: 2,
      adoption_basis: 'HISTORICAL_CORRECTION' as const,
    };
    expect(remunerationChanges(base, snapshot({ items: [v2] }))).toEqual([
      { kind: 'text', item: '新90分コース', label: '名称', text: '90分コース → 新90分コース' },
      { kind: 'text', item: '新90分コース', label: '時間', text: '90分 → 100分' },
      {
        kind: 'text',
        item: '新90分コース',
        label: '条件',
        text: '店舗の設定 → 過去の版による訂正',
      },
      { kind: 'text', item: '新90分コース', label: '版', text: '第1版 → 第2版' },
    ]);
  });

  it('受諾した条件の版が変われば条件の差に版を添える', () => {
    const accepted = { ...v1, adoption_basis: 'ACCEPTED_TERMS' as const, consent_version: 1 };
    expect(
      remunerationChanges(
        snapshot({ items: [accepted] }),
        snapshot({ items: [{ ...accepted, consent_version: 2 }] })
      )
    ).toEqual([
      {
        kind: 'text',
        item: '90分コース',
        label: '条件',
        text: '受諾した条件（第1版） → 受諾した条件（第2版）',
      },
    ]);
  });
});
