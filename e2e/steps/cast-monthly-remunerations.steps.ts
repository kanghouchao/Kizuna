import { expect, type APIRequestContext, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { APP_URL } from "../base-url";
import {
  acceptCastInvitation,
  acceptExistingCastInvitation,
  cancelOrder,
  completeAgreedOrder,
  correctOrderExtension,
  createAgreedOrder,
  createCast,
  createCourse,
  getAuthorizedStores,
  getOrder,
  invalidateOrder,
  issueCastInvitation,
  loginAsStoreAdmin,
  loginUser,
  STORE1_ID,
  suspendCast,
  withdrawCast,
  openLoginForm,
  monthInTokyo,
} from "./store-api";

const { Given, When, Then } = createBdd();
const base = "/api/me/monthly-remunerations";
const privateName = "本人月次へ返してはいけない顧客";
let manager = "";
let token = "";
let otherToken = "";
let email = "";
let password = "";
let month = "";
let oldDate = "";
let firstOrder = "";
let secondOrder = "";
let pendingOrder = "";
let otherOrder = "";
let otherEnrollment = "";
let secondStore = "";
let storeName = "";
let secondStoreName = "";
let initialTotal = 14000;

const WEEKDAYS = "日月火水木金土";
/** ポータルの日付の書式「9/30（火）」（今年でなければ「2025/9/30（火）」）。 */
function dayLabel(date: string): string {
  const [y, m, d] = date.split("-").map(Number);
  const thisYear = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo" })
    .format(new Date())
    .slice(0, 4);
  const weekday = WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()];
  return `${String(y) === thisYear ? "" : `${y}/`}${m}/${d}（${weekday}）`;
}

const summary = (page: Page) =>
  page.getByRole("region", { name: `${Number(month.slice(5, 7))}月の合計` });
const oldDay = (page: Page) =>
  page.getByRole("region", { name: `${dayLabel(oldDate)}の明細` });

/** 開いた直後の月次明細（今月・最初の店舗）から、退店店舗の原月へ移る。 */
async function openOldMonth(page: Page) {
  await page.getByRole("button", { name: storeName, exact: true }).click();
  await page.getByRole("button", { name: "前の月", exact: true }).click();
  await expect(summary(page)).toBeVisible();
}

