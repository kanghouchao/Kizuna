import Image from 'next/image';

interface PortraitProps {
  url?: string;
  name?: string;
  sizes: string;
  /** 写真が無いときの文字札の字の大きさ */
  glyphSize: number;
  className?: string;
  preload?: boolean;
}

/** 細い二重罫の額。写真が無ければ名の一字を罫の中に置く（影絵は使わない） */
export default function Portrait({ url, name, sizes, glyphSize, className, preload }: PortraitProps) {
  const glyph = name ? Array.from(name.trim())[0] ?? '〇' : '〇';
  return (
    <div className={`c-frame ${className ?? ''}`}>
      <div className="c-frame-in">
        {url ? (
          <Image
            src={url}
            alt={name ?? 'キャスト'}
            fill
            sizes={sizes}
            preload={preload}
            className="c-photo"
          />
        ) : (
          <span
            className="c-tile c-display"
            style={{ fontSize: `${glyphSize}px` }}
            role="img"
            aria-label={name ?? 'キャスト'}
          >
            {glyph}
          </span>
        )}
      </div>
    </div>
  );
}
