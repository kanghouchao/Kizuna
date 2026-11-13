'use client';

// PROTOTYPE（#1356）: variant とデータの状態を切り替える浮動バー。評価対象のデザインの一部ではない。
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useCallback, useEffect } from 'react';
import {
  PROTO_DATA_MODES,
  PROTO_VARIANTS,
  PROTO_VARIANT_NAMES,
  type ProtoDataMode,
  type ProtoVariant,
} from './contract';

const ORDER = ['current', ...PROTO_VARIANTS] as const;
type Slot = (typeof ORDER)[number];

const DATA_LABEL: Record<ProtoDataMode, string> = {
  real: '実データ',
  bare: '未設定だらけ',
  many: 'キャスト40人',
};

export default function PrototypeSwitcher() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const raw = params.get('variant');
  const current: Slot = (PROTO_VARIANTS as readonly string[]).includes(raw ?? '')
    ? (raw as ProtoVariant)
    : 'current';
  const rawData = params.get('data');
  const data: ProtoDataMode = (PROTO_DATA_MODES as readonly string[]).includes(rawData ?? '')
    ? (rawData as ProtoDataMode)
    : 'real';

  const go = useCallback(
    (slot: Slot, mode: ProtoDataMode) => {
      const next = new URLSearchParams();
      if (slot !== 'current') next.set('variant', slot);
      if (slot !== 'current' && mode !== 'real') next.set('data', mode);
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname);
    },
    [router, pathname]
  );

  const step = useCallback(
    (delta: number) => {
      const i = ORDER.indexOf(current);
      go(ORDER[(i + delta + ORDER.length) % ORDER.length], data);
    },
    [current, data, go]
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
      if (e.key === 'ArrowLeft') step(-1);
      if (e.key === 'ArrowRight') step(1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [step]);

  const label =
    current === 'current' ? '現行（変更前）' : `${current}（${PROTO_VARIANT_NAMES[current]}）`;
  const nextData = PROTO_DATA_MODES[(PROTO_DATA_MODES.indexOf(data) + 1) % PROTO_DATA_MODES.length];

  const btn: React.CSSProperties = {
    all: 'unset',
    cursor: 'pointer',
    padding: '6px 10px',
    borderRadius: 999,
    lineHeight: 1,
  };

  return (
    <div
      style={{
        position: 'fixed',
        left: '50%',
        bottom: 12,
        transform: 'translateX(-50%)',
        zIndex: 99999,
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        padding: '4px 6px',
        borderRadius: 999,
        background: '#111',
        color: '#fff',
        border: '2px solid #ff0',
        boxShadow: '0 6px 24px rgba(0,0,0,.45)',
        font: '12px/1 ui-monospace, Menlo, monospace',
        whiteSpace: 'nowrap',
      }}
    >
      <button style={btn} aria-label="前の variant" onClick={() => step(-1)}>
        ←
      </button>
      <span style={{ padding: '0 6px' }}>{label}</span>
      <button style={btn} aria-label="次の variant" onClick={() => step(1)}>
        →
      </button>
      {current !== 'current' && (
        <button
          style={{ ...btn, background: '#333' }}
          aria-label="データの状態を切り替える"
          onClick={() => go(current, nextData)}
        >
          {DATA_LABEL[data]}
        </button>
      )}
    </div>
  );
}