/** 退店店舗の原月の明細が出ていること。URL に月を持つので、詳細から戻っても選び直さずに出る。 */
async function expectOldMonth(page: Page) {
  await expect(page).toHaveURL(
    new RegExp(`/cast/remunerations/monthly\\?.*month=${month}`),
  );
  await expect(
    page.getByRole("button", { name: storeName, exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await expect(summary(page)).toBeVisible();
}

async function loginAsSelf(page: Page) {
  await openLoginForm(page);
  await page.getByLabel("メールアドレス").fill(email);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/cast\/?$/);
}

/** 月を一度動かして戻し、原月の明細を取り直す。 */
async function refetchMonth(page: Page) {
  await page.getByRole("button", { name: "次の月", exact: true }).click();
  await page.getByRole("button", { name: "前の月", exact: true }).click();
  await expect(summary(page)).toBeVisible();
}

/** 本人の月次明細の受注報酬の合計（保証の不足分とボーナスを含まない order_total）。 */
async function orderTotal(request: APIRequestContext, storeId = STORE1_ID) {
  const response = await request.get("/api/me/remuneration-statements", {
    headers: { Authorization: `Bearer ${token}` },
    params: { store_id: storeId, month },
  });
  const body = await response.text();
  expect(response.status(), body).toBe(200);
  expect(body).not.toContain(privateName);
  return JSON.parse(body).order_total;
}

Given(
  "月次照会の本人に退店と再入店の受注および停止中の他店在籍がある",
  async ({ request }) => {
    manager = await loginAsStoreAdmin(request);
    const suffix = Date.now().toString();
    email = `self-monthly-${suffix}@kizuna.test`;
    password = `Cast-${suffix}-pass`;
    const today = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Tokyo",
    }).format(new Date());
    oldDate = new Date(
      Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 1, 0),
    )
      .toISOString()
      .slice(0, 10);
    month = oldDate.slice(0, 7);
    initialTotal = 14000;
    const stores = await getAuthorizedStores(request, manager);
    storeName = stores.find((s) => String(s.id) === STORE1_ID)!.name;
    const otherStore = stores.find((s) => String(s.id) !== STORE1_ID)!;
    secondStore = String(otherStore.id);
    secondStoreName = otherStore.name;
    const first = await createCast(request, manager, `本人月次旧名-${suffix}`);
    await acceptCastInvitation(
      request,
      await issueCastInvitation(request, manager, first),
      email,
      password,
      "月次本人",
    );
    token = await loginUser(request, email, password);
    const course = await createCourse(
      request,
      manager,
      "本人月次の長いサービス名で折り返しを確認する特別コース",
    );
    firstOrder = await createAgreedOrder(
      request,
      manager,
      first,
      course,
      privateName,
      { businessDate: oldDate },
    );
    await completeAgreedOrder(request, manager, firstOrder);
    await withdrawCast(request, manager, first);
    const second = await createCast(request, manager, `本人月次新名-${suffix}`);
    await acceptExistingCastInvitation(
      request,
      await issueCastInvitation(request, manager, second),
      email,
      password,
    );
    secondOrder = await createAgreedOrder(
      request,
      manager,
      second,
      course,
      privateName,
      { businessDate: oldDate },
    );
    await completeAgreedOrder(request, manager, secondOrder);
    pendingOrder = await createAgreedOrder(
      request,
      manager,
      second,
      course,
      privateName,
      { businessDate: oldDate },
    );
    const cancelled = await createAgreedOrder(
      request,
      manager,
      second,
      course,
      privateName,
      { businessDate: oldDate },
    );
    await cancelOrder(request, manager, cancelled, "提供しないため取消");
    const nextMonth = await createAgreedOrder(
      request,
      manager,
      second,
      course,
      privateName,
      { businessDate: today },
    );
    await completeAgreedOrder(request, manager, nextMonth);
    await withdrawCast(request, manager, second);
    const other = await createCast(
      request,
      manager,
      `本人月次他店-${suffix}`,
      secondStore,
    );
    await acceptExistingCastInvitation(
      request,
      await issueCastInvitation(request, manager, other, secondStore),
      email,
      password,
    );
    const otherCourse = await createCourse(
      request,
      manager,
      "他店の本人コース",
      secondStore,
    );
    const foreignOrder = await createAgreedOrder(
      request,
      manager,
      other,
      otherCourse,
      privateName,
      { storeId: secondStore, businessDate: oldDate },
    );
    await completeAgreedOrder(request, manager, foreignOrder, secondStore);
    await suspendCast(request, manager, other, secondStore);
    otherEnrollment = await createCast(request, manager, `月次別人-${suffix}`);
    const otherEmail = `other-monthly-${suffix}@kizuna.test`;
    await acceptCastInvitation(
      request,
      await issueCastInvitation(request, manager, otherEnrollment),
      otherEmail,
      password,
      "別人",
    );
    otherToken = await loginUser(request, otherEmail, password);
    otherOrder = await createAgreedOrder(
      request,
      manager,
      otherEnrollment,
      course,
      "別人の顧客",
      { businessDate: oldDate },
    );
    await completeAgreedOrder(request, manager, otherOrder);
  },
);

When("本人が月次給与明細で退店店舗と原月を選ぶ", async ({ page }) => {
  await loginAsSelf(page);
  await page
    .getByRole("navigation", { name: "メニュー" })
    .getByRole("link", { name: "報酬", exact: true })
    .click();
  await page
    .getByRole("navigation", { name: "報酬の切替" })
    .getByRole("link", { name: "月次", exact: true })
    .click();
  await openOldMonth(page);
});

