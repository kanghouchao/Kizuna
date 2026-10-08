import { expect, type APIRequestContext } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { PLATFORM_URL } from "../base-url";
import {
  getSelfDailyRemunerations,
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
  loginPlatformUser,
  STORE1_ID,
  suspendCast,
  withdrawCast,
} from "./store-api";

const { Given, When, Then } = createBdd();
const base = "/api/platform/me/monthly-remunerations";
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

async function monthly(
  request: APIRequestContext,
  params: Record<string, string | number> = {},
  actor = token,
) {
  const response = await request.get(base, {
    headers: { Authorization: `Bearer ${actor}` },
    params: { store_id: STORE1_ID, month, ...params },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
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
    token = await loginPlatformUser(request, email, password);
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
      token,
      await issueCastInvitation(request, manager, second),
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
      token,
      await issueCastInvitation(request, manager, other, secondStore),
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
    otherToken = await loginPlatformUser(request, otherEmail, password);
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
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel("メールアドレス").fill(email);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/cast\/schedule/);
  await page.getByRole("link", { name: "報酬明細", exact: true }).click();
  await page.getByRole("link", { name: "月次給与明細", exact: true }).click();
  await page.getByRole("combobox", { name: "店舗", exact: true }).click();
  await page.getByRole("option", { name: storeName, exact: true }).click();
  await page.getByLabel("対象月").fill(month);
  await page.getByRole("button", { name: "照会", exact: true }).click();
});

Then(
  "本人の同店の完了受注だけが月合計と全ページに含まれる",
  async ({ request, page }) => {
    await expect(page.getByLabel("月次報酬合計")).toHaveText("¥14,000");
    await expect(
      page.getByRole("button", { name: "詳細・変更履歴を確認" }),
    ).toHaveCount(2);
    const pages = [
      await monthly(request, { size: 1 }),
      await monthly(request, { page: 1, size: 1 }),
    ];
    expect(
      pages.flatMap((p) =>
        p.orders.content.map((r: { order_id: string }) => r.order_id),
      ),
    ).toEqual([secondOrder, firstOrder]);
    for (const p of pages) {
      expect(p.total_remuneration).toBe(initialTotal);
      expect(p.orders.total_elements).toBe(2);
      expect(p.orders.content[0].business_date).toBe(oldDate);
    }
    expect(
      (await monthly(request, { store_id: secondStore })).total_remuneration,
    ).toBe(7000);
    const beyond = await monthly(request, { page: 7, size: 1 });
    expect(beyond.orders.content).toEqual([]);
    expect(beyond.total_remuneration).toBe(initialTotal);
    expect(beyond.orders.total_elements).toBe(2);
    for (const emptyMonth of ["0001-01", "9999-12"]) {
      const empty = await monthly(request, { month: emptyMonth });
      expect(empty.total_remuneration).toBe(0);
      expect(empty.orders.content).toEqual([]);
    }
  },
);

When(
  "本人の原月の受注を翌月に完了し訂正して無効化する",
  async ({ request }) => {
    await completeAgreedOrder(request, manager, pendingOrder);
    expect((await monthly(request)).total_remuneration).toBe(21000);
    await correctOrderExtension(
      request,
      manager,
      firstOrder,
      "本人の原月の延長を訂正",
    );
    expect((await monthly(request)).total_remuneration).toBe(23000);
    await invalidateOrder(
      request,
      manager,
      firstOrder,
      "本人の原月の未提供を無効化",
    );
    const original = await getOrder(request, manager, STORE1_ID, firstOrder);
    expect(original.business_date).toBe(oldDate);
    expect(original.completed_at!.slice(0, 7) > month).toBe(true);
  },
);

Then(
  "本人は月合計を再照会し退店後の詳細と変更履歴を辿れる",
  async ({ request, page }) => {
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(page.getByLabel("月次報酬合計")).toHaveText("¥14,000");
    await expect(
      page.getByText("全 3 件の合計。", { exact: false }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "詳細・変更履歴を確認" })
      .last()
      .click();
    await expect(page.getByText("無効化（有効報酬 0 円）")).toBeVisible();
    await page.getByRole("button", { name: "変更履歴を確認" }).click();
    await expect(page.getByText("本人の原月の延長を訂正")).toBeVisible();
    await expect(page.getByText("本人の原月の未提供を無効化")).toBeVisible();
    await page.getByRole("button", { name: "一覧へ戻る" }).click();
    await expect(page.getByLabel("対象月")).toHaveValue(month);
    await expect(page.getByLabel("月次報酬合計")).toHaveText("¥14,000");
    const history = await request.get(
      `/api/platform/me/remunerations/${firstOrder}/changes`,
      { headers: { Authorization: `Bearer ${token}` }, params: { size: 1 } },
    );
    expect(history.status()).toBe(200);
    const firstPage = await history.json();
    expect(firstPage.content[0].change_type).toBe("COMPLETION_INVALIDATION");
    const next = await request.get(
      `/api/platform/me/remunerations/${firstOrder}/changes`,
      {
        headers: { Authorization: `Bearer ${token}` },
        params: { cursor: firstPage.next_cursor, size: 1 },
      },
    );
    expect(next.status()).toBe(200);
    expect((await next.json()).content[0].change_type).toBe("CORRECTION");
    const wrong = await request.get(
      `/api/platform/me/remunerations/${secondOrder}/changes`,
      {
        headers: { Authorization: `Bearer ${token}` },
        params: { cursor: firstPage.next_cursor },
      },
    );
    expect(wrong.status()).toBe(400);
  },
);

