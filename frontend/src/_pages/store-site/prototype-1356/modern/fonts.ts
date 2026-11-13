import { M_PLUS_Rounded_1c } from 'next/font/google';

// 見出し用の丸ゴシック。本文はシステムの角ゴシックに任せる
export const roundedDisplay = M_PLUS_Rounded_1c({
  weight: ['500', '800'],
  subsets: ['latin'],
  preload: false,
  display: 'swap',
  variable: '--m-font-display',
});