When(
  "本人がホームのやることから退店店舗の原月の承諾待ちを開く",
  async ({ page }) => {
    await loginAsSelf(page);
    // 先月の承諾待ちでも、その店舗とその月の明細を開く
    const todo = page
      .getByRole("list", { name: "やること" })
      .getByRole("link", { name: new RegExp(`^${dayLabel(oldDate)}の報酬`) })
      .filter({ has: page.getByText(storeName, { exact: true }) });
    await expect(todo).toHaveAttribute(
      "href",
      `/cast/remunerations/monthly?store_id=${STORE1_ID}&month=${month}`,
    );
    await todo.click();
    await expectOldMonth(page);
    await expect(oldDay(page)).toBeVisible();
  },
);

Then(
  "本人の同店の完了受注だけが月合計と日ごとの明細に含まれる",
  async ({ request, page }) => {
    await expect(summary(page)).toContainText("受注報酬¥14,000");
    await expect(page.getByRole("table")).toHaveCount(0);
    // 承諾待ちの日は最初から開き、受注番号を出さずにサービス名と金額を出す
    await expect(oldDay(page).getByRole("link")).toHaveCount(2);
    await expect(oldDay(page)).not.toContainText(firstOrder);
    await expect(oldDay(page)).toContainText(
      "本人月次の長いサービス名で折り返しを確認する特別コース",
    );
    expect(await orderTotal(request)).toBe(initialTotal);
    expect(await orderTotal(request, secondStore)).toBe(7000);
  },
);

Then("本人は原営業日を日付を名乗るボタンで承諾できる", async ({ page }) => {
  const accept = oldDay(page).getByRole("button");
  await expect(accept).toHaveCount(1);
  await expect(accept).toHaveText(`${dayLabel(oldDate)}を承諾する`);
  await accept.click();
  await expect(
    page.getByRole("button", { name: new RegExp(`^${dayLabel(oldDate)}`) }).first(),
  ).toContainText("承諾済み");
  await expect(summary(page)).toContainText("承諾 1 / 1 日");
});

When(
  "本人の原月の受注を翌月に完了し訂正して無効化する",
  async ({ request }) => {
    await completeAgreedOrder(request, manager, pendingOrder);
    expect(await orderTotal(request)).toBe(21000);
    await correctOrderExtension(
      request,
      manager,
      firstOrder,
      "本人の原月の延長を訂正",
    );
    expect(await orderTotal(request)).toBe(23000);
    await invalidateOrder(
      request,
      manager,
      firstOrder,
      "本人の原月の未提供を無効化",
    );
    const original = await getOrder(request, manager, STORE1_ID, firstOrder);
    expect(original.business_date).toBe(oldDate);
    expect(monthInTokyo(original.completed_at!) > month).toBe(true);
  },
);

Then(
  "本人は月合計を再照会し退店後の詳細と変更履歴を辿れる",
  async ({ request, page }) => {
    await refetchMonth(page);
    await expect(summary(page)).toContainText("受注報酬¥14,000");
    // 承諾した後に変わった日は先頭で開き、変更前後の差と再承諾のボタンを出す
    await expect(oldDay(page)).toContainText("承諾した後に変わりました");
    await expect(oldDay(page).getByRole("link")).toHaveCount(3);
    await expect(
      oldDay(page).getByRole("button", {
        name: `${dayLabel(oldDate)}を再承諾する`,
        exact: true,
      }),
    ).toBeVisible();
    await oldDay(page)
      .getByRole("link")
      .filter({ hasText: "（無効化）" })
      .click();
    await expect(page).toHaveURL(new RegExp(`/cast/remunerations/${firstOrder}$`));
    await expect(page.getByRole("region", { name: "概要" })).toContainText(
      "無効化",
    );
    await expect(page.getByText("本人の原月の延長を訂正")).toBeVisible();
    await expect(page.getByText("本人の原月の未提供を無効化")).toBeVisible();
    await page.goBack();
    await expectOldMonth(page);
    const history = await request.get(
      `/api/me/remunerations/${firstOrder}/changes`,
      { headers: { Authorization: `Bearer ${token}` }, params: { size: 1 } },
    );
    expect(history.status()).toBe(200);
    const firstPage = await history.json();
    expect(firstPage.content[0].change_type).toBe("COMPLETION_INVALIDATION");
    expect(firstPage.total_pages).toBeGreaterThan(1);
    const next = await request.get(
      `/api/me/remunerations/${firstOrder}/changes`,
      {
        headers: { Authorization: `Bearer ${token}` },
        params: { page: 1, size: 1 },
      },
    );
    expect(next.status()).toBe(200);
    expect((await next.json()).content[0].change_type).toBe("CORRECTION");
    // 履歴は名指した受注の分だけで、別の受注の変更は混ざらない。
    const other = await request.get(
      `/api/me/remunerations/${secondOrder}/changes`,
      { headers: { Authorization: `Bearer ${token}` }, params: { size: 100 } },
    );
    expect(other.status()).toBe(200);
    const otherChanges = (await other.json()).content as Array<{ order_id: string }>;
    for (const change of otherChanges) expect(change.order_id).toBe(secondOrder);
  },
);

