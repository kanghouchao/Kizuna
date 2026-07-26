import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { StoreProfileForm } from '../ui/StoreProfileForm';
import type { StoreProfileResponse, StoreProfileUpdateRequest } from '@/entities/store-profile';

// 送信ペイロードの形を固定する錨。プリミティブ置換で defaultValues や
// フィールド配線が崩れると、この期待値が先に落ちる。
const PAYLOAD_KEYS = [
  'address',
  'banner_url',
  'business_hours',
  'catch_copy',
  'custom_texts',
  'description',
  'logo_url',
  'mv_type',
  'mv_url',
  'partner_links',
  'phone',
  'pricing_description',
  'sns_links',
  'template_key',
];

function initialData(overrides: Partial<StoreProfileResponse> = {}): StoreProfileResponse {
  return {
    id: '1',
    template_key: 'default',
    mv_type: 'image',
    logo_url: '/uploads/logo.png',
    banner_url: '/uploads/banner.png',
    mv_url: '/uploads/mv.png',
    description: '説明',
    catch_copy: '',
    address: '',
    phone: '',
    business_hours: '',
    pricing_description: '',
    custom_texts: { access_note: '既存メモ', modern_only_key: '他模版の値' },
    sns_links: [{ platform: 'instagram', url: 'https://insta', label: '' }],
    partner_links: [],
    created_at: '',
    updated_at: '',
    ...overrides,
  };
}

function renderForm(data: StoreProfileResponse) {
  const onSubmit = jest.fn<Promise<void>, [StoreProfileUpdateRequest]>().mockResolvedValue();
  const view = render(
    <StoreProfileForm initialData={data} onSubmit={onSubmit} isSubmitting={false} />
  );
  return { ...view, onSubmit };
}

async function submitAndGetBody(onSubmit: jest.Mock) {
  fireEvent.click(screen.getByRole('button', { name: '設定を保存する' }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  return onSubmit.mock.calls[0][0] as StoreProfileUpdateRequest;
}

describe('店舗プロフィールフォームの送信ペイロード', () => {
  it('無編集の送信でも保存済みの全キーと値がそのまま送られること', async () => {
    const { onSubmit } = renderForm(initialData());

    const body = await submitAndGetBody(onSubmit);

    expect(Object.keys(body).sort()).toEqual(PAYLOAD_KEYS);
    expect(body.template_key).toBe('default');
    expect(body.mv_type).toBe('image');
    expect(body.description).toBe('説明');
    // 選択肢の先頭値（twitter）へ化けず、保存済みの platform と label が保たれること
    expect(body.sns_links).toEqual([{ platform: 'instagram', url: 'https://insta', label: '' }]);
    expect(body.partner_links).toEqual([]);
    // 画像欄はアップロード済み URL を保持するだけで、無編集の送信でも値ごと往復すること
    expect(body.logo_url).toBe('/uploads/logo.png');
    expect(body.banner_url).toBe('/uploads/banner.png');
    expect(body.mv_url).toBe('/uploads/mv.png');
  });

  it('既定値ではない mv_type が無編集の送信で保持されること', async () => {
    const { onSubmit } = renderForm(initialData({ mv_type: 'video' }));

    const body = await submitAndGetBody(onSubmit);

    expect(body.mv_type).toBe('video');
  });

  it('セレクトで選び直した MV タイプが送信ペイロードに反映されること', async () => {
    const { onSubmit } = renderForm(initialData());

    // キーボードで開く経路のみを使う（ポインタ系 API は jsdom に無い）
    fireEvent.keyDown(screen.getByRole('combobox', { name: 'タイプ:' }), { key: 'ArrowDown' });
    fireEvent.click(await screen.findByRole('option', { name: '動画' }));

    const body = await submitAndGetBody(onSubmit);

    expect(body.mv_type).toBe('video');
  });

  it('セレクトで選び直した SNS プラットフォームが送信ペイロードに反映されること', async () => {
    const { onSubmit } = renderForm(initialData());

    fireEvent.keyDown(screen.getByRole('combobox', { name: 'プラットフォーム' }), {
      key: 'ArrowDown',
    });
    fireEvent.click(await screen.findByRole('option', { name: 'LINE' }));

    const body = await submitAndGetBody(onSubmit);

    // 行の他のキーは巻き添えで消えないこと
    expect(body.sns_links).toEqual([{ platform: 'line', url: 'https://insta', label: '' }]);
  });

  it('模版テキストの編集後も現模版に無い custom_texts の key が保持されること', async () => {
    const { container, onSubmit } = renderForm(initialData());

    const slot = container.querySelector('[name="custom_texts.access_note"]');
    expect(slot).not.toBeNull();
    fireEvent.change(slot as HTMLTextAreaElement, { target: { value: '新値' } });

    const body = await submitAndGetBody(onSubmit);

    expect(body.custom_texts).toEqual({ access_note: '新値', modern_only_key: '他模版の値' });
  });

  it('テンプレートカードの選択が template_key に反映されること', async () => {
    const { onSubmit } = renderForm(initialData());

    fireEvent.click(screen.getByRole('radio', { name: /モダン/ }));

    const body = await submitAndGetBody(onSubmit);

    expect(body.template_key).toBe('modern');
  });

  it('SNS リンクの追加行が既定値どおりのキーで送られること', async () => {
    const { onSubmit } = renderForm(initialData());

    fireEvent.click(screen.getAllByRole('button', { name: '+ 追加' })[0]);
    const urlInputs = screen.getAllByPlaceholderText('https://...');
    fireEvent.change(urlInputs[urlInputs.length - 1], { target: { value: 'https://new.test' } });

    const body = await submitAndGetBody(onSubmit);

    expect(body.sns_links).toHaveLength(2);
    expect(body.sns_links?.[1]).toEqual({
      platform: 'twitter',
      url: 'https://new.test',
      label: '',
    });
  });

  it('保存済みのパートナー行がロゴ URL ごと無編集の送信で往復すること', async () => {
    const partner = { name: '提携店', url: 'https://partner.test', logo_url: '/uploads/p.png' };
    const { onSubmit } = renderForm(initialData({ partner_links: [partner] }));

    const body = await submitAndGetBody(onSubmit);

    expect(body.partner_links).toEqual([partner]);
  });

  it('パートナーリンクの追加行が既定値どおりのキーで送られること', async () => {
    const { onSubmit } = renderForm(initialData());

    fireEvent.click(screen.getAllByRole('button', { name: '+ 追加' })[1]);
    fireEvent.change(screen.getByPlaceholderText('パートナー名'), { target: { value: '提携B' } });
    const urlInputs = screen.getAllByPlaceholderText('https://...');
    fireEvent.change(urlInputs[urlInputs.length - 1], {
      target: { value: 'https://partner-b.test' },
    });

    const body = await submitAndGetBody(onSubmit);

    expect(body.partner_links).toEqual([
      { name: '提携B', url: 'https://partner-b.test', logo_url: '' },
    ]);
  });
});
