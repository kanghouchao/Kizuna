import { M_PLUS_Rounded_1c } from 'next/font/google';

// 見出し用の丸ゴシック。本文は端末の書体に任せる。日本語の字形は分割して配られ、使う分だけ読まれる
export const roundedDisplay = M_PLUS_Rounded_1c({
  weight: '800',
  subsets: ['latin'],
  preload: false,
  display: 'swap',
  variable: '--m-font-display',
});
