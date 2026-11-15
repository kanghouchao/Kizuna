import { Shippori_Mincho_B1, Zen_Old_Mincho } from 'next/font/google';

// 和文の書体は subset を持たず、字の範囲ごとに分かれたファイルを必要な分だけ読む。先読みはできない
export const classicDisplay = Shippori_Mincho_B1({
  weight: ['500', '700'],
  preload: false,
  display: 'swap',
  variable: '--classic-font-display',
});

export const classicBody = Zen_Old_Mincho({
  weight: '400',
  preload: false,
  display: 'swap',
  variable: '--classic-font-body',
});

/** classic の様式が効く根のクラス。ページの根と、body の直下へ描くダイアログの根の両方に付ける。 */
export const classicRootClass = `classic-site ${classicDisplay.variable} ${classicBody.variable}`;