Then(
  "本人の月次画面は取得失敗から再試行でき狭い画面でも操作できる",
  async ({ page }) => {
    await page.route(
      "**/api/platform/me/monthly-remunerations?*",
      (route) =>
        route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ error: "月次照会を実行できませんでした" }),
        }),
      { times: 1 },
    );
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(page.getByRole("main").getByRole("alert")).toContainText(
      "月次給与明細を取得できませんでした",
    );
    await expect(page.getByLabel("月次報酬合計")).toHaveCount(0);
    await page.getByRole("button", { name: /再試行/ }).click();
    await expect(page.getByLabel("月次報酬合計")).toHaveText("¥14,000");
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByLabel("対象月").focus();
    await page.keyboard.press("Tab");
    await expect(
      page.getByRole("button", { name: "照会", exact: true }),
    ).toBeFocused();
    for (const theme of ["light", "dark"]) {
      await page.evaluate((mode) => {
        document.documentElement.classList.toggle("dark", mode === "dark");
      }, theme);
      const foreground = await page
        .locator("body")
        .evaluate((element) => getComputedStyle(element).color);
      await expect(page.getByLabel("対象月")).toHaveCSS("color", foreground);
      await expect(
        page.getByRole("combobox", { name: "店舗", exact: true }),
      ).toHaveCSS("color", foreground);
      await page.screenshot({
        path: `test-results/cast-monthly-${theme}.png`,
        fullPage: true,
        animations: "disabled",
      });
    }
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
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
    for (const suffix of ["", "/stores"]) {
      const params = { store_id: STORE1_ID, month };
      expect((await request.get(`${base}${suffix}`, { params })).status()).toBe(
        401,
      );
      expect(
        (
          await request.get(`${base}${suffix}`, {
            params,
            headers: { Authorization: `Bearer ${manager}` },
          })
        ).status(),
      ).toBe(403);
      expect(
        (
          await request.get(`${base}${suffix}`, {
            params: { ...params, page: 2147483647 },
            headers,
          })
        ).status(),
      ).toBe(400);
    }
    for (const params of [
      { month: "0000-01" },
      { month: "2026-13" },
      { month: "2026-9" },
      { store_id: 0 },
      { page: -1 },
    ] as Record<string, string | number>[]) {
      expect(
        (
          await request.get(base, {
            params: { store_id: STORE1_ID, month, ...params },
            headers,
          })
        ).status(),
      ).toBe(400);
    }
    expect(
      (
        await request.get(base, { params: { store_id: STORE1_ID }, headers })
      ).status(),
    ).toBe(400);
    expect(
      (
        await request.get(base, {
          params: { store_id: secondStore, month },
          headers: { Authorization: `Bearer ${otherToken}` },
        })
      ).status(),
    ).toBe(404);
    expect(
      (
        await request.get(base, {
          params: { store_id: 9223372036854775807n.toString(), month },
          headers,
        })
      ).status(),
    ).toBe(404);
    const forged = await monthly(request, {
      person_id: 999999,
      cast_id: otherEnrollment,
      enrollment_id: otherEnrollment,
    });
    expect(
      forged.orders.content.map((r: { order_id: string }) => r.order_id),
    ).toEqual([secondOrder, firstOrder]);
    const filtered = await request.get("/api/platform/me/remunerations", {
      params: { enrollment_id: otherEnrollment },
      headers,
    });
    expect(filtered.status()).toBe(404);
    for (const suffix of ["", "/changes"])
      expect(
        (
          await request.get(
            `/api/platform/me/remunerations/${otherOrder}${suffix}`,
            { headers },
          )
        ).status(),
      ).toBe(404);
    for (const size of [0, -3, 3000]) {
      const result = await monthly(request, { size });
      expect(result.orders.size).toBe(size > 2000 ? 2000 : 1);
      expect(result.total_remuneration).toBe(initialTotal);
    }
  },
);

