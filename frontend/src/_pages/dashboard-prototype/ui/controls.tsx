'use client';

import { ChevronLeftIcon, ChevronRightIcon, SlidersHorizontalIcon } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

// 試作専用。区画ごとの状態を手で切り替え、各案の出し分け・失敗表示を比べる。
export type RegionState = 'ok' | 'denied' | 'error' | 'loading';

const STATE_LABELS: Record<RegionState, string> = {
  ok: '許可',
  denied: '権限なし',
  error: '取得失敗',
  loading: '読込中',
};

export type Variant = { key: string; name: string };

/** perms は取得先の端点が要る権限（すべて必要）。candidate は spec の第一弾に無い候補区画。 */
export type RegionDef = { key: string; label: string; perms: string[]; candidate?: boolean };

/** 権限の組合せの例。既定ロールは今の種子の権限そのもの。区画の権限の提案ではない。 */
export type Preset = { label: string; perms: string[] };

type Settings = {
  states: Record<string, RegionState>;
  storeCount: number;
  longNames: boolean;
  monthDown: boolean;
  storeCard: boolean;
  showCandidates: boolean;
};

type ProtoContextValue = Settings & {
  candidateKeys: string[];
  retry: (key: string) => void;
};

const ProtoContext = createContext<ProtoContextValue | null>(null);

export function useProto() {
  const ctx = useContext(ProtoContext);
  if (ctx === null) throw new Error('PrototypeProvider の外');
  return ctx;
}

/** 区画の状態。denied なら区画ごと描かない。 */
export function useRegion(key: string) {
  const { states, retry, showCandidates, candidateKeys } = useProto();
  const hidden = !showCandidates && candidateKeys.includes(key);
  return { state: hidden ? 'denied' : (states[key] ?? 'ok'), retry: () => retry(key) };
}

