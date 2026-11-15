import Image from 'next/image';

interface PortraitProps {
  url?: string;
  name?: string;
  sizes: string;
  className?: string;
  preload?: boolean;
}

/**
 * 二重罫の額に入れた写真。写真が無いときは名前の一字を罫の中に置き、代わりの画像は使わない。
 * 名前は必ず隣の見出しにあるので、写真と札は読み上げから外し、同じ名前を二度読ませない。
 */
export default function Portrait({ url, name, sizes, className, preload }: PortraitProps) {
  return (
    <div className={`c-frame ${className ?? ''}`}>
      <div className="c-frame-in">
        {url ? (
          <Image src={url} alt="" fill sizes={sizes} preload={preload} className="c-photo" />
        ) : (
          <span className="c-tile c-display" aria-hidden="true">
            {Array.from(name?.trim() ?? '')[0] ?? '〇'}
          </span>
        )}
      </div>
    </div>
  );
}
