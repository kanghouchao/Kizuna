'use client';

import { useState, useRef } from 'react';
import Image from 'next/image';
import { PhotoIcon, XMarkIcon } from '@heroicons/react/24/outline';
import { fileApi } from '@/services/tenant/api';
import { toast } from 'react-hot-toast';

interface ImageUploadProps {
  /** 現在の画像URL */
  value?: string;
  /** 画像URL変更時のコールバック */
  onChange: (url: string) => void;
  /** アップロード先ディレクトリ */
  directory?: string;
}

/** 画像アップロードコンポーネント */
export default function ImageUpload({ value, onChange, directory = 'casts' }: ImageUploadProps) {
  const [isUploading, setIsUploading] = useState(false);
  const [preview, setPreview] = useState<string | undefined>(value);
  const inputRef = useRef<HTMLInputElement>(null);

  /** ファイル選択時の処理 */
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // プレビュー表示
    const reader = new FileReader();
    reader.onload = () => setPreview(reader.result as string);
    reader.readAsDataURL(file);

    try {
      setIsUploading(true);
      const response = await fileApi.upload(file, directory);
      onChange(response.url);
      toast.success('画像をアップロードしました');
    } catch {
      toast.error('画像のアップロードに失敗しました');
      setPreview(value);
    } finally {
      setIsUploading(false);
    }
  };

  /** 画像を削除する */
  const handleRemove = () => {
    setPreview(undefined);
    onChange('');
    if (inputRef.current) inputRef.current.value = '';
  };

  // data: URI（ローカルプレビュー）はそのまま使用し、APIパスの場合は /api プレフィックスを付与
  const displayUrl = preview;

  return (
    <div className="space-y-2">
      <div
        className="relative w-40 h-52 border-2 border-dashed border-gray-300 rounded-lg overflow-hidden cursor-pointer hover:border-indigo-400 transition-colors"
        onClick={() => inputRef.current?.click()}
      >
        {displayUrl ? (
          <>
            <Image src={displayUrl} alt="プレビュー" fill className="object-cover" sizes="160px" />
            <button
              type="button"
              onClick={e => {
                e.stopPropagation();
                handleRemove();
              }}
              className="absolute top-1 right-1 bg-red-500 text-white rounded-full p-1 hover:bg-red-600"
            >
              <XMarkIcon className="h-4 w-4" />
            </button>
          </>
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-gray-400">
            <PhotoIcon className="h-10 w-10 mb-2" />
            <span className="text-xs">{isUploading ? 'アップロード中...' : '写真を選択'}</span>
          </div>
        )}
        {isUploading && (
          <div className="absolute inset-0 bg-white/70 flex items-center justify-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600" />
          </div>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        onChange={handleFileChange}
        className="hidden"
      />
    </div>
  );
}
