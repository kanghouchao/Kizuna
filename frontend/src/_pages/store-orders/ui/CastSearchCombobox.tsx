'use client';

import { useEffect, useState } from 'react';
import { ChevronsUpDownIcon } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { OrderCastCandidate, orderApi } from '@/entities/order';
import {
  Button,
  Command,
  CommandEmpty,
  CommandInput,
  CommandItem,
  CommandList,
  Label,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/shared/ui';

interface CastSearchComboboxProps {
  /** ラベルと引き金を結ぶ id。同一画面で衝突しない値を親が与える。 */
  id: string;
  label: string;
  /**
   * 親が選択済みとみなしているキャストの名前。id を持つのは親なので、この props が動かすのは
   * 表示だけで、onChange は鳴らない（親の初期値と競合させないため）。
   */
  castName: string;
  /** 候補を選んだときだけ鳴る。 */
  onChange: (castId: string) => void;
  disabled?: boolean;
}

/**
 * 名前で当店の指名候補（在籍中のキャスト）を絞り込んで 1 人選ぶコンボボックス。
 *
 * 検索語は popover の中だけに存在し、閉じると捨てる。選択を動かすのは候補のクリックだけなので、
 * 「打ちかけの文字列」が指名として漏れることも、打ちかけのまま保存して指名が消えることも無い。
 */
export function CastSearchCombobox({
  id,
  label,
  castName,
  onChange,
  disabled,
}: CastSearchComboboxProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [options, setOptions] = useState<OrderCastCandidate[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedName, setSelectedName] = useState(castName);

  useEffect(() => {
    setSelectedName(castName);
  }, [castName]);

  useEffect(() => {
    if (!isOpen) return;

    const search = keyword.trim();
    // 片付けは飛んだ後の通信を止められない。遅れて着いた古い応答を入れると、今の検索語とは
    // 無関係なキャストが候補に残る
    let superseded = false;
    const timer = setTimeout(async () => {
      try {
        setIsLoading(true);
        // 件数上限・並び・在籍中への絞り込みはすべてサーバ側の読み口が持つ
        const candidates = await orderApi.listCastCandidates(search ? { search } : undefined);
        if (superseded) return;
        setOptions(candidates);
      } catch {
        if (superseded) return;
        setOptions([]);
        toast.error('キャスト候補の取得に失敗しました');
      } finally {
        if (!superseded) setIsLoading(false);
      }
    }, 250);

    return () => {
      superseded = true;
      clearTimeout(timer);
    };
  }, [keyword, isOpen]);

  const handleOpenChange = (next: boolean) => {
    setIsOpen(next);
    // 打ちかけの検索語を持ち越すと、次に開いたとき表示中の指名と食い違う候補が出る
    if (!next) {
      setKeyword('');
      setOptions([]);
    }
  };

  const handleSelect = (cast: OrderCastCandidate) => {
    setSelectedName(cast.name ?? '');
    onChange(cast.id ?? '');
    handleOpenChange(false);
  };

  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Popover open={isOpen} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>
          <Button
            id={id}
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={isOpen}
            disabled={disabled}
            className="w-full justify-between font-normal"
          >
            <span className={selectedName ? '' : 'text-muted-foreground'}>
              {selectedName || '名前で検索'}
            </span>
            <ChevronsUpDownIcon className="size-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        {/* 引き金の幅に合わせるが、狭い桁に置かれても候補が潰れないよう下限を持たせる */}
        <PopoverContent className="w-(--radix-popover-trigger-width) min-w-72 p-0" align="start">
          {/* 絞り込みはサーバ側が担うので、cmdk 自前の絞り込みは切る */}
          <Command shouldFilter={false}>
            <CommandInput value={keyword} onValueChange={setKeyword} placeholder="名前で検索" />
            <CommandList>
              {isLoading ? (
                <div className="py-6 text-center text-sm text-muted-foreground">検索中...</div>
              ) : (
                <>
                  <CommandEmpty>該当するキャストがいません</CommandEmpty>
                  {options.map(cast => (
                    <CommandItem key={cast.id} value={cast.id} onSelect={() => handleSelect(cast)}>
                      <span className="font-medium">{cast.name}</span>
                      <span className="ml-auto text-xs text-muted-foreground">ID: {cast.id}</span>
                    </CommandItem>
                  ))}
                </>
              )}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
