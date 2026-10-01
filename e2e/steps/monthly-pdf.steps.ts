import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { readFile } from 'node:fs/promises';

const { Then } = createBdd();

Then('月次画面から全月PDFを保存し同じ形式を開いて印刷できる', async ({ page, $testInfo: testInfo }) => {
  await expect(page.getByRole('button', { name: 'PDF ダウンロード', exact: true })).toBeVisible();
  const region = page.getByRole('region', { name: '月次明細の PDF' });
  const endpoint = '**/monthly-remunerations/pdf?*';
  await page.route(endpoint, route => route.fulfill({ status: 503,
    contentType: 'application/json', body: JSON.stringify({ error: 'PDF の生成が時間内に完了しませんでした。再試行してください' }) }));
  await region.getByRole('button', { name: 'PDF ダウンロード', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(region.getByRole('alert')).toContainText('再試行してください');
  await page.unroute(endpoint);
  const viewport = page.viewportSize();
  const dark = await page.evaluate(() => document.documentElement.classList.contains('dark'));
  await page.setViewportSize({ width: 390, height: 844 });
  for (const theme of ['light', 'dark']) {
    await page.evaluate(value => document.documentElement.classList.toggle('dark', value === 'dark'), theme);
    const path = testInfo.outputPath(`pdf-error-${theme}-mobile.png`);
    await region.screenshot({ path });
    await testInfo.attach(`PDF再試行・狭幅・${theme}`, { path, contentType: 'image/png' });
  }
  const responsePromise = page.waitForResponse(response => response.url().includes('/monthly-remunerations/pdf'));
  const downloadPromise = page.waitForEvent('download');
  await region.getByRole('button', { name: '再試行', exact: true }).focus();
  await page.keyboard.press('Enter');
  if (viewport) await page.setViewportSize(viewport);
  await page.evaluate(value => document.documentElement.classList.toggle('dark', value), dark);
  const response = await responsePromise;
  expect(response.status(), await response.text()).toBe(200);
  expect(response.headers()['content-type']).toContain('application/pdf');
  expect(response.headers()['cache-control']).toContain('no-store');
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/^monthly-remuneration-\d{4}-\d{2}\.pdf$/);
  const output = testInfo.outputPath(download.suggestedFilename());
  await download.saveAs(output);
  const { getDocument } = await import('pdfjs-dist/legacy/build/pdf.mjs');
  const loading = getDocument({ data: new Uint8Array(await readFile(output)), useSystemFonts: false });
  const pdf = await loading.promise;
  let text = '';
  try {
    for (let index = 1; index <= pdf.numPages; index++) {
      const content = await (await pdf.getPage(index)).getTextContent();
      text += content.items.map(item => 'str' in item ? item.str : '').join('');
    }
  } finally { await loading.destroy(); }
  const compact = text.replace(/\s/g, '');
  expect(compact).toContain('生成日時点の報酬集計');
  expect(compact).toContain('支払済み額ではありません');
  expect(compact).toContain('源氏名:');
  const query = new URL(response.url());
  query.pathname = query.pathname.replace(/\/pdf$/, '');
  query.searchParams.set('size', '2000');
  const headers = await response.request().allHeaders();
  let total = 0;
  let count = 0;
  for (let number = 0; ; number++) {
    query.searchParams.set('page', String(number));
    const statement = await page.request.get(query.toString(), { headers });
    expect(statement.status()).toBe(200);
    const body = await statement.json();
    expect(compact).toContain(`月次報酬合計:${body.total_remuneration.toLocaleString('ja-JP')}円`);
    for (const row of body.orders.content) {
      expect(compact).toContain(row.order_id);
      total += row.accrued_remuneration;
      count++;
    }
    if (body.orders.last) { expect(total).toBe(body.total_remuneration); break; }
  }
  expect(compact).toContain(`全${count}件`);
  await testInfo.attach('月次PDF', { path: output, contentType: 'application/pdf' });
  const popupPromise = page.waitForEvent('popup').then(async popup => {
    if (testInfo.project.use.headless === false) {
      await expect.poll(() => popup.url()).toMatch(/^blob:/);
      await popup.waitForLoadState('load');
      const viewer = popup.frames().find(frame => frame.url().startsWith('chrome-extension://'));
      expect(viewer).toBeDefined();
      await expect(viewer!.getByRole('button', { name: 'Print', exact: true })).toBeVisible();
      const path = testInfo.outputPath('pdf-browser-viewer.png');
      await popup.screenshot({ path });
      await testInfo.attach('ブラウザ PDF ビューア', { path, contentType: 'image/png' });
    } else {
      // headless shell は PDF ビューアを持たないため、同じナビゲーションのダウンロードを検証する。
      const printed = await popup.waitForEvent('download');
      expect(printed.url()).toMatch(/^blob:/);
      expect(await printed.failure()).toBeNull();
    }
    return popup;
  });
  const printResponse = page.waitForResponse(r => r.url().includes('/monthly-remunerations/pdf'));
  await page.getByRole('button', { name: 'PDFを開いて印刷', exact: true }).click();
  const popup = await popupPromise;
  expect((await printResponse).status()).toBe(200);
  await expect(page.getByRole('link', { name: '生成した PDF を開く' })).toHaveAttribute('href', /^blob:/);
  await popup.close();
  for (const theme of ['light', 'dark']) {
    await page.evaluate(value => document.documentElement.classList.toggle('dark', value === 'dark'), theme);
    const path = testInfo.outputPath(`pdf-success-${theme}.png`);
    await region.screenshot({ path });
    await testInfo.attach(`PDF取得後・${theme}`, { path, contentType: 'image/png' });
  }
  await page.evaluate(value => document.documentElement.classList.toggle('dark', value), dark);
});
