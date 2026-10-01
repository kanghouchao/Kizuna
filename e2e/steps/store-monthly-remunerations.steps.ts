import { expect, type APIRequestContext } from "@playwright/test";
import { PLATFORM_URL } from "../base-url";
import { createBdd } from "playwright-bdd";
import {
  acceptCastInvitation,
  createAgreedOrder,
  completeAgreedOrder,
  correctOrderExtension,
  createCast,
  createCourse,
  issueCastInvitation,
  loginAsStoreAdmin,
  loginViaUiAndEnterStore,
  STORE_HEADERS,
  STORE1_ID,
  getAuthorizedStores,
  getOrder,
  acceptExistingCastInvitation,
  loginPlatformUser,
  withdrawCast,
  invalidateOrder,
  cancelOrder,
} from "./store-api";

const { Given, When, Then } = createBdd();
let name = "";
let month = "";
let completedId = "";

Given("月次明細の本人に完了受注と未完了受注がある", async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const suffix = Date.now().toString();
  name = `月次本人-${suffix}`;
  month = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo" })
    .format(new Date())
    .slice(0, 7);
  const cast = await createCast(request, token, name);
  await acceptCastInvitation(
    request,
    await issueCastInvitation(request, token, cast),
    `monthly-${suffix}@kizuna.test`,
    `Cast-${suffix}-pass`,
    name,
  );
  const course = await createCourse(request, token, "月次コース");
  completedId = await createAgreedOrder(
    request,
    token,
    cast,
    course,
    "月次顧客",
  );
  await completeAgreedOrder(request, token, completedId);
  await createAgreedOrder(request, token, cast, course, "未完了顧客");
  const candidates = await request.get(
    "/api/store/monthly-remunerations/casts",
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
      params: { search: name },
    },
  );
  expect(candidates.status()).toBe(200);
  expect((await candidates.json()).content).toHaveLength(1);
});

