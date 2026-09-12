'use client';

import { useEffect, useState } from 'react';
import { OrderCastCandidate, orderApi } from '@/entities/order';
import {
  Button,
  Label,
  RegionError,
  Combobox,
  ComboboxTrigger,
  ComboboxContent,
  ComboboxInput,
  ComboboxEmpty,
  ComboboxList,
  ComboboxItem,
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
  /**
   * handleSubmit の焦点移動が叩く先。焦点要素は popup の中の入力ではなく引き金の button なので、
   * ここへ届かないと文言だけ出て焦点が動かない（他に症状が出ない）。
   */
  triggerRef?: React.Ref<HTMLButtonElement>;
  /** FormControl が差し込む欄の状態。引き金は button で原生の制約を持てないため props で受ける。 */
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
  /**
   * 必須の指名かどうか。引き金へ aria-required として手で載せる。
   * Combobox の required は使えない——この構成は value を null に固定して選択を出来事としてだけ
   * 受け取るため、Root 側の必須は永久に未充足のままになり、実測で送信そのものが通らなくなった。
   */
  required?: boolean;
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
  triggerRef,
  'aria-invalid': ariaInvalid,
  'aria-describedby': ariaDescribedby,
  required,
}: CastSearchComboboxProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [options, setOptions] = useState<OrderCastCandidate[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [selectedName, setSelectedName] = useState(castName);
  // 再試行のたびに増やして、検索語が同じままでも取得を走らせ直す
  const [attempt, setAttempt] = useState(0);

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
        setFailed(false);
      } catch {
        if (superseded) return;
        // 空の候補一覧は「該当するキャストがいません」と区別がつかない。読めなかったことを名乗る
        setOptions([]);
        setFailed(true);
      } finally {
        if (!superseded) setIsLoading(false);
      }
    }, 250);

    return () => {
      superseded = true;
      clearTimeout(timer);
    };
  }, [keyword, isOpen, attempt]);

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
      {/*
       * 選択を持つのは親なので、combobox 自身の値は常に null に固定して選択を出来事としてだけ受け取る。
       * 絞り込みはサーバ側の読み口が担うため filter は切る。
       */}
      <Combobox
        items={options}
        filter={null}
        value={null}
        onValueChange={cast => cast && handleSelect(cast)}
        open={isOpen}
        onOpenChange={handleOpenChange}
        inputValue={keyword}
        onInputValueChange={setKeyword}
        itemToStringLabel={(cast: OrderCastCandidate) => cast.name ?? ''}
        disabled={disabled}
      >
        <ComboboxTrigger
          render={
            <Button
              id={id}
              type="button"
              variant="outline"
              className="min-w-0"
              ref={triggerRef}
              aria-invalid={ariaInvalid}
              aria-describedby={ariaDescribedby}
              aria-required={required}
            />
          }
        >
          <span className={selectedName ? 'truncate' : 'truncate text-muted-foreground'}>
            {selectedName || '名前で検索'}
          </span>
        </ComboboxTrigger>
        <ComboboxContent className="min-w-72">
          <ComboboxInput placeholder="名前で検索" />
          {isLoading ? (
            <div className="py-6 text-center text-sm text-muted-foreground">検索中...</div>
          ) : failed ? (
            <RegionError
              message="キャスト候補の取得に失敗しました"
              onRetry={() => setAttempt(count => count + 1)}
              className="justify-center px-3 py-6"
            />
          ) : (
            <>
              <ComboboxEmpty>該当するキャストがいません</ComboboxEmpty>
              <ComboboxList>
                {(cast: OrderCastCandidate) => (
                  <ComboboxItem key={cast.id} value={cast}>
                    <span className="font-medium">{cast.name}</span>
                    <span className="ml-auto text-xs text-muted-foreground">ID: {cast.id}</span>
                  </ComboboxItem>
                )}
              </ComboboxList>
            </>
          )}
        </ComboboxContent>
      </Combobox>
    </div>
  );
}
