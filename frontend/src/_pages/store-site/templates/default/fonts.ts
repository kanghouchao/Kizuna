import { Shippori_Mincho } from 'next/font/google';

// 見出しは明朝ひとつで通し、本文は端末のゴシックに任せる。日本語の字形は unicode-range で
// 分割配信されるので、先読みはしない。
export const mincho = Shippori_Mincho({
  weight: '400',
  subsets: ['latin'],
  preload: false,
  display: 'swap',
  variable: '--sfd-mincho',
});