When("店長が月次給与明細で本人と対象月を選ぶ", async ({ page }) => {
  const storeId = await loginViaUiAndEnterStore(page);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders`);
  await page.getByRole("link", { name: "月次給与明細", exact: true }).click();
  await page.getByRole("combobox", { name: "キャスト本人" }).click();
  await page.getByPlaceholder("源氏名で検索").fill(name);
  await page.getByRole("option", { name, exact: false }).click();
  await page.getByLabel("対象月").fill(month);
  await page.getByRole("button", { name: "照会", exact: true }).click();
});

Then("月次報酬合計と受注の根拠を確認できる", async ({ page }) => {
  await expect(
    page.getByRole("heading", { name: "月次給与明細", exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("月次報酬合計")).toHaveText("¥7,000");
  const row = page.getByRole("row").filter({ hasText: completedId });
  await expect(row).toContainText("月次コース");
  await expect(row).toContainText("¥7,000");
  await row.getByRole("button", { name: "訂正履歴" }).click();
  await expect(page.getByRole("dialog")).toContainText("訂正履歴はありません");
});

let managerToken = "";
let castToken = "";
let personId = 0;
let originalDate = "";
let originalMonth = "";
let pendingId = "";
let originalOrderId = "";
let orderIds: string[] = [];
let otherStoreId = "";
let statementPages: {
  total_remuneration: number;
  orders: {
    total_elements: number;
    content: {
      order_id: string;
      business_date: string;
      accrued_remuneration: number;
      completion_invalidated: boolean;
    }[];
  };
}[] = [];

async function monthly(request: APIRequestContext, page = 0, size = 20) {
  const response = await request.get("/api/store/monthly-remunerations", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` },
    params: { person_id: personId, month: originalMonth, page, size },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

Given(
  "同じ本人に原月末の受注と再入店後の受注と他店の受注がある",
  async ({ request }) => {
    managerToken = await loginAsStoreAdmin(request);
    const suffix = Date.now().toString();
    const today = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Tokyo",
    }).format(new Date());
    originalDate = new Date(
      Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 1, 0),
    )
      .toISOString()
      .slice(0, 10);
    originalMonth = originalDate.slice(0, 7);
    name = `月次再入店-${suffix}`;
    const email = `monthly-return-${suffix}@kizuna.test`;
    const password = `Cast-${suffix}-pass`;
    const first = await createCast(request, managerToken, `${name}-旧名`);
    await acceptCastInvitation(
      request,
      await issueCastInvitation(request, managerToken, first),
      email,
      password,
      name,
    );
    castToken = await loginPlatformUser(request, email, password);
    const course = await createCourse(request, managerToken, "月末コース");
    originalOrderId = await createAgreedOrder(
      request,
      managerToken,
      first,
      course,
      "原月の顧客",
      { businessDate: originalDate },
    );
    await completeAgreedOrder(request, managerToken, originalOrderId);
    const completed = await getOrder(
      request,
      managerToken,
      STORE1_ID,
      originalOrderId,
    );
    expect(completed.business_date).toBe(originalDate);
    expect(completed.completed_at!.slice(0, 7) > originalMonth).toBeTruthy();
    await withdrawCast(request, managerToken, first);
    const second = await createCast(request, managerToken, `${name}-新名`);
    await acceptExistingCastInvitation(
      request,
      castToken,
      await issueCastInvitation(request, managerToken, second),
    );
    orderIds = [originalOrderId];
    for (let i = 0; i < 20; i++) {
      const id = await createAgreedOrder(
        request,
        managerToken,
        second,
        course,
        "再入店後の顧客",
        { businessDate: originalDate },
      );
      await completeAgreedOrder(request, managerToken, id);
      orderIds.push(id);
    }
    pendingId = await createAgreedOrder(
      request,
      managerToken,
      second,
      course,
      "未完了",
      { businessDate: originalDate },
    );
    const cancelled = await createAgreedOrder(
      request,
      managerToken,
      second,
      course,
      "取消",
      { businessDate: originalDate },
    );
    await cancelOrder(request, managerToken, cancelled, "提供しないため取消");
    const nextMonth = await createAgreedOrder(
      request,
      managerToken,
      second,
      course,
      "翌月",
      { businessDate: today },
    );
    await completeAgreedOrder(request, managerToken, nextMonth);
    otherStoreId = String(
      (await getAuthorizedStores(request, managerToken)).find(
        (store) => String(store.id) !== STORE1_ID,
      )!.id,
    );
    const other = await createCast(
      request,
      managerToken,
      `${name}-他店`,
      otherStoreId,
    );
    await acceptExistingCastInvitation(
      request,
      castToken,
      await issueCastInvitation(request, managerToken, other, otherStoreId),
    );
    const otherCourse = await createCourse(
      request,
      managerToken,
      "他店コース",
      otherStoreId,
    );
    const otherOrder = await createAgreedOrder(
      request,
      managerToken,
      other,
      otherCourse,
      "他店顧客",
      { storeId: otherStoreId, businessDate: originalDate },
    );
    await completeAgreedOrder(request, managerToken, otherOrder, otherStoreId);
    await withdrawCast(request, managerToken, second);
    const candidates = await request.get(
      "/api/store/monthly-remunerations/casts",
      {
        headers: { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` },
        params: { search: `${name}-旧名` },
      },
    );
    expect(candidates.status()).toBe(200);
    const people = (await candidates.json()).content;
    expect(people).toHaveLength(1);
    expect(people[0].name).toBe(`${name}-新名`);
    personId = people[0].person_id;
  },
);

When("原月の給与明細をページに分けて照会する", async ({ request }) => {
  statementPages = [await monthly(request), await monthly(request, 1)];
});

Then(
  "全対象受注だけが重複なく原月の合計に含まれる",
  async ({ request, page }) => {
    for (const result of statementPages) {
      expect(result.total_remuneration).toBe(147000);
      expect(result.orders.total_elements).toBe(21);
    }
    expect(statementPages[0].orders.content).toHaveLength(20);
    expect(statementPages[1].orders.content).toHaveLength(1);
    const rows = statementPages.flatMap((result) => result.orders.content);
    expect(rows.map((row) => row.order_id).sort()).toEqual(
      [...orderIds].sort(),
    );
    expect(
      rows.every((row) => row.business_date === originalDate),
    ).toBeTruthy();
    const storeId = await loginViaUiAndEnterStore(page);
    await page.goto(
      `${PLATFORM_URL}/store/${storeId}/orders/monthly-remunerations`,
    );
    await page.getByRole("combobox", { name: "キャスト本人" }).click();
    await page.getByPlaceholder("源氏名で検索").fill(`${name}-旧名`);
    await page
      .getByRole("option", { name: `${name}-新名`, exact: false })
      .click();
    await page.getByLabel("対象月").fill(originalMonth);
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(page.getByLabel("月次報酬合計")).toHaveText("¥147,000");
    await expect(
      page.getByRole("button", { name: "訂正履歴", exact: true }),
    ).toHaveCount(20);
    await page.getByRole("button", { name: "次へ", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "訂正履歴", exact: true }),
    ).toHaveCount(1);
    await expect(page.getByLabel("月次報酬合計")).toHaveText("¥147,000");
    expect((await monthly(request, 0, 1)).total_remuneration).toBe(147000);
  },
);

When("原月の受注を翌月に完了し訂正して無効化する", async ({ request }) => {
  await completeAgreedOrder(request, managerToken, pendingId);
  expect((await monthly(request)).total_remuneration).toBe(154000);
  await correctOrderExtension(request, managerToken, originalOrderId, "原月の延長記録を訂正");
  expect((await monthly(request)).total_remuneration).toBe(156000);
  await invalidateOrder(
    request,
    managerToken,
    originalOrderId,
    "原月の未提供受注を無効化",
  );
});

Then(
  "再照会で原月の最新額と訂正履歴を確認できる",
  async ({ request, page }) => {
    const result = await monthly(request, 0, 2000);
    expect(result.total_remuneration).toBe(147000);
    expect(result.orders.total_elements).toBe(22);
    expect(
      result.orders.content.find(
        (row: { order_id: string }) => row.order_id === originalOrderId,
      ),
    ).toMatchObject({
      business_date: originalDate,
      accrued_remuneration: 0,
      completion_invalidated: true,
    });
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await expect(
      page.getByText("全 22 件の合計。", { exact: false }),
    ).toBeVisible();
    await page.getByRole("button", { name: "次へ", exact: true }).click();
    const row = page.getByRole("row").filter({ hasText: originalOrderId });
    await expect(row).toContainText("無効化済み（有効報酬 0 円）");
    await row.getByRole("button", { name: "訂正履歴" }).click();
    await expect(page.getByRole("dialog")).toContainText(
      "原月の延長記録を訂正",
    );
    await expect(page.getByRole("dialog")).toContainText(
      "原月の未提供受注を無効化",
    );
    await expect(page.getByRole("dialog")).toContainText("¥7,000 → ¥9,000");
    await expect(page.getByRole("dialog")).toContainText("¥9,000 → ¥0");
  },
);

let staffToken = "";
let foreignPersonId = 0;
let staffPersonId = 0;
Given("店舗スタッフと別店舗だけに在籍する本人がいる", async ({ request }) => {
  managerToken = await loginAsStoreAdmin(request);
  staffToken = await loginPlatformUser(
    request,
    "yamada.jiro@kizuna.test",
    "pass",
  );
  otherStoreId = String(
    (await getAuthorizedStores(request, managerToken)).find(
      (store) => String(store.id) !== STORE1_ID,
    )!.id,
  );
  const suffix = Date.now().toString();
  const cast = await createCast(
    request,
    managerToken,
    `月次他店限定-${suffix}`,
    otherStoreId,
  );
  const email = `monthly-foreign-${suffix}@kizuna.test`;
  const password = `Cast-${suffix}-pass`;
  await acceptCastInvitation(
    request,
    await issueCastInvitation(request, managerToken, cast, otherStoreId),
    email,
    password,
    "他店本人",
  );
  castToken = await loginPlatformUser(request, email, password);
  const candidates = await request.get(
    "/api/store/monthly-remunerations/casts",
    {
      headers: {
        ...STORE_HEADERS,
        "X-Store-ID": otherStoreId,
        Authorization: `Bearer ${managerToken}`,
      },
      params: { search: `月次他店限定-${suffix}` },
    },
  );
  expect(candidates.status()).toBe(200);
  foreignPersonId = (await candidates.json()).content[0].person_id;
  const own = await createCast(
    request,
    managerToken,
    `月次店舗スタッフ対象-${suffix}`,
  );
  await acceptCastInvitation(
    request,
    await issueCastInvitation(request, managerToken, own),
    `monthly-staff-${suffix}@kizuna.test`,
    password,
    "店舗スタッフ対象本人",
  );
  const ownCandidates = await request.get(
    "/api/store/monthly-remunerations/casts",
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${staffToken}` },
      params: { search: `月次店舗スタッフ対象-${suffix}` },
    },
  );
  expect(ownCandidates.status()).toBe(200);
  staffPersonId = (await ownCandidates.json()).content[0].person_id;
});

