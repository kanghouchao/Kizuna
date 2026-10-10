import { expect, type APIRequestContext } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  activateEmergencyElevation,
  revokeEmergencyElevation,
  createPermissionRole,
  createPlatformStaffFixture,
  createStoreStaffFixture,
  createUnpublishedCast,
  issueCastInvitation,
  acceptCastInvitation,
  withdrawCast,
  createSpecialService,
  getServiceSetting,
  listCastFieldDefinitions,
  getPlatformMe,
  findPlatformCastUserId,
  listServiceRevisions,
  updateServiceSetting,
  deleteServiceSetting,
  decideOwnServiceCondition,
  getAuditEvent,
  listAuditEvents,
  getAuthorizedStores,
  loginAsStoreAdmin,
  loginPlatformUser,
  type ServiceSettingChange,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let manager = "",
  ordinary = "",
  limited = "",
  auditor = "",
  elevated = "",
  castToken = "",
  secret = "",
  enrollment = "",
  otherStore = "";
let ordinaryId = 0,
  auditorId = 0,
  castId = 0,
  elevationId = 0;
let cases: { id: string; actorId: number; elevated: boolean }[] = [];
const active = new Set<string>();
const actions = [
  "SERVICE_CREATED",
  "SERVICE_UPDATED",
  "SERVICE_DELETED",
  "SERVICE_CONSENT_CHANGED",
];
const settingKeys = [
  "exists",
  "deleted",
  "version",
  "revision_number",
  "terms_version",
];
const consentKeys = [
  "exists",
  "version",
  "revision_number",
  "terms_version",
  "decision",
  "service_id",
  "enrollment_id",
  "service_revision_id",
];
const headers = (token: string, store = STORE1_ID) => ({
  ...STORE_HEADERS,
  "X-Store-ID": store,
  Authorization: `Bearer ${token}`,
});

Given(
  "サービス監査専用の通常担当と本人と昇格セッションを用意する",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    cases = [];
    active.clear();
    enrollment = "";
    elevationId = 0;
    const suffix = randomUUID(),
      password = randomUUID();
    secret = `監査へ複写しない内容-${suffix}`;
    manager = await loginAsStoreAdmin(request);
    const stores = await getAuthorizedStores(request, manager);
    otherStore = String(
      stores.find((store) => String(store.id) !== STORE1_ID)!.id,
    );
    const owner = await loginPlatformUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    const role = await createPermissionRole(
      request,
      owner,
      `サービス担当-${suffix}`,
      ["SERVICE_MANAGE", "STORE_VIEW", "STORE_MENU_VIEW"],
    );
    const email = `service-audit-${suffix}@example.test`;
    ordinaryId = await createStoreStaffFixture(
      request,
      manager,
      email,
      password,
      [role],
      stores.map((store) => store.id),
    );
    ordinary = await loginPlatformUser(request, email, password);
    const limitedRole = await createPermissionRole(
      request,
      owner,
      `サービス閲覧資格-${suffix}`,
      ["CAST_FIELD_DEF_VIEW", "STORE_VIEW", "STORE_MENU_VIEW"],
    );
    const limitedEmail = `service-limited-${suffix}@example.test`;
    await createStoreStaffFixture(
      request,
      manager,
      limitedEmail,
      password,
      [limitedRole],
      [Number(STORE1_ID)],
    );
    limited = await loginPlatformUser(request, limitedEmail, password);
    expect(
      (await getAuthorizedStores(request, limited)).map((store) =>
        String(store.id),
      ),
    ).toContain(STORE1_ID);
    await listCastFieldDefinitions(request, limited);
    const auditRole = await createPermissionRole(
      request,
      owner,
      `サービス監査-${suffix}`,
      [
        "AUDIT_VIEW",
        "EMERGENCY_ELEVATE",
        "PLATFORM_MENU_VIEW",
        "CAST_PERSON_VIEW",
      ],
    );
    const auditEmail = `service-elevation-${suffix}@example.test`;
    auditorId = await createPlatformStaffFixture(
      request,
      owner,
      auditEmail,
      password,
      [auditRole],
    );
    auditor = await loginPlatformUser(request, auditEmail, password);
    const session = await activateEmergencyElevation(
      request,
      auditor,
      STORE1_ID,
      "サービス監査の検証",
      password,
    );
    elevated = session.token;
    elevationId = session.id;
    enrollment = await createUnpublishedCast(request, manager, secret);
    const invitation = await issueCastInvitation(request, manager, enrollment);
    const castEmail = `service-cast-${suffix}@example.test`;
    await acceptCastInvitation(
      request,
      invitation,
      castEmail,
      password,
      `本人-${suffix}`,
    );
    castToken = await loginPlatformUser(request, castEmail, password);
    expect(await getPlatformMe(request, castToken)).toMatchObject({
      email: castEmail,
      user_type: "CAST",
    });
    castId = await findPlatformCastUserId(request, auditor, `本人-${suffix}`);
  },
);

async function auditIds(request: APIRequestContext) {
  const rows = await Promise.all(
    actions.map((action) => listAuditEvents(request, auditor, action)),
  );
  return rows
    .flatMap((row) => row.content.map((event) => event.id))
    .sort((a, b) => a - b);
}