Then(
  "本人の月次画面は取得失敗から再試行でき狭い画面でも操作できる",
  async ({ page }) => {
    await page.route(
      "**/api/me/remuneration-statements?*",
      (route) =>
        route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "明細を取得できませんでした" }),
        }),
      { times: 1 },
    );
    await page.getByRole("button", { name: "次の月", exact: true }).click();
    await expect(page.getByRole("main").getByRole("alert")).toContainText(
      "月次明細の取得に失敗しました",
    );
    await page.getByRole("button", { name: /再試行/ }).click();
    await page.getByRole("button", { name: "前の月", exact: true }).click();
    await expect(summary(page)).toContainText("受注報酬¥14,000");
    for (const width of [375, 430]) {
      await page.setViewportSize({ width, height: 844 });
      const header = page
        .getByRole("button", { name: new RegExp(`^${dayLabel(oldDate)}`) })
        .first();
      await header.focus();
      await page.keyboard.press("Enter");
      await expect(header).toHaveAttribute("aria-expanded", "false");
      await page.keyboard.press("Enter");
      await expect(header).toHaveAttribute("aria-expanded", "true");
      for (const theme of ["light", "dark"]) {
        await page.evaluate((mode) => {
          document.documentElement.classList.toggle("dark", mode === "dark");
        }, theme);
        await page.screenshot({
          path: `test-results/cast-monthly-${width}-${theme}.png`,
          fullPage: true,
          animations: "disabled",
        });
      }
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= window.innerWidth,
        ),
      ).toBe(true);
    }
  },
);

Then("本人の店舗候補は重複せず退店と停止の両方を含む", async ({ request }) => {
  const first = await request.get(`${base}/stores`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { size: 1 },
  });
  const second = await request.get(`${base}/stores`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { size: 1, page: 1 },
  });
  expect(first.status()).toBe(200);
  expect(second.status()).toBe(200);
  const pages = [await first.json(), await second.json()];
  expect(pages[0].total_elements).toBe(2);
  expect(pages.flatMap((p) => p.content)).toEqual(
    [
      { store_id: Number(STORE1_ID), store_name: storeName },
      { store_id: Number(secondStore), store_name: secondStoreName },
    ].sort((a, b) => a.store_id - b.store_id),
  );
});