Then("店舗スタッフは自店の月次給与明細を照会できる", async ({ request }) => {
  const headers = { ...STORE_HEADERS, Authorization: `Bearer ${staffToken}` };
  const candidates = await request.get(
    "/api/store/monthly-remunerations/casts",
    { headers },
  );
  expect(candidates.status()).toBe(200);
  const id = staffPersonId;
  const response = await request.get("/api/store/monthly-remunerations", {
    headers,
    params: { person_id: id, month: "2000-01" },
  });
  expect(response.status()).toBe(200);
  expect(await response.json()).toMatchObject({
    total_remuneration: 0,
    orders: { content: [], total_elements: 0 },
  });
});

Then(
  "他店の本人IDや店舗ヘッダーによる月次明細取得は拒否される",
  async ({ request }) => {
    const params = { person_id: foreignPersonId, month: "2026-09" };
    const headers = { ...STORE_HEADERS, Authorization: `Bearer ${staffToken}` };
    const foreign = await request.get("/api/store/monthly-remunerations", {
      headers,
      params,
    });
    expect(foreign.status()).toBe(404);
    for (const path of ["", "/casts", "/pdf"]) {
      const forged = await request.get(
        `/api/store/monthly-remunerations${path}`,
        { headers: { ...headers, "X-Store-ID": otherStoreId }, params },
      );
      expect(forged.status()).toBe(403);
      const missing = await request.get(
        `/api/store/monthly-remunerations${path}`,
        { headers: { Authorization: `Bearer ${staffToken}` }, params },
      );
      expect(missing.status()).toBe(403);
    }
  },
);

Then(
  "未認証と権限のない本人の照会および不正な月指定は拒否される",
  async ({ request }) => {
    for (const path of ["", "/casts", "/pdf"]) {
      const params = { person_id: foreignPersonId, month: "2026-09" };
      const anonymous = await request.get(
        `/api/store/monthly-remunerations${path}`,
        { headers: STORE_HEADERS, params },
      );
      expect(anonymous.status()).toBe(401);
      const denied = await request.get(
        `/api/store/monthly-remunerations${path}`,
        {
          headers: {
            ...STORE_HEADERS,
            "X-Store-ID": otherStoreId,
            Authorization: `Bearer ${castToken}`,
          },
          params,
        },
      );
      expect(denied.status()).toBe(403);
    }
    for (const invalidMonth of [
      "2026-13",
      "2026-9",
      "0000-01",
      "not-a-month",
    ]) {
      const response = await request.get("/api/store/monthly-remunerations", {
        headers: { ...STORE_HEADERS, Authorization: `Bearer ${staffToken}` },
        params: { person_id: foreignPersonId, month: invalidMonth },
      });
      expect(response.status()).toBe(400);
    }
  },
);
