import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import {
  ADMIN_PASSWORD,
  SAMPLE_PNG,
  STORE1_ID,
  STORE_HEADERS,
  createPermissionRole,
  createStaffFixture,
  getAuditEvent,
  getStoreConfig,
  listAuditEvents,
  loginUser,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let operator = "",
  limited = "",
  auditor = "",
  operatorId = 0,
  description = "",
  fileName = "",
  uploadedUrl = "",
  originalDescription: string | null = null;

const headers = (token: string) => ({
  ...STORE_HEADERS,
  Authorization: `Bearer ${token}`,
});

Given(
  "プロフィール監査専用の担当と監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    const suffix = randomUUID();
    const password = randomUUID();
    description = `プロフィール監査へ複写しない紹介文-${suffix}`;
    fileName = `監査へ複写しない元ファイル名-${suffix}.png`;
    const owner = await loginUser(request, "admin@kizuna.test", ADMIN_PASSWORD);
    const operatorRole = await createPermissionRole(
      request,
      owner,
      `プロフィール担当-${suffix}`,
      ["STORE_PROFILE_MANAGE"],
    );
    const operatorEmail = `profile-operator-${suffix}@example.test`;
    operatorId = await createStaffFixture(
      request,
      owner,
      operatorEmail,
      password,
      [operatorRole],
    );
    operator = await loginUser(request, operatorEmail, password);
    const limitedRole = await createPermissionRole(
      request,
      owner,
      `プロフィール権限なし-${suffix}`,
      ["SHIFT_MANAGE"],
    );
    const limitedEmail = `profile-limited-${suffix}@example.test`;
    await createStaffFixture(request, owner, limitedEmail, password, [
      limitedRole,
    ]);
    limited = await loginUser(request, limitedEmail, password);
    const auditRole = await createPermissionRole(
      request,
      owner,
      `プロフィール監査-${suffix}`,
      ["AUDIT_VIEW"],
    );
    const auditEmail = `profile-auditor-${suffix}@example.test`;
    await createStaffFixture(request, owner, auditEmail, password, [auditRole]);
    auditor = await loginUser(request, auditEmail, password);
  },
);

When(
  "担当が店舗プロフィールを更新し同値の更新と拒否される操作も試した上でファイルを登録する",
  async ({ request }) => {
    const current = await getStoreConfig(request, operator);
    originalDescription = (current.description as string | null) ?? "";

    const denied = await request.put("/api/config", {
      headers: headers(limited),
      data: { description },
    });
    expect(denied.status()).toBe(403);
    for (let i = 0; i < 2; i++) {
      const response = await request.put("/api/config", {
        headers: headers(operator),
        data: { description },
      });
      expect(response.status(), await response.text()).toBe(200);
    }

    const upload = await request.post("/api/files", {
      headers: headers(operator),
      multipart: {
        file: { name: fileName, mimeType: "image/png", buffer: SAMPLE_PNG },
      },
    });
    expect(upload.status(), await upload.text()).toBe(201);
    uploadedUrl = (await upload.json()).url;
    const anonymous = await request.post("/api/files", {
      headers: STORE_HEADERS,
      multipart: {
        file: { name: fileName, mimeType: "image/png", buffer: SAMPLE_PNG },
      },
    });
    expect([401, 403]).toContain(anonymous.status());
  },
);

Then(
  "実変更の項目名とファイルの白名簿だけが監査され値と元ファイル名は複写されない",
  async ({ request }) => {
    const mine = async (action: string) =>
      (await listAuditEvents(request, auditor, action)).content.filter(
        (event) => event.actor_id === operatorId,
      );
    const profile = await mine("STORE_PROFILE_UPDATED");
    expect(profile).toHaveLength(1);
    expect(profile[0].target_type).toBe("STORE_PROFILE");
    expect(profile[0].target_id).toBe(STORE1_ID);
    expect(String(profile[0].store_id)).toBe(STORE1_ID);
    const profileDetail = await getAuditEvent(request, auditor, profile[0].id);
    expect(profileDetail.after_values.changed_fields).toBe("description");

    const files = await mine("FILE_UPLOADED");
    expect(files).toHaveLength(1);
    expect(files[0].target_type).toBe("STORED_FILE");
    expect(String(files[0].store_id)).toBe(STORE1_ID);
    expect(uploadedUrl.endsWith(files[0].target_id)).toBe(true);
    const fileDetail = await getAuditEvent(request, auditor, files[0].id);
    expect(fileDetail.after_values).toEqual({
      bucket: "public",
      size: String(SAMPLE_PNG.length),
      content_type: "image/png",
    });

    const dump = JSON.stringify([profileDetail, fileDetail, files[0]]);
    expect(dump).not.toContain(description);
    expect(dump).not.toContain(fileName);
  },
);

// 共有店舗の紹介文を検証値のまま残さない。退避に成功した場合だけ戻す。
After({ tags: "@store-profile-file-audit" }, async ({ request }) => {
  if (originalDescription === null) return;
  const response = await request.put("/api/config", {
    headers: headers(operator),
    data: { description: originalDescription },
  });
  expect(response.status(), await response.text()).toBe(200);
  originalDescription = null;
});
