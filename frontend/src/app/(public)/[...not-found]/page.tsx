import { notFound } from 'next/navigation';

/**
 * 根 layout を (admin) / (public) に分割すると、どの route にも一致しない URL を受ける
 * 根の not-found が置けなくなる。この catch-all が未一致 URL を (public) 世界の
 * not-found へ導く（明示 route が常に優先されるため、既存画面には影響しない）。
 */
export default function NotFoundCatchAll(): never {
  notFound();
}
