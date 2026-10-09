import { expect, type APIRequestContext, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import { Client } from "pg";
import { zipEntry } from "./report-export";
import { PLATFORM_URL } from "../base-url";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  loginPlatformUser,
  loginAsStoreAdmin,
  getAuthorizedStores,
  createPermissionRole,
  createStoreStaffFixture,
} from "./store-api";
const { Given, When, Then } = createBdd();
const base = "/api/store/advertising-costs",
  months = "/api/store/advertising-cost-months";
let token = "",
  reader = "",
  exporter = "",
  foreign = "",
  email = "",
  readerEmail = "",
  password = "",
  month = "",
  current: any,
  original: any;
const h = (t = token, store = STORE1_ID) => ({
  ...STORE_HEADERS,
  "X-Store-ID": store,
  Authorization: `Bearer ${t}`,
});
const values = {
  category: "SALES",
  media_name: "媒体",
  agency_name: null,
  plan_name: "標準",
  inquiry_count: null,
  amount: 100,
};
async function add(
  request: APIRequestContext,
  overrides: Record<string, unknown> = {},
) {
  const r = await request.post(base, {
    headers: h(),
    data: { ...values, month, request_id: randomUUID(), ...overrides },
  });
  expect(r.status(), await r.text()).toBe(201);
  return r.json();
}
async function summary(request: APIRequestContext, m = month) {
  const r = await request.get(`${months}/${m}`, { headers: h() });
  expect(r.status(), await r.text()).toBe(200);
  return r.json();
}
async function selectMonth(page: Page, m: string) {
  await page.getByLabel("対象月", { exact: true }).fill(m);
  await page.getByRole("button", { name: "表示", exact: true }).click();
  await expect(
    page.getByText(`${m} の登録済み費用。`, { exact: false }),
  ).toBeVisible();
}
async function login(page: Page, address = email) {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel("メールアドレス", { exact: true }).fill(address);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/store\/\d+\//);
  await page.goto(`${PLATFORM_URL}/store/${STORE1_ID}/advertising-costs`);
  await expect(page.getByRole("heading", { name: "広告費管理" })).toBeVisible();
  await selectMonth(page, month);
}
Given("広告費検証用の権限と店舗がある", async ({ request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  const owner = await loginPlatformUser(
    request,
    "admin@kizuna.test",
    ADMIN_PASSWORD,
  );
  const manager = await loginAsStoreAdmin(request);
  foreign = String(
    (await getAuthorizedStores(request, manager)).find(
      (s) => String(s.id) !== STORE1_ID,
    )!.id,
  );
  const suffix = randomUUID();
  password = randomUUID();
  month = `${($testInfo.title.includes("ページ") ? 1950 : $testInfo.title.includes("応答") ? 1960 : 1940) - $testInfo.retry}-09`;
  const tokens: string[] = [];
  for (const [index, perms] of [
    [
      "ADVERTISING_COST_VIEW",
      "ADVERTISING_COST_MANAGE",
      "ADVERTISING_COST_EXPORT",
    ],
    ["ADVERTISING_COST_VIEW"],
    ["ADVERTISING_COST_EXPORT"],
  ].entries()) {
    const role = await createPermissionRole(
      request,
      owner,
      `広告費-${index}-${suffix}`,
      ["STORE_VIEW", "STORE_MENU_VIEW", ...perms],
    );
    const address = `advertising-${index}-${suffix}@example.test`;
    if (index === 0) email = address;
    if (index === 1) readerEmail = address;
    await createStoreStaffFixture(
      request,
      manager,
      address,
      password,
      [role],
      [Number(STORE1_ID), Number(foreign)],
    );
    tokens.push(await loginPlatformUser(request, address, password));
  }
  [token, reader, exporter] = tokens;
});
When("広告費を登録変更コピー削除する", async ({ request }) => {
  const key = randomUUID();
  original = await add(request, { request_id: key });
  const updated = {
    ...values,
    category: "RECRUITMENT",
    inquiry_count: 0,
    amount: 300,
    version: original.version,
    reason: "内訳修正",
    request_id: randomUUID(),
  };
  const edit = await request.put(`${base}/${original.id}`, {
    headers: h(),
    data: updated,
  });
  expect(edit.status(), await edit.text()).toBe(200);
  current = await edit.json();
  const replay = await request.post(base, {
    headers: h(),
    data: { ...values, month, request_id: key },
  });
  expect(replay.status()).toBe(201);
  expect(await replay.json()).toEqual(original);
  const stale = await request.put(`${base}/${current.id}`, {
    headers: h(),
    data: { ...updated, request_id: randomUUID() },
  });
  expect(stale.status()).toBe(409);
  const target = month.slice(0, 4) + "-10";
  const copy = {
    source_version: 2,
    target_version: 0,
    reason: "翌月予定",
    request_id: randomUUID(),
  };
  const copies = await Promise.all(
    [0, 1].map(() =>
      request.post(`${months}/${target}/copies`, { headers: h(), data: copy }),
    ),
  );
  for (const c of copies) expect(c.status(), await c.text()).toBe(201);
  expect(await copies[0].json()).toEqual(await copies[1].json());
  const rows = await request.get(base, {
    headers: h(),
    params: { month: target },
  });
  const copied = (await rows.json()).content;
  expect(copied).toHaveLength(1);
  expect(copied[0]).toMatchObject({
    inquiry_count: null,
    amount: 300,
    category: "RECRUITMENT",
  });
  expect(
    (
      await request.post(`${months}/${target}/copies`, {
        headers: h(),
        data: { ...copy, target_version: 1, request_id: randomUUID() },
      })
    ).status(),
  ).toBe(409);
  const removal = {
    version: current.version,
    reason: "重複登録",
    request_id: randomUUID(),
  };
  for (let i = 0; i < 2; i++)
    expect(
      (
        await request.delete(`${base}/${current.id}`, {
          headers: h(),
          data: removal,
        })
      ).status(),
    ).toBe(204);
});
Then("広告費の履歴と冪等性と店舗分離が守られる", async ({ request }) => {
  expect((await summary(request)).recorded_total_amount).toBe(0);
  const history = await request.get(`${months}/${month}/changes`, {
    headers: h(),
    params: { size: 2 },
  });
  expect(history.status(), await history.text()).toBe(200);
  const page = await history.json();
  expect(page.content).toHaveLength(2);
  expect(page.next_cursor).toBeTruthy();
  const detail = await request.get(
    `${months}/${month}/changes/${page.content[0].id}`,
    { headers: h() },
  );
  expect(await detail.json()).toMatchObject({
    reason: "重複登録",
    before: { amount: 300, inquiry_count: 0 },
    after: null,
  });
  expect(
    (await request.get(`${base}/${current.id}`, { headers: h() })).status(),
  ).toBe(404);
  expect(
    (
      await request.get(`${months}/${month}/changes/${page.content[0].id}`, {
        headers: h(token, foreign),
      })
    ).status(),
  ).toBe(404);
  expect(
    (
      await request.get(`${months}/${month}`, { headers: h(token, foreign) })
    ).status(),
  ).toBe(200);
  expect(
    (
      await request.post(base, {
        headers: h(reader),
        data: { ...values, month, request_id: randomUUID() },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.get(`${base}/exports`, {
        headers: h(reader),
        params: { month, format: "csv" },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.get(`${base}/exports`, {
        headers: h(exporter),
        params: { month, format: "csv" },
      })
    ).status(),
  ).toBe(403);
  for (const bad of [
    { category: 0 },
    { amount: "10" },
    { amount: 1.5 },
    { media_name: 3 },
    { month: "0000-01" },
  ])
    expect(
      (
        await request.post(base, {
          headers: h(),
          data: { ...values, month, request_id: randomUUID(), ...bad },
        })
      ).status(),
    ).toBe(400);
});
When("広告費を2001行用意する", async ({ request }) => {
  const seed = await add(request, {
    media_name: "=媒体先頭",
    inquiry_count: 0,
  });
  if (process.env.PGHOST !== "database")
    throw new Error("専用 E2E DB でのみ実行できます");
  const db = new Client({ application_name: "advertising389-bdd" });
  await db.connect();
  try {
    await db.query(
      `INSERT INTO t_advertising_costs(id,store_id,month,category,media_name,agency_name,plan_name,inquiry_count,amount,deleted,revision,version,created_at,updated_at) SELECT ('ad389-' || $2 || '-' || lpad(n::text,4,'0')),store_id,month,category,('=媒体' || lpad(n::text,4,'0')),NULL,NULL,CASE WHEN n%2=0 THEN 0 ELSE NULL END,100,false,0,0,created_at,updated_at FROM t_advertising_costs CROSS JOIN generate_series(1,2000) n WHERE id=$1`,
      [seed.id, randomUUID()],
    );
  } finally {
    await db.end();
  }
});
Then(
  "広告費のCSVとExcelに全行と型と未計測が保存される",
  async ({ request }) => {
    const csv = await request.get(`${base}/exports`, {
      headers: h(),
      params: { month, format: "csv" },
    });
    expect(csv.status(), await csv.text()).toBe(200);
    const bytes = await csv.body();
    expect(bytes.subarray(0, 3).toString("hex")).toBe("efbbbf");
    const text = bytes.toString();
    expect(text).toContain("'=媒体0001");
    expect(text).toContain("'=媒体1000");
    expect(text).toContain("'=媒体2000");
    expect(text.split("\r\n").filter((l) => l.includes("'=媒体"))).toHaveLength(
      2001,
    );
    const xlsx = await request.get(`${base}/exports`, {
      headers: h(),
      params: { month, format: "xlsx" },
    });
    expect(xlsx.status(), await xlsx.text()).toBe(200);
    const book = await xlsx.body();
    expect(zipEntry(book, "xl/workbook.xml")).toContain("広告費明細");
    const sheet = zipEntry(book, "xl/worksheets/sheet2.xml");
    expect(sheet.match(/<row /g)).toHaveLength(2002);
    expect(sheet).toContain("=媒体2000");
    expect(sheet).toContain('t="n"');
    expect(sheet).toContain('t="inlineStr"');
    expect((await summary(request)).recorded_total_amount).toBe(200100);
  },
);
When(
  "広告費画面で取消と二重送信と応答消失を操作する",
  async ({ page, request }) => {
    await login(page);
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .click();
    await page.getByLabel("媒体", { exact: true }).fill("破棄する入力");
    await page.getByRole("button", { name: "閉じる", exact: true }).click();
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .click();
    await expect(page.getByLabel("媒体", { exact: true })).toHaveValue("");
    await page.getByRole("button", { name: "保存する" }).click();
    await expect(page.getByText("媒体を入力してください")).toBeVisible();
    await page.getByLabel("媒体", { exact: true }).fill("応答消失検証");
    await page.getByLabel("金額（円）").fill("100");
    let count = 0;
    const requests: string[] = [];
    await page.route("**/api/store/advertising-costs", async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      count++;
      requests.push(route.request().postData()!);
      await route.fetch();
      await route.abort("failed");
    });
    await page.getByRole("button", { name: "保存する" }).click();
    await expect(
      page.getByText("操作結果をまだ確認できません。", { exact: false }),
    ).toBeVisible();
    await page.getByRole("button", { name: "閉じる", exact: true }).click();
    expect(count).toBe(1);
    expect((await summary(request)).entry_count).toBe(1);
    await page.unroute("**/api/store/advertising-costs");
    await page.reload();
    await selectMonth(page, month);
    await expect(
      page.getByRole("button", { name: "同じ要求で結果を確認" }),
    ).toBeVisible();
    page.on("request", (req) => {
      if (
        req.method() === "POST" &&
        req.url().endsWith("/api/store/advertising-costs")
      )
        requests.push(req.postData()!);
    });
    await page.getByRole("button", { name: "同じ要求で結果を確認" }).click();
    await expect(
      page.getByRole("button", { name: "同じ要求で結果を確認" }),
    ).toBeHidden();
    expect(requests).toHaveLength(2);
    expect(requests[0]).toBe(requests[1]);
    expect((await summary(request)).entry_count).toBe(1);
  },
);
Then(
  "広告費の画面切替と権限とテーマが安全に表示される",
  async ({ page, request, $testInfo }) => {
    await expect(page.getByText("応答消失検証", { exact: true })).toBeVisible();
    let finish!: () => void;
    const pendingSave = new Promise<void>((r) => {
      finish = r;
    });
    await page.route("**/api/store/advertising-costs", async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      await pendingSave;
      const response = await route.fetch();
      await route.fulfill({ response });
    });
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .click();
    await page.getByLabel("媒体", { exact: true }).fill("切替中に保存");
    await page.getByLabel("金額（円）").fill("200");
    await page.getByRole("button", { name: "保存する" }).click();
    await expect(
      page.getByText("送信中です。", { exact: false }),
    ).toBeVisible();
    await page.getByRole("button", { name: "閉じる", exact: true }).click();
    await selectMonth(page, month.slice(0, 4) + "-11");
    await selectMonth(page, month);
    await expect(page.getByText("応答消失検証", { exact: true })).toBeVisible();
    await expect(page.getByText("切替中に保存", { exact: true })).toBeHidden();
    finish();
    await expect(page.getByText("切替中に保存", { exact: true })).toBeVisible();
    await expect(page.getByLabel("登録額合計", { exact: true })).toHaveText(
      "300円",
    );
    await page.unroute("**/api/store/advertising-costs");
    let release!: () => void;
    const gate = new Promise<void>((r) => {
      release = r;
    });
    await page.route("**/api/store/advertising-costs?**", async (route) => {
      const u = new URL(route.request().url());
      if (u.searchParams.get("month") === month) {
        const response = await route.fetch();
        await gate;
        await route.fulfill({ response });
      } else await route.continue();
    });
    const other = month.slice(0, 4) + "-12";
    await selectMonth(page, other);
    await selectMonth(page, month);
    await selectMonth(page, other);
    release();
    await expect(
      page.getByText("この月の広告費は登録されていません"),
    ).toBeVisible();
    await expect(page.getByText("応答消失検証", { exact: true })).toBeHidden();
    await page.unroute("**/api/store/advertising-costs?**");
    await page.goto(`${PLATFORM_URL}/store/${foreign}/advertising-costs`);
    await selectMonth(page, month);
    await expect(page.getByText("応答消失検証", { exact: true })).toBeHidden();
    await page.goto(`${PLATFORM_URL}/store/${STORE1_ID}/advertising-costs`);
    await selectMonth(page, month);
    await expect(page.getByText("応答消失検証", { exact: true })).toBeVisible();
    for (const theme of ["light", "dark"]) {
      await page
        .getByRole("button", { name: "表示モード", exact: true })
        .click();
      await page
        .getByRole("menuitemradio", {
          name: theme === "dark" ? "ダーク" : "ライト",
          exact: true,
        })
        .click();
      await page.screenshot({
        path: $testInfo.outputPath(`advertising-${theme}.png`),
        fullPage: true,
        animations: "disabled",
      });
    }
    await page.setViewportSize({ width: 760, height: 900 });
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("dialog")).toBeVisible();
    await page
      .getByLabel("媒体", { exact: true })
      .fill("非常に長い媒体名".repeat(20));
    await page.getByLabel("金額（円）").fill("0");
    await page.screenshot({
      path: $testInfo.outputPath("advertising-narrow.png"),
      fullPage: true,
      animations: "disabled",
    });
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toBeHidden();
    await login(page, readerEmail);
    await expect(
      page.getByRole("button", { name: "広告費を登録", exact: true }),
    ).toBeHidden();
    await expect(
      page.getByRole("button", { name: "CSV 全件出力" }),
    ).toBeHidden();
    expect(
      (
        await request.get(`${base}/exports`, {
          headers: h(reader),
          params: { month, format: "csv" },
        })
      ).status(),
    ).toBe(403);
  },
);
