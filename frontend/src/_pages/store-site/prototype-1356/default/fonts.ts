import { Shippori_Mincho } from 'next/font/google';

// 見出し・数字・本文の要所は明朝ひとつで通す。本文は system のゴシック。
export const mincho = Shippori_Mincho({
  weight: ['400', '500', '700'],
  subsets: ['latin'],
  preload: false,
  display: 'swap',
  variable: '--p1356d-mincho',
});
