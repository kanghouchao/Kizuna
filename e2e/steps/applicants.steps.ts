import { expect, type APIRequestContext } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import { Client } from "pg";
import { PLATFORM_URL } from "../base-url";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  createPermissionRole,
  loginAsStoreAdmin,
} from "./store-api";

const { Given, When, Then } = createBdd();
let token = "";
let managerToken = "";
let applicantId = "";
let applicantName = "";
let storeId = STORE1_ID;
const intake = () => ({
  name: applicantName,
  channel: "PHONE",
  source_type: "MEDIA",
  source_media: "検証媒体",
  referrer: null,
  assignee: "検証担当",
  phone: "09000000000",
  email: "applicant@example.invalid",
  address: "非公開住所",
  experience: "検証経歴",
  desired_conditions: "検証希望",
});
const headers = () => ({
  ...STORE_HEADERS,
  "X-Store-ID": storeId,
  Authorization: `Bearer ${token}`,
});
const path = () => `/api/store/applicants/${applicantId}`;
async function current(request: APIRequestContext) {
  const result = await request.get(path(), { headers: headers() });
  expect(result.status()).toBe(200);
  return result.json();
}

Given(
  "検証専用の採用担当者でログインしている",
  async ({ page, request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    const suffix = randomUUID();
    applicantName = `応募検証-${suffix}`;
    managerToken = await loginAsStoreAdmin(request);
    const ownerLogin = await request.post("/api/platform/login", {
      data: { email: "admin@kizuna.test", password: ADMIN_PASSWORD },
    });
    expect(ownerLogin.status()).toBe(200);
    const ownerToken = (await ownerLogin.json()).token;
    const roleId = await createPermissionRole(
      request,
      ownerToken,
      `採用検証-${suffix}`,
      [
        "RECRUITMENT_VIEW",
        "RECRUITMENT_MANAGE",
        "RECRUITMENT_DECIDE",
        "STORE_VIEW",
        "STORE_MENU_VIEW",
      ],
    );
    const email = `recruitment-${suffix}@example.invalid`;
    const password = randomUUID();
    const created = await request.post("/api/store/staff-members", {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` },
      data: {
        email,
        password,
        display_name: "採用検証担当",
        role_ids: [roleId],
        store_scope_type: "SPECIFIC_STORES",
        store_ids: [Number(STORE1_ID)],
      },
    });
    expect(created.status(), await created.text()).toBe(201);
    const login = await request.post("/api/platform/login", {
      data: { email, password },
    });
    expect(login.status()).toBe(200);
    token = (await login.json()).token;
    await page.goto(`${PLATFORM_URL}/platform/login`);
    await page.getByLabel("メールアドレス", { exact: true }).fill(email);
    await page.getByLabel("パスワード", { exact: true }).fill(password);
    await page.getByRole("button", { name: "ログイン", exact: true }).click();
    await expect(page).toHaveURL(/\/store\/\d+\//, { timeout: 20000 });
    storeId = new URL(page.url()).pathname.match(/\/store\/(\d+)/)![1];
  },
);

When("応募者の受付と面接を画面で登録する", async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/applicants`);
  await page.getByRole("link", { name: "応募者を登録" }).click();
  await page.getByLabel("氏名", { exact: true }).fill(applicantName);
  await page.getByLabel("電話番号", { exact: true }).fill("09000000000");
  await page.getByLabel("住所", { exact: true }).fill("非公開住所");
  await page
    .getByLabel("希望条件", { exact: true })
    .fill("長い希望条件".repeat(40));
  await page.getByRole("button", { name: "受付情報を保存" }).click();
  await expect(
    page.getByRole("heading", { name: applicantName, exact: true }),
  ).toBeVisible();
  applicantId = new URL(page.url()).pathname.split("/").at(-1)!;
  await page.getByRole("button", { name: "受付情報を編集" }).click();
  await page.getByLabel("担当者名（記録用）").fill("採用担当者".repeat(15));
  await page.getByRole("button", { name: "受付情報を保存" }).click();
  await expect(
    page.getByRole("button", { name: "受付情報を編集" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "面接記録を編集" }).click();
  await page.getByLabel("面接日時（端末の時刻）").fill("2026-10-01T10:30");
  await page.getByLabel("面接担当者名（記録用）").fill("面接担当者");
  await page.getByLabel("面接メモ", { exact: true }).fill("非公開面接メモ");
  await page.getByRole("button", { name: "確認項目を追加" }).click();
  await page.getByLabel("確認項目1の名前").fill("希望条件を確認");
  await page.getByLabel("確認項目1の確認済み").check();
  await page.getByRole("button", { name: "面接記録を保存" }).click();
  await expect(page.getByText("非公開面接メモ", { exact: true })).toBeVisible();
});

Then(
  "応募者の一覧と詳細は公開範囲を区別し両テーマと狭幅で操作できる",
  async ({ page, request, $testInfo }) => {
    const result = await request.get("/api/store/applicants", {
      headers: headers(),
      params: { search: applicantName },
    });
    expect(result.status()).toBe(200);
    const rows = (await result.json()).content;
    expect(rows).toHaveLength(1);
    for (const key of [
      "phone",
      "email",
      "address",
      "interview",
      "experience",
      "desired_conditions",
      "referrer",
    ])
      expect(rows[0]).not.toHaveProperty(key);
    await expect(
      page.getByRole("button", { name: "採否を確定", exact: true }),
    ).toBeDisabled();
    for (const theme of ["light", "dark"]) {
      await page.evaluate((value) => {
        localStorage.setItem("theme", value);
        document.documentElement.classList.toggle("dark", value === "dark");
      }, theme);
      await page.setViewportSize({ width: 1280, height: 960 });
      await page.screenshot({
        path: $testInfo.outputPath(`applicant-detail-${theme}.png`),
        fullPage: true,
      });
      await page.goto(`${PLATFORM_URL}/store/${storeId}/applicants`);
      await expect(
        page.getByRole("heading", { name: "応募者管理", exact: true }),
      ).toBeVisible();
      await expect(page.getByText("非公開住所")).toHaveCount(0);
      await page.screenshot({
        path: $testInfo.outputPath(`applicant-list-${theme}.png`),
        fullPage: true,
      });
      await page.goto(
        `${PLATFORM_URL}/store/${storeId}/applicants/${applicantId}`,
      );
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(
      page.getByRole("button", { name: "受付情報を編集" }),
    ).toBeEnabled();
    await page.getByRole("button", { name: "受付情報を編集" }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByLabel("氏名", { exact: true })).toBeVisible();
    await page.screenshot({
      path: $testInfo.outputPath("applicant-narrow.png"),
      fullPage: true,
    });
    await page.getByRole("button", { name: "編集を閉じる" }).click();
    await page.setViewportSize({ width: 1280, height: 960 });
  },
);

When("応募者の選考を進めて本人の辞退を記録する", async ({ page }) => {
  for (const [state, reason] of [
    ["選考中", "選考を開始"],
    ["面接済", "面接を実施"],
    ["辞退", "本人の希望で応募撤回"],
  ]) {
    await page.getByLabel("変更先", { exact: true }).click();
    await page.getByRole("option", { name: state, exact: true }).click();
    await page.getByLabel("変更理由", { exact: true }).fill(reason);
    await page
      .getByRole("button", { name: "選考状態を変更", exact: true })
      .click();
    if (state === "辞退")
      await page
        .getByRole("button", { name: "辞退を記録", exact: true })
        .click();
    await expect(page.getByText(reason, { exact: true })).toBeVisible();
  }
});

Then(
  "理由付き履歴が残り終了済みの応募者は変更できない",
  async ({ page, request }) => {
    await expect(
      page.getByRole("button", { name: "受付情報を編集" }),
    ).toHaveCount(0);
    const detail = await current(request);
    expect(detail.status).toBe("WITHDRAWN");
    const response = await request.post(`${path()}/transitions`, {
      headers: headers(),
      data: {
        version: detail.version,
        status: "SCREENING",
        reason: "再開不可",
      },
    });
    expect(response.status()).toBe(409);
    const history = await request.get(`${path()}/history`, {
      headers: headers(),
    });
    expect(
      (await history.json()).content.map(
        (row: { new_status: string }) => row.new_status,
      ),
    ).toEqual(["WITHDRAWN", "INTERVIEWED", "SCREENING", "RECEIVED"]);
  },
);

Then(
  "応募者APIは店舗外アクセスと通常キャスト権限を拒否する",
  async ({ request }) => {
    const created = await request.post("/api/store/applicants", {
      headers: headers(),
      data: intake(),
    });
    expect(created.status(), await created.text()).toBe(201);
    applicantId = (await created.json()).id;
    const denied = await request.get(path(), {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` },
    });
    expect(denied.status()).toBe(403);
    const outsideStore = await request.get("/api/platform/stores/me", {
      headers: { Authorization: `Bearer ${managerToken}` },
    });
    expect(outsideStore.status()).toBe(200);
    const catalog = await outsideStore.json();
    const otherStore = (
      Array.isArray(catalog) ? catalog : catalog.content
    ).find((store: { id: number }) => String(store.id) !== storeId);
    expect(otherStore).toBeTruthy();
    const forbiddenStore = await request.get(path(), {
      headers: { ...headers(), "X-Store-ID": String(otherStore.id) },
    });
    expect(forbiddenStore.status()).toBe(403);
    const db = new Client();
    await db.connect();
    try {
      const insert = await db.query(
        "INSERT INTO t_applicants (id,store_id,name,channel,source_type,source_media,referrer,status,created_at,updated_at,version,modified_by,edit_sequence) SELECT $1,$2,$3,channel,source_type,source_media,referrer,status,created_at,updated_at,version,modified_by,edit_sequence FROM t_applicants WHERE id=$4 RETURNING id",
        [`outside-${randomUUID()}`, otherStore.id, "別店舗応募者", applicantId],
      );
      const foreignId = insert.rows[0].id;
      for (const suffix of ["", "/history"])
        expect(
          (
            await request.get(`/api/store/applicants/${foreignId}${suffix}`, {
              headers: headers(),
            })
          ).status(),
        ).toBe(404);
      expect(
        (
          await request.put(`/api/store/applicants/${foreignId}`, {
            headers: headers(),
            data: { version: 0, intake: intake() },
          })
        ).status(),
      ).toBe(404);
    } finally {
      await db.end();
    }
  },
);

Then(
  "応募者APIは旧版と同時変更を拒否し未設定の採否を確定しない",
  async ({ request }) => {
    const original = await current(request);
    const update = await request.put(path(), {
      headers: headers(),
      data: { version: original.version, intake: intake() },
    });
    expect(update.status()).toBe(200);
    const edited = await update.json();
    expect(edited.version).toBe(original.version + 1);
    expect(
      (
        await request.put(path(), {
          headers: headers(),
          data: { version: original.version, intake: intake() },
        })
      ).status(),
    ).toBe(409);
    const data = {
      version: edited.version,
      status: "SCREENING",
      reason: "同時変更検証",
    };
    const concurrent = await Promise.all([
      request.post(`${path()}/transitions`, { headers: headers(), data }),
      request.post(`${path()}/transitions`, { headers: headers(), data }),
    ]);
    expect(concurrent.map((value) => value.status()).sort()).toEqual([
      200, 409,
    ]);
    const detail = await current(request);
    for (const status of ["HIRED", "REJECTED"]) {
      const body = { version: detail.version, status, reason: "未設定検証" };
      expect(
        (
          await request.post(`${path()}/decision`, {
            headers: headers(),
            data: body,
          })
        ).status(),
      ).toBe(409);
      expect(
        (
          await request.post(`${path()}/transitions`, {
            headers: headers(),
            data: body,
          })
        ).status(),
      ).toBe(400);
    }
    const history = await request.get(`${path()}/history`, {
      headers: headers(),
      params: { size: 1 },
    });
    const first = await history.json();
    expect(first.content).toHaveLength(1);
    expect(first.next_cursor).toBeTruthy();
    const next = await request.get(`${path()}/history`, {
      headers: headers(),
      params: { size: 1, cursor: first.next_cursor },
    });
    expect((await next.json()).content[0].new_status).toBe("RECEIVED");
  },
);

Then(
  "応募履歴の保存が失敗すると応募者登録もロールバックされる",
  async ({ request }) => {
    const db = new Client();
    await db.connect();
    const name = `rollback-${randomUUID()}`;
    try {
      await db.query(
        "CREATE FUNCTION recruitment_test_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic history failure'; END $$",
      );
      await db.query(
        "CREATE TRIGGER recruitment_test_failure BEFORE INSERT ON t_applicant_status_histories FOR EACH ROW EXECUTE FUNCTION recruitment_test_failure()",
      );
      const failure = await request.post("/api/store/applicants", {
        headers: headers(),
        data: { ...intake(), name },
      });
      expect(failure.status()).toBe(500);
      expect(
        (
          await db.query("SELECT count(*) FROM t_applicants WHERE name=$1", [
            name,
          ])
        ).rows[0].count,
      ).toBe("0");
    } finally {
      await db.query(
        "DROP TRIGGER IF EXISTS recruitment_test_failure ON t_applicant_status_histories",
      );
      await db.query("DROP FUNCTION IF EXISTS recruitment_test_failure()");
      await db.end();
    }
  },
);
