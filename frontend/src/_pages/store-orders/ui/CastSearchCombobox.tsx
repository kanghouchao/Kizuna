'use client';

import { useEffect, useRef, useState } from 'react';
import { toast } from 'react-hot-toast';
import { CastResponse, castApi } from '@/entities/cast';
import { Input, Label } from '@/shared/ui';

interface CastSearchComboboxProps {
  /** input と候補リストを結ぶ id。同一画面で衝突しない値を親が与える。 */
  id: string;
  label: string;
  /**
   * 親が選択済みとみなしているキャストの名前。id を持つのは親なので、この props が動かすのは
   * 表示だけで、onChange は鳴らない（親の初期値と競合させないため）。
   */
  castName: string;
  /** 候補を選ぶとその id、名前を打ち直して選択が外れると null。 */
  onChange: (castId: string | null) => void;
  disabled?: boolean;
}

/**
 * 名前で当店のキャストを絞り込んで 1 人選ぶコンボボックス。
 *
 * 選択の確定は候補のクリックだけで、打ちかけの文字列では id を返さない。曖昧な名前のまま
 * 送れてしまうと、店舗が意図しないキャストが指名として残るため。
 */
export function CastSearchCombobox({
  id,
  label,
  castName,
  onChange,
  disabled,
}: CastSearchComboboxProps) {
  const [nameInput, setNameInput] = useState(castName);
  const [options, setOptions] = useState<CastResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  // 表示名の差し込み（親からの反映・候補の選択）で検索が走ると、選んだ直後に候補が開き直す
  const skipNextSearchRef = useRef(false);

  useEffect(() => {
    skipNextSearchRef.current = true;
    setNameInput(castName);
  }, [castName]);

  useEffect(() => {
    if (skipNextSearchRef.current) {
      skipNextSearchRef.current = false;
      return;
    }

    const keyword = nameInput.trim();
    if (!keyword) {
      setOptions([]);
      setIsOpen(false);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        setIsLoading(true);
        const response = await castApi.list({
          size: 10,
          sort: 'displayOrder,asc',
          search: keyword,
        });
        setOptions(response.rows);
        setIsOpen(true);
      } catch {
        toast.error('キャスト候補の取得に失敗しました');
      } finally {
        setIsLoading(false);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [nameInput]);

  const handleSelect = (cast: CastResponse) => {
    skipNextSearchRef.current = true;
    setNameInput(cast.name ?? '');
    onChange(cast.id ?? null);
    setIsOpen(false);
  };

  const handleInputChange = (value: string) => {
    setNameInput(value);
    onChange(null);
  };

  return (
    <div className="relative grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type="text"
        value={nameInput}
        onChange={e => handleInputChange(e.target.value)}
        onFocus={() => options.length > 0 && setIsOpen(true)}
        placeholder="名前で検索"
        role="combobox"
        aria-expanded={isOpen}
        aria-controls={`${id}-suggestions`}
        autoComplete="off"
        disabled={disabled}
      />
      {isOpen && (
        <div
          id={`${id}-suggestions`}
          role="listbox"
          className="absolute top-full z-20 mt-1 w-full rounded-md border border-border bg-popover shadow-lg"
        >
          {isLoading ? (
            <div className="px-4 py-2 text-sm text-muted-foreground">検索中...</div>
          ) : options.length === 0 ? (
            <div className="px-4 py-2 text-sm text-muted-foreground">
              該当するキャストがいません
            </div>
          ) : (
            <ul className="max-h-56 overflow-auto py-1">
              {options.map(cast => (
                <li key={cast.id}>
                  <button
                    type="button"
                    onClick={() => handleSelect(cast)}
                    className="flex w-full items-center justify-between px-4 py-2 text-left text-sm text-foreground hover:bg-primary/10"
                    role="option"
                  >
                    <span className="font-medium">{cast.name}</span>
                    <span className="text-xs text-muted-foreground">ID: {cast.id}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
