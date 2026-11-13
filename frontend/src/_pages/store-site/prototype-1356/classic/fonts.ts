import { Shippori_Mincho_B1, Zen_Old_Mincho } from 'next/font/google';

export const classicDisplay = Shippori_Mincho_B1({
  weight: ['500', '700'],
  preload: false,
  display: 'swap',
  variable: '--c-font-display',
});

export const classicBody = Zen_Old_Mincho({
  weight: ['400', '700'],
  preload: false,
  display: 'swap',
  variable: '--c-font-body',
});
