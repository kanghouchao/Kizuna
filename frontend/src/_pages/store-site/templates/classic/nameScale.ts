import type { CSSProperties } from 'react';

/**
 * 縦書きの大きな名前の字の大きさを、字数に合わせて縮める比率。長い名前を大きな字のまま組むと
 * 列が増えすぎて版面を塞ぐ。CSS は字数を知らないので、比率だけを変数で渡す。
 */
export function nameScaleStyle(name: string | undefined): CSSProperties {
  const length = Array.from(name ?? '').length;
  const scale = length <= 12 ? 1 : length <= 20 ? 0.72 : 0.5;
  return { '--classic-name-scale': scale } as CSSProperties;
}