export function PrototypeProvider({
  regions,
  presets,
  variants,
  current,
  showStoreOptions,
  children,
}: {
  regions: RegionDef[];
  presets: Preset[];
  variants: Variant[];
  current: string;
  showStoreOptions: 'group' | 'store';
  children: ReactNode;
}) {
  const [settings, setSettings] = useState<Settings>(() => ({
    states: Object.fromEntries(regions.map(r => [r.key, 'ok'])),
    storeCount: 6,
    longNames: false,
    monthDown: false,
    storeCard: false,
    showCandidates: true,
  }));
  const [presetLabel, setPresetLabel] = useState<string | null>(null);

  const applyPreset = (preset: Preset) => {
    setPresetLabel(preset.label);
    setSettings(s => ({
      ...s,
      states: Object.fromEntries(
        regions.map(r => [r.key, r.perms.every(p => preset.perms.includes(p)) ? 'ok' : 'denied'])
      ),
    }));
  };
  const candidateKeys = useMemo(() => regions.filter(r => r.candidate).map(r => r.key), [regions]);

  const setAll = (state: RegionState) =>
    setSettings(s => ({
      ...s,
      states: Object.fromEntries(regions.map(r => [r.key, state])),
    }));

  const retry = useCallback((key: string) => {
    setSettings(s => ({ ...s, states: { ...s.states, [key]: 'loading' } }));
    setTimeout(() => setSettings(s => ({ ...s, states: { ...s.states, [key]: 'ok' } })), 800);
  }, []);

  const value = useMemo(
    () => ({ ...settings, retry, candidateKeys }),
    [settings, retry, candidateKeys]
  );

  return (
    <ProtoContext.Provider value={value}>
      <div className="pb-24">{children}</div>
      <SwitcherBar
        variants={variants}
        current={current}
        panel={
          <div className="space-y-3 text-xs">
            <p className="text-white/70">権限の組合せ{presetLabel && `（いま: ${presetLabel}）`}</p>
            <div className="flex flex-wrap gap-1">
              {presets.map(p => (
                <button
                  key={p.label}
                  type="button"
                  className="rounded border border-white/30 px-2 py-1 hover:bg-white/10"
                  onClick={() => applyPreset(p)}
                >
                  {p.label}
                </button>
              ))}
            </div>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={settings.showCandidates}
                onChange={e => setSettings(s => ({ ...s, showCandidates: e.target.checked }))}
              />
              第一弾に無い候補区画（☆）も出す
            </label>
            <div className="flex flex-wrap gap-1">
              {(['ok', 'denied', 'error', 'loading'] as RegionState[]).map(s => (
                <button
                  key={s}
                  type="button"
                  className="rounded border border-white/30 px-2 py-1 hover:bg-white/10"
                  onClick={() => {
                    setPresetLabel(null);
                    setAll(s);
                  }}
                >
                  全部{STATE_LABELS[s]}
                </button>
              ))}
            </div>
            <table className="w-full">
              <tbody>
                {regions.map(r => (
                  <tr key={r.key}>
                    <td className="py-0.5 pr-2" title={r.perms.join(' + ') || '権限 1 つ以上'}>
                      {r.candidate && '☆'}
                      {r.label}
                    </td>
                    <td>
                      <select
                        className="rounded bg-neutral-800 px-1 py-0.5"
                        value={settings.states[r.key]}
                        onChange={e =>
                          setSettings(s => ({
                            ...s,
                            states: { ...s.states, [r.key]: e.target.value as RegionState },
                          }))
                        }
                      >
                        {(Object.keys(STATE_LABELS) as RegionState[]).map(s => (
                          <option key={s} value={s}>
                            {STATE_LABELS[s]}
                          </option>
                        ))}
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={settings.monthDown}
                onChange={e => setSettings(s => ({ ...s, monthDown: e.target.checked }))}
              />
              今月の売上を前月同時点より減にする
            </label>
            {showStoreOptions === 'group' ? (
              <>
                <label className="flex items-center gap-2">
                  店舗数
                  <select
                    className="rounded bg-neutral-800 px-1 py-0.5"
                    value={settings.storeCount}
                    onChange={e => setSettings(s => ({ ...s, storeCount: Number(e.target.value) }))}
                  >
                    {[1, 3, 6, 12, 40].map(n => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={settings.longNames}
                    onChange={e => setSettings(s => ({ ...s, longNames: e.target.checked }))}
                  />
                  長い店名を混ぜる
                </label>
              </>
            ) : (
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={settings.storeCard}
                  onChange={e => setSettings(s => ({ ...s, storeCard: e.target.checked }))}
                />
                「店舗名と状態」カードを残す
              </label>
            )}
          </div>
        }
      />
    </ProtoContext.Provider>
  );
}

function SwitcherBar({
  variants,
  current,
  panel,
}: {
  variants: Variant[];
  current: string;
  panel: ReactNode;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const index = Math.max(
    0,
    variants.findIndex(v => v.key === current)
  );

  const go = useCallback(
    (delta: number) => {
      const next = variants[(index + delta + variants.length) % variants.length];
      const url = new URL(window.location.href);
      url.searchParams.set('variant', next.key);
      router.replace(url.pathname + url.search, { scroll: false });
    },
    [index, variants, router]
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t?.closest('input, textarea, select, [contenteditable]')) return;
      if (e.key === 'ArrowLeft') go(-1);
      if (e.key === 'ArrowRight') go(1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [go]);

  const v = variants[index];
  return (
    // 試作の操作盤。評価対象の画面と混ざらないよう、テーマに関係なく黒地にする。z はダイアログ（z-50）より下。
    <div className="fixed bottom-4 left-1/2 z-40 -translate-x-1/2 text-white">
      {open && (
        <div className="mb-2 max-h-[60vh] w-80 overflow-auto rounded-xl bg-neutral-900 p-3 shadow-2xl ring-1 ring-white/20">
          {panel}
        </div>
      )}
      <div className="flex items-center gap-1 rounded-full bg-neutral-900 px-2 py-1 shadow-2xl ring-1 ring-white/20">
        <button
          type="button"
          aria-label="前の案"
          className="rounded-full p-1.5 hover:bg-white/10"
          onClick={() => go(-1)}
        >
          <ChevronLeftIcon className="size-4" />
        </button>
        <span className="min-w-48 px-2 text-center text-sm">
          試作 {v.key}（{v.name}）
        </span>
        <button
          type="button"
          aria-label="次の案"
          className="rounded-full p-1.5 hover:bg-white/10"
          onClick={() => go(1)}
        >
          <ChevronRightIcon className="size-4" />
        </button>
        <button
          type="button"
          aria-label="区画の状態"
          aria-expanded={open}
          className="rounded-full p-1.5 hover:bg-white/10"
          onClick={() => setOpen(o => !o)}
        >
          <SlidersHorizontalIcon className="size-4" />
        </button>
      </div>
    </div>
  );
}