Then(
  "月次明細と本人詳細と変更履歴は専用の応答項目だけを返す",
  async ({ request }) => {
    const result = await monthly(request);
    expect(Object.keys(result).sort()).toEqual(
      [
        "store_id",
        "store_name",
        "month",
        "total_remuneration",
        "orders",
      ].sort(),
    );
    for (const row of result.orders.content)
      expect(Object.keys(row).sort()).toEqual(
        [
          "order_id",
          "business_date",
          "service_summary",
          "accrued_remuneration",
          "completion_invalidated",
        ].sort(),
      );
    await correctOrderExtension(
      request,
      manager,
      firstOrder,
      "本人向け変更根拠",
    );
    for (const suffix of ["", "/changes"]) {
      const response = await request.get(
        `/api/platform/me/remunerations/${firstOrder}${suffix}`,
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
  "本人の日別照会は歴史在籍と専用項目を守り月別へ戻れる",
  async ({ request, page, $testInfo: testInfo }) => {
    const headers = { Authorization: `Bearer ${token}` };
    const params = { store_id: STORE1_ID, business_date: oldDate };
    const response = await getSelfDailyRemunerations(request, {
      headers,
      params: { ...params, size: 1 },
    });
    expect(response.status()).toBe(200);
    const result = await response.json();
    expect(result.total_remuneration).toBe(initialTotal);
    expect(Object.keys(result).sort()).toEqual([
      "business_date",
      "orders",
      "store_id",
      "store_name",
      "total_remuneration",
    ]);
    expect(Object.keys(result.orders.content[0]).sort()).toEqual([
      "accrued_remuneration",
      "business_date",
      "completion_invalidated",
      "order_id",
      "service_summary",
    ]);
    expect(JSON.stringify(result)).not.toContain(privateName);
    const suspended = await getSelfDailyRemunerations(request, {
      headers,
      params: { ...params, store_id: secondStore },
    });
    expect(suspended.status()).toBe(200);
    expect((await suspended.json()).total_remuneration).toBe(7000);
    const foreign = await getSelfDailyRemunerations(request, {
      headers: { Authorization: `Bearer ${otherToken}` },
      params,
    });
    expect(
      (await foreign.json()).orders.content.map(
        (r: { order_id: string }) => r.order_id,
      ),
    ).not.toContain(firstOrder);
    await page.getByRole("button", { name: "日別", exact: true }).click();
    await page.getByLabel("営業日", { exact: true }).fill(oldDate);
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(page.getByLabel("日別報酬合計")).toHaveText(
      `¥${initialTotal.toLocaleString("ja-JP")}`,
    );
    await expect(
      page.getByRole("button", { name: "PDF ダウンロード" }),
    ).toHaveCount(0);
    await page
      .getByRole("button", { name: "詳細・変更履歴を確認" })
      .first()
      .click();
    await page
      .getByRole("button", { name: "変更履歴を確認", exact: true })
      .click();
    await expect(page.getByText("変更履歴はありません")).toBeVisible();
    await page.getByRole("button", { name: "一覧へ戻る", exact: true }).click();
    await correctOrderExtension(
      request,
      manager,
      firstOrder,
      "本人の日別延長を訂正",
    );
    expect(
      (
        await (
          await getSelfDailyRemunerations(request, { headers, params })
        ).json()
      ).total_remuneration,
    ).toBe(initialTotal + 2000);
    await invalidateOrder(
      request,
      manager,
      firstOrder,
      "本人の日別未提供を無効化",
    );
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(page.getByLabel("日別報酬合計")).toHaveText(
      `¥${(initialTotal - 7000).toLocaleString("ja-JP")}`,
    );
    await page.route("**/api/platform/me/daily-remunerations?*", (route) =>
      route.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({ error: "一時的に取得できません" }),
      }),
    );
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(
      page.getByText("日別給与明細を取得できませんでした。"),
    ).toBeVisible();
    await expect(page.getByLabel("日別報酬合計")).toHaveCount(0);
    await page.unroute("**/api/platform/me/daily-remunerations?*");
    await page.getByRole("button", { name: "再試行", exact: true }).click();
    await expect(page.getByLabel("日別報酬合計")).toBeVisible();
    await page.setViewportSize({ width: 390, height: 844 });
    for (const theme of ["light", "dark"]) {
      await page.evaluate((theme) => {
        document.documentElement.classList.remove("light", "dark");
        document.documentElement.classList.add(theme);
        document.documentElement.style.colorScheme = theme;
      }, theme);
      const path = testInfo.outputPath(`daily-self-${theme}.png`);
      await page.screenshot({ path, fullPage: true, animations: "disabled" });
      await testInfo.attach(`日別本人-${theme}`, {
        path,
        contentType: "image/png",
      });
    }
    await page.getByRole("button", { name: "月別", exact: true }).click();
    await expect(page.getByLabel("日別報酬合計")).toHaveCount(0);
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "PDF ダウンロード" }),
    ).toBeVisible();
  },
);
