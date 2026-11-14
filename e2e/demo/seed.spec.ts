import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { expect, test, type APIRequestContext } from '@playwright/test';
import { APP_URL } from '../base-url';
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  STORE_HEADERS,
  createPermissionRole,
  createShift,
  createStaffAccount,
  createStore,
  findRoleIdByName,
  findStoreIdByDomain,
  loginUser,
  setCastPublication,
  todayInTokyo,
} from '../steps/store-api';

// 目視用のデータ。アカウントは E2E と同じもの（統括 admin@kizuna.test と店長 ADMIN_EMAIL）を作る。
// store1 は全項目を埋め、store2 は何も設定しない（未設定のときの見え方を確かめる店舗）。
const PHOTOS = join(__dirname, 'photos');
const SETUP_ADMIN = 'admin@kizuna.test';

const CASTS = [
  { name: '美咲', age: 24, height: 158, introduction: 'はじめまして。お話しするのが大好きです。\nお仕事帰りに、ふらっと立ち寄ってくださいね。' },
  { name: 'あやか', age: 22, height: 162, introduction: '笑顔がチャームポイントと言われます。\nワインとゴルフのお話なら任せてください。' },
  { name: '玲奈', age: 27, height: 165, bust: 86, waist: 58, hip: 87, introduction: '落ち着いた雰囲気でゆっくりお話ししたい方、ぜひ。\n日本酒を勉強中です。' },
  { name: 'ゆい', age: 21, height: 154 },
  { name: '七海', introduction: '海の近くで育ちました。旅行のお話を聞くのが好きです。' },
  { name: 'さくら', age: 25, height: 160, bust: 84, waist: 57, hip: 85 },
  { name: '麗', age: 29, height: 168, introduction: '銀座は 5 年目になります。\nお酒の選び方に迷ったら、お気軽にお尋ねください。' },
  { name: 'かれん', age: 23, height: 156 },
  { name: 'みお', introduction: '映画と猫の話になると止まりません。' },
  { name: '凛', age: 26, height: 163, bust: 85, waist: 58, hip: 86, introduction: '静かに飲みたい夜に、隣でお付き合いします。' },
  { name: 'アナスタシア・クリスティーナ', age: 24, height: 170, introduction: '名前が長いときの折り返しを確かめるためのキャストです。' },
  // 写真の無いキャスト（写真が無いときの枠を確かめる）
  { name: 'ひなた', age: 20, height: 152 },
];

const ARTICLES = [
  { title: '年末年始の営業について', body: '12 月 31 日から 1 月 3 日まで休業いたします。\n新年は 1 月 4 日 20:00 より通常どおり営業いたします。', cover: true },
  { title: '新人キャスト入店のお知らせ', body: '今月、新しいキャストが 3 名入店いたしました。\nキャスト一覧よりプロフィールをご覧ください。', cover: false },
  { title: '店内改装が完了しました', body: 'ソファ席を増やし、照明を落ち着いた色に改めました。\nより寛いでお過ごしいただけます。', cover: false },
];

const REVIEWS = [
  { display_name: 'T.K', body: '接客がとても丁寧で、初めてでも落ち着いて過ごせました。\nまた伺います。' },
  { display_name: '銀座の常連', body: '内装が上品で、接待にも安心して使えます。' },
  { display_name: 'M', body: 'キャストの皆さんの会話の引き出しが多く、あっという間の 2 時間でした。料金の説明も最初に分かりやすくしてもらえたので、会計で驚くこともありませんでした。' },
];

async function upload(request: APIRequestContext, token: string, purpose: string, file: string): Promise<string> {
  const response = await request.post('/api/files', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    multipart: { purpose, file: { name: file, mimeType: 'image/png', buffer: readFileSync(join(PHOTOS, file)) } },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).url;
}

async function post(request: APIRequestContext, token: string, path: string, data: unknown, expected = 201) {
  const response = await request.post(path, { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data });
  expect(response.status(), `${path}: ${await response.text()}`).toBe(expected);
  return response.json();
}

async function put(request: APIRequestContext, token: string, path: string, data: unknown) {
  const response = await request.put(path, { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data });
  expect(response.status(), `${path}: ${await response.text()}`).toBe(200);
  return response.json();
}

