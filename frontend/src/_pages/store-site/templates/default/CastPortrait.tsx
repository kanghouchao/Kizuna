import Image from 'next/image';
import type { Cast } from '../../model/types';

interface CastPortraitProps {
  cast: Cast;
  sizes: string;
  preload?: boolean;
}

/** キャストの写真。写真が無ければ代わりの画像は置かず、名前の一文字目を明朝で据える。 */
export default function CastPortrait({ cast, sizes, preload }: CastPortraitProps) {
  if (cast.photo_url) {
    return (
      <Image
        src={cast.photo_url}
        alt={cast.name ?? 'キャスト'}
        fill
        preload={preload}
        sizes={sizes}
        className="sfd-cover"
      />
    );
  }
  return (
    <div className="sfd-nametile" aria-hidden="true">
      <span>{cast.name ? Array.from(cast.name)[0] : '・'}</span>
    </div>
  );
}

export const castNo = (index: number) => String(index + 1).padStart(2, '0');