When("設定と本人選択を変更し同値と拒否条件を確認する", async ({ request }) => {
  for (const actor of [
    { token: ordinary, id: ordinaryId, elevated: false },
    { token: elevated, id: auditorId, elevated: true },
  ]) {
    const id = await createSpecialService(request, actor.token, secret);
    active.add(id);
    cases.push({ id, actorId: actor.id, elevated: actor.elevated });
    const original: ServiceSettingChange = {
      name: secret,
      charge_type: "PAID",
      price: 2000,
      remuneration: 1500,
      expected_version: 1,
    };
    const unchanged = await auditIds(request);
    await updateServiceSetting(request, actor.token, id, original);
    expect(await auditIds(request)).toEqual(unchanged);
    expect(
      (
        await decideOwnServiceCondition(
          request,
          castToken,
          id,
          1,
          0,
          "ACCEPTED",
        )
      ).consent_status,
    ).toBe("ACCEPTED");
    const renamed = { ...original, name: `${secret}-変更` };
    expect(
      (await updateServiceSetting(request, actor.token, id, renamed)).version,
    ).toBe(2);
    const same = await auditIds(request);
    await decideOwnServiceCondition(request, castToken, id, 1, 1, "ACCEPTED");
    expect(await auditIds(request)).toEqual(same);
    const changed: ServiceSettingChange = {
      name: renamed.name,
      charge_type: "FREE",
      price: 0,
      remuneration: 0,
      expected_version: 2,
    };
    expect(
      (await updateServiceSetting(request, actor.token, id, changed)).version,
    ).toBe(3);
    const beforeDenials = await auditIds(request);
    expect(
      (
        await request.put(`/api/store/services/${id}`, {
          headers: headers(actor.token),
          data: original,
        })
      ).status(),
    ).toBe(409);
    expect(
      (
        await request.delete(`/api/store/services/${id}`, {
          headers: headers(actor.token),
          params: { expected_version: 1 },
        })
      ).status(),
    ).toBe(409);
    expect(
      (
        await request.put(`/api/store/services/${id}`, {
          headers: headers(actor.token),
          data: { ...changed, price: 1, expected_version: 3 },
        })
      ).status(),
    ).toBe(400);
    expect(
      (
        await request.put(`/api/store/services/${id}`, {
          headers: headers(limited),
          data: { ...changed, expected_version: 3 },
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await request.delete(`/api/store/services/${id}`, {
          headers: headers(limited),
          params: { expected_version: 3 },
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await request.post("/api/store/services", {
          headers: headers(limited),
          data: {
            kind: "SPECIAL_SERVICE",
            name: secret,
            charge_type: "PAID",
            price: 2000,
            remuneration: 1500,
          },
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await request.put(`/api/store/services/${id}`, {
          headers: headers(ordinary, otherStore),
          data: { ...changed, expected_version: 3 },
        })
      ).status(),
    ).toBe(404);
    expect(
      (
        await request.delete(`/api/store/services/${id}`, {
          headers: headers(ordinary, otherStore),
          params: { expected_version: 3 },
        })
      ).status(),
    ).toBe(404);
    const consentPath = `/api/platform/me/service-conditions/${id}/consent`;
    const consent = {
      terms_version: 2,
      consent_version: 1,
      decision: "ACCEPTED",
    };
    expect(
      (
        await request.put(consentPath, {
          headers: { Authorization: `Bearer ${castToken}` },
          params: { store_id: STORE1_ID },
          data: { ...consent, terms_version: 1 },
        })
      ).status(),
    ).toBe(409);
    expect(
      (
        await request.put(consentPath, {
          headers: { Authorization: `Bearer ${castToken}` },
          params: { store_id: STORE1_ID },
          data: { ...consent, consent_version: 0 },
        })
      ).status(),
    ).toBe(409);
    expect(
      (
        await request.put(consentPath, {
          headers: { Authorization: `Bearer ${ordinary}` },
          params: { store_id: STORE1_ID },
          data: consent,
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await request.put(consentPath, {
          headers: { Authorization: `Bearer ${castToken}` },
          params: { store_id: otherStore },
          data: consent,
        })
      ).status(),
    ).toBe(404);
    expect(await auditIds(request)).toEqual(beforeDenials);
    expect(
      (
        await decideOwnServiceCondition(
          request,
          castToken,
          id,
          2,
          1,
          "ACCEPTED",
        )
      ).consent_version,
    ).toBe(2);
    expect(
      (
        await decideOwnServiceCondition(
          request,
          castToken,
          id,
          2,
          2,
          "REJECTED",
        )
      ).consent_status,
    ).toBe("REJECTED");
    const rejected = await auditIds(request);
    await decideOwnServiceCondition(request, castToken, id, 2, 3, "REJECTED");
    expect(await auditIds(request)).toEqual(rejected);
    await deleteServiceSetting(request, actor.token, id, 3);
    active.delete(id);
    expect(await getServiceSetting(request, actor.token, id)).toMatchObject({
      deleted: true,
      version: 4,
    });
    const deleted = await auditIds(request);
    expect(
      (
        await request.delete(`/api/store/services/${id}`, {
          headers: headers(actor.token),
          params: { expected_version: 4 },
        })
      ).status(),
    ).toBe(400);
    expect(
      (
        await request.put(consentPath, {
          headers: { Authorization: `Bearer ${castToken}` },
          params: { store_id: STORE1_ID },
          data: { ...consent, consent_version: 3 },
        })
      ).status(),
    ).toBe(404);
    expect(await auditIds(request)).toEqual(deleted);
  }
});

Then(
  "共通監査には実主体と版本参照と安全な変更摘要だけが残る",
  async ({ request }) => {
    for (const item of cases) {
      let settingEvents = 0;
      for (const action of actions.slice(0, 3)) {
        const rows = (
          await listAuditEvents(request, auditor, action)
        ).content.filter((row) => row.target_id === item.id);
        expect(rows).toHaveLength(action === "SERVICE_UPDATED" ? 2 : 1);
        for (const row of rows) {
          const detail = await getAuditEvent(request, auditor, row.id);
          expect(row).toMatchObject({
            actor_id: item.actorId,
            actor_type: "STAFF",
            store_id: Number(STORE1_ID),
            target_type: "SERVICE",
            source_type: "SERVICE_REVISION",
            result: "SUCCEEDED",
          });
          const keys = [
            ...settingKeys,
            ...(action === "SERVICE_UPDATED"
              ? ["redacted_fields_changed"]
              : []),
            ...(item.elevated ? ["emergency_elevation_id"] : []),
          ];
          expect(Object.keys(detail.after_values).sort()).toEqual(keys.sort());
          expect(Object.keys(detail.before_values).sort()).toEqual(
            action === "SERVICE_CREATED" ? [] : [...settingKeys].sort(),
          );
          const revision = Number(detail.after_values.revision_number);
          expect(detail.after_values.version).toBe(String(revision - 1));
          expect(detail.after_values.exists).toBe("true");
          expect(detail.after_values.deleted).toBe(
            String(action === "SERVICE_DELETED"),
          );
          expect(detail.after_values.terms_version).toBe(
            revision >= 3 ? "2" : "1",
          );
          if (action === "SERVICE_UPDATED")
            expect(detail.after_values.redacted_fields_changed).toBe(
              revision === 2 ? "name" : "charge_type,price,remuneration",
            );
          if (item.elevated)
            expect(detail.after_values.emergency_elevation_id).toBe(
              String(elevationId),
            );
          const history = await listServiceRevisions(
            request,
            ordinary,
            item.id,
          );
          expect(
            history.content.some((entry) => entry.id === row.source_id),
          ).toBe(true);
          expect(
            JSON.stringify(detail.before_values) +
              JSON.stringify(detail.after_values),
          ).not.toContain(secret);
          settingEvents++;
        }
      }
      expect(settingEvents).toBe(4);
      const consentRows = (
        await listAuditEvents(request, auditor, "SERVICE_CONSENT_CHANGED")
      ).content;
      const matching = [];
      for (const row of consentRows) {
        const detail = await getAuditEvent(request, auditor, row.id);
        if (detail.after_values.service_id !== item.id) continue;
        matching.push(detail);
        expect(row).toMatchObject({
          actor_id: castId,
          actor_type: "CAST",
          store_id: Number(STORE1_ID),
          target_type: "SERVICE_CONSENT",
          source_type: "SERVICE_CONSENT_EVENT",
          result: "SUCCEEDED",
        });
        expect(Object.keys(detail.after_values).sort()).toEqual(
          [...consentKeys].sort(),
        );
        expect(detail.after_values.enrollment_id).toBe(enrollment);
        expect(detail.after_values.service_revision_id).toBeTruthy();
        expect(row.source_id).toBeTruthy();
        const revision = Number(detail.after_values.revision_number);
        expect(detail.after_values.version).toBe(String(revision - 1));
        expect(detail.after_values.decision).toBe(
          revision === 3 ? "REJECTED" : "ACCEPTED",
        );
        expect(Object.keys(detail.before_values).sort()).toEqual(
          revision === 1 ? [] : [...consentKeys].sort(),
        );
        expect(
          JSON.stringify(detail.before_values) +
            JSON.stringify(detail.after_values),
        ).not.toContain(secret);
      }
      expect(matching).toHaveLength(3);
      expect(
        new Set(matching.map((detail) => detail.event.target_id)).size,
      ).toBe(1);
      expect(
        new Set(matching.map((detail) => detail.event.source_id)).size,
      ).toBe(3);
    }
  },
);

After({ tags: "@service-audit" }, async ({ request }) => {
  for (const id of active) {
    const current = await getServiceSetting(request, manager, id);
    if (!current.deleted)
      await deleteServiceSetting(request, manager, id, current.version);
  }
  active.clear();
  if (enrollment) await withdrawCast(request, manager, enrollment);
  if (elevationId)
    await revokeEmergencyElevation(request, auditor, elevationId);
});