function dayFromToday(offset: number): string {
  const date = new Date(`${todayInTokyo()}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}

test('目視用のデータを投入する', async ({ request }) => {
  const setup = await request.post(`${APP_URL}/api/auth/initial-setup`, {
    data: { email: SETUP_ADMIN, password: ADMIN_PASSWORD, display_name: '統括' },
  });
  expect(setup.ok(), await setup.text()).toBeTruthy();
  const admin = await loginUser(request, SETUP_ADMIN, ADMIN_PASSWORD);

  await createStore(request, admin, { name: 'CLUB 月華 銀座本店', short_name: '月華', domain: 'store1.kizuna.test' });
  await createStore(request, admin, { name: 'ラウンジ 未設定', short_name: '未設定', domain: 'store2.kizuna.test' });
  const storeIds = [
    Number(await findStoreIdByDomain(request, admin, 'store1.kizuna.test')),
    Number(await findStoreIdByDomain(request, admin, 'store2.kizuna.test')),
  ];
  // 記事と口コミの権限は店長の既定に依らず、専用のロールで足す
  const content = await createPermissionRole(request, admin, '目視用の掲載担当', [
    'ARTICLE_MANAGE',
    'REVIEW_VIEW',
    'REVIEW_MANAGE',
    'REVIEW_MODERATE',
  ]);
  await createStaffAccount(request, admin, {
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD,
    displayName: '田中 花子',
    roleIds: [await findRoleIdByName(request, admin, '店長'), content],
    storeIds,
  });
  const token = await loginUser(request, ADMIN_EMAIL, ADMIN_PASSWORD);

  await put(request, token, '/api/config', {
    logo_url: await upload(request, token, 'store-logo', 'logo.png'),
    banner_url: await upload(request, token, 'store-banner', 'banner.png'),
    mv_url: await upload(request, token, 'store-mv', 'mv.png'),
    mv_type: 'image',
    catch_copy: '今宵、月の華が咲く。\n銀座で過ごす、上質な時間。',
    description: '銀座の中心に佇む、大人のための社交場。\n落ち着いた空間と洗練されたおもてなしで、特別なひとときをお届けします。',
    address: '東京都中央区銀座 0-0-0 月華ビル 5F',
    phone: '03-0000-0000',
    business_hours: '20:00 〜 LAST',
    pricing_description: 'セット料金 60分 10,000円〜\n延長 30分 6,000円\n指名料 3,000円\nサービス料・消費税を別途申し受けます。',
    partner_links: [
      { name: 'ナイトワーク求人ナビ', url: 'https://example.com/partner-a' },
      { name: '銀座ナイトガイド', url: 'https://example.com/partner-b' },
    ],
    custom_texts: { access_note: '銀座駅 A0 出口より徒歩 3 分' },
    sns_links: [
      { platform: 'x', url: 'https://example.com/x', label: 'X' },
      { platform: 'instagram', url: 'https://example.com/ig', label: 'Instagram' },
    ],
  });

  const photos = readdirSync(PHOTOS).filter((file) => file.startsWith('cast-')).sort();
  const castIds: string[] = [];
  for (const [index, cast] of CASTS.entries()) {
    const photo = photos[index];
    const created = await post(request, token, '/api/casts', {
      ...cast,
      started_on: todayInTokyo(),
      display_order: index,
      ...(photo ? { photo_url: await upload(request, token, 'cast-photo', photo) } : {}),
    });
    await setCastPublication(request, token, created.id, 'PUBLISHED');
    castIds.push(created.id);
  }

  // 今日から 7 日分。日ごとに出勤する顔ぶれと時間帯をずらす
  for (let day = 0; day < 7; day += 1) {
    for (const [index, castId] of castIds.entries()) {
      if ((index + day) % 3 === 0) continue;
      await createShift(request, token, {
        castId,
        workDate: dayFromToday(day),
        startTime: index % 2 === 0 ? '20:00:00' : '18:00:00',
        endTime: index % 2 === 0 ? '02:00:00' : '23:00:00',
        status: 'CONFIRMED',
      });
    }
  }

  const cover = await upload(request, token, 'article-cover', 'article.png');
  for (const [index, article] of ARTICLES.entries()) {
    const created = await post(request, token, '/api/articles', {
      title: article.title,
      body: article.body,
      ...(article.cover ? { cover_image_url: cover } : {}),
    });
    await put(request, token, `/api/articles/${created.id}/publication`, {
      // 作成の応答は版を返さない。作成直後の版は 0
      version: 0,
      published: true,
      publish_from: new Date(Date.now() - (index + 1) * 86_400_000).toISOString(),
    });
  }

  for (const [index, review] of REVIEWS.entries()) {
    const key = `demo-${index}`;
    const received = (
      await post(request, token, '/api/reviews', {
        ...review,
        received_via: 'PAPER',
        received_at: new Date(Date.now() - (index + 2) * 86_400_000).toISOString(),
        dedupe_key: key,
      })
    ).review;
    const base = `/api/reviews/${received.id}`;
    const approved = (
      await post(request, token, `${base}/decisions`, { version: received.version, decision: 'APPROVE', reason: '内容を確認', dedupe_key: `${key}-approve` }, 200)
    ).review;
    const permitted = (
      await post(
        request,
        token,
        `${base}/permissions`,
        { version: approved.version, basis_type: 'WRITTEN', granted_at: new Date().toISOString(), evidence_note: '書面で掲載を確認', dedupe_key: `${key}-grant` },
        201,
      )
    ).review;
    await put(request, token, `${base}/publication`, {
      version: permitted.version,
      published: true,
      publish_from: new Date(Date.now() - 60_000).toISOString(),
      dedupe_key: `${key}-publish`,
    });
  }
});