Then(
  "本人の月次照会は不正入力と他人の識別子による越権を拒否する",
  async ({ request }) => {
    const headers = { Authorization: `Bearer ${token}` };
    const pdfParams = { store_id: STORE1_ID, month };
    expect((await request.get(`${base}/pdf`, { params: pdfParams })).status()).toBe(401);
    expect((await request.get(`${base}/pdf`, { params: pdfParams, headers: { Authorization: `Bearer ${manager}` } })).status()).toBe(403);
    expect((await request.get(`${base}/pdf`, { params: { store_id: secondStore, month }, headers: { Authorization: `Bearer ${otherToken}` } })).status()).toBe(404);
    const stores = `${base}/stores`;
    expect((await request.get(stores)).status()).toBe(401);
    expect(
      (
        await request.get(stores, {
          headers: { Authorization: `Bearer ${manager}` },
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await request.get(stores, { params: { page: 2147483647 }, headers })
      ).status(),
    ).toBe(400);
    // 本人の月次一覧と日別の口は無い。本人でも経路が無いので 404 になる
    for (const [path, params] of [
      [base, { store_id: STORE1_ID, month }],
      ["/api/me/daily-remunerations", { store_id: STORE1_ID, business_date: oldDate }],
    ] as const)
      expect((await request.get(path, { params, headers })).status()).toBe(404);
    const foreign = await request.get("/api/me/remuneration-statements", {
      params: { store_id: STORE1_ID, month },
      headers: { Authorization: `Bearer ${otherToken}` },
    });
    expect(await foreign.text()).not.toContain(firstOrder);
    const filtered = await request.get("/api/me/remunerations", {
      params: { enrollment_id: otherEnrollment },
      headers,
    });
    expect(filtered.status()).toBe(404);
    for (const suffix of ["", "/changes"])
      expect(
        (
          await request.get(
            `/api/me/remunerations/${otherOrder}${suffix}`,
            { headers },
          )
        ).status(),
      ).toBe(404);
  },
);

Then(
  "本人詳細と変更履歴は専用の応答項目だけを返す",
  async ({ request }) => {
    await correctOrderExtension(
      request,
      manager,
      firstOrder,
      "本人向け変更根拠",
    );
    for (const suffix of ["", "/changes"]) {
      const response = await request.get(
        `/api/me/remunerations/${firstOrder}${suffix}`,
        { headers: { Authorization: `Bearer ${token}` } },
      );
      expect(response.status()).toBe(200);
      expect(await response.text()).not.toMatch(
        /customer|contact|remarks|corrected_by|receptionist|actor|本人月次へ返してはいけない顧客/,
      );
    }
  },
);

Then(
  "本人の月次明細の日ごとに退店後の原営業日の受注と訂正後の合計が出る",
  async ({ request, page, $testInfo: testInfo }) => {
    const day = oldDay(page);
    await expect(day.getByRole("link")).toHaveCount(2);
    await expect(day).toContainText("受注 2件");
    await expect(day).toContainText(
      `この日の合計¥${initialTotal.toLocaleString("ja-JP")}`,
    );
    await day.getByRole("link").first().click();
    await expect(page.getByText("変更履歴はありません")).toBeVisible();
    await page.goBack();
    await expectOldMonth(page);
    await correctOrderExtension(
      request,
      manager,
      firstOrder,
      "本人の日別延長を訂正",
    );
    await refetchMonth(page);
    await expect(oldDay(page)).toContainText(
      `この日の合計¥${(initialTotal + 2000).toLocaleString("ja-JP")}`,
    );
    await invalidateOrder(
      request,
      manager,
      firstOrder,
      "本人の日別未提供を無効化",
    );
    await refetchMonth(page);
    await expect(oldDay(page)).toContainText(
      `この日の合計¥${(initialTotal - 7000).toLocaleString("ja-JP")}`,
    );
    await expect(
      oldDay(page).getByRole("link").filter({ hasText: "（無効化）" }),
    ).toHaveCount(1);
    await page.route("**/api/me/remuneration-statements?*", (route) =>
      route.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({ error: "一時的に取得できません" }),
      }),
    );
    await page.getByRole("button", { name: "次の月", exact: true }).click();
    await expect(page.getByText("月次明細の取得に失敗しました")).toBeVisible();
    await page.unroute("**/api/me/remuneration-statements?*");
    await page.getByRole("button", { name: "再試行", exact: true }).click();
    await page.getByRole("button", { name: "前の月", exact: true }).click();
    await expect(oldDay(page)).toBeVisible();
    await page.setViewportSize({ width: 375, height: 812 });
    for (const theme of ["light", "dark"]) {
      await page.evaluate((theme) => {
        document.documentElement.classList.remove("light", "dark");
        document.documentElement.classList.add(theme);
        document.documentElement.style.colorScheme = theme;
      }, theme);
      const path = testInfo.outputPath(`daily-self-${theme}.png`);
      await page.screenshot({ path, fullPage: true, animations: "disabled" });
      await testInfo.attach(`日ごとの明細-${theme}`, {
        path,
        contentType: "image/png",
      });
    }
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  },
);
