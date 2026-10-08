import { expect } from "@playwright/test";
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
  renameCast,
  setCastPublication,
  suspendCast,
  resumeCast,
  withdrawCast,
  castStatusHistory,
  getAuditEvent,
  listAuditEvents,
  getAuthorizedStores,
  listShifts,
  loginAsStoreAdmin,
  loginPlatformUser,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let ordinary = "",
  limited = "",
  auditor = "",
  elevated = "",
  secret = "",
  otherStore = "";
let ordinaryId = 0,
  auditorId = 0,
  elevationId = 0;
let cases: {
  id: string;
  actorId: number;
  elevated: boolean;
  histories: { id: string; actor_id: number; new_status: string }[];
}[] = [];
const headers = (token: string, storeId = STORE1_ID) => ({
  ...STORE_HEADERS,
  "X-Store-ID": storeId,
  Authorization: `Bearer ${token}`,
});
const enrollmentKeys = [
  "exists",
  "cast_id",
  "status",
  "ended_at",
  "enrollment_version",
  "profile_id",
  "profile_version",
  "publication_status",
];
const lifecycleKeys = ["exists", "cast_id", "status", "ended_at", "version"];
const profileKeys = ["enrollment_id", "version", "publication_status"];
const actions = [
  "CAST_ENROLLMENT_CREATED",
  "CAST_ENROLLMENT_UPDATED",
  "CAST_PROFILE_PUBLICATION_CHANGED",
  "CAST_ENROLLMENT_SUSPENDED",
  "CAST_ENROLLMENT_RESUMED",
  "CAST_ENROLLMENT_WITHDRAWN",
];

Given(
  "キャスト監査専用の担当と昇格セッションが用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    cases = [];
    elevationId = 0;
    const suffix = randomUUID();
    secret = `監査へ複写しない秘密-${suffix}`;
    const manager = await loginAsStoreAdmin(request);
    const stores = await getAuthorizedStores(request, manager);
    const other = stores.find((store) => String(store.id) !== STORE1_ID);
    expect(other).toBeDefined();
    otherStore = String(other!.id);
    const owner = await loginPlatformUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    const password = randomUUID();
    const ordinaryRole = await createPermissionRole(
      request,
      owner,
      `在籍担当-${suffix}`,
      ["CAST_MANAGE", "STORE_VIEW", "STORE_MENU_VIEW"],
    );
    const ordinaryEmail = `cast-audit-${suffix}@example.test`;
    ordinaryId = await createStoreStaffFixture(
      request,
      manager,
      ordinaryEmail,
      password,
      [ordinaryRole],
      stores.map((store) => store.id),
    );
    ordinary = await loginPlatformUser(request, ordinaryEmail, password);
    const limitedRole = await createPermissionRole(
      request,
      owner,
      `勤務担当-${suffix}`,
      ["SHIFT_MANAGE", "STORE_VIEW", "STORE_MENU_VIEW"],
    );
    const limitedEmail = `cast-limited-${suffix}@example.test`;
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
    const date = new Date().toISOString().slice(0, 10);
    await listShifts(request, limited, date, date);
    const auditRole = await createPermissionRole(
      request,
      owner,
      `在籍監査-${suffix}`,
      ["AUDIT_VIEW", "EMERGENCY_ELEVATE", "PLATFORM_MENU_VIEW"],
    );
    const auditEmail = `cast-elevation-${suffix}@example.test`;
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
      "在籍監査の検証",
      password,
    );
    elevated = session.token;
    elevationId = session.id;
  },
);

When(
  "通常と昇格の担当がキャストの六操作と拒否操作を実行する",
  async ({ request }) => {
    for (const [token, actorId, isElevated] of [
      [ordinary, ordinaryId, false],
      [elevated, auditorId, true],
    ] as const) {
      const id = await createUnpublishedCast(request, token, secret);
      await renameCast(request, token, id, secret);
      await setCastPublication(request, token, id, "UNPUBLISHED");
      const mutations = [
        { path: "/api/store/casts", method: "POST", data: { name: secret } },
        {
          path: `/api/store/casts/${id}`,
          method: "PUT",
          data: { name: secret },
        },
        {
          path: `/api/store/casts/${id}/publication`,
          method: "PATCH",
          data: { publication_status: "PUBLISHED" },
        },
        { path: `/api/store/casts/${id}/suspension`, method: "POST" },
        { path: `/api/store/casts/${id}/resumption`, method: "POST" },
        { path: `/api/store/casts/${id}/withdrawal`, method: "POST" },
      ];
      for (const mutation of mutations) {
        const denied = await request.fetch(mutation.path, {
          method: mutation.method,
          data: mutation.data,
          headers: headers(limited),
        });
        expect(denied.status(), await denied.text()).toBe(403);
      }
      for (const mutation of mutations.slice(1)) {
        const hidden = await request.fetch(mutation.path, {
          method: mutation.method,
          data: mutation.data,
          headers: headers(token, otherStore),
        });
        expect(hidden.status(), await hidden.text()).toBe(
          isElevated ? 403 : 404,
        );
      }
      await renameCast(request, token, id, `${secret}-更新`);
      await setCastPublication(request, token, id, "PUBLISHED");
      await setCastPublication(request, token, id, "PUBLISHED");
      await suspendCast(request, token, id);
      const repeatedSuspend = await request.post(
        `/api/store/casts/${id}/suspension`,
        { headers: headers(token) },
      );
      expect(repeatedSuspend.status()).toBe(400);
      await resumeCast(request, token, id);
      const repeatedResume = await request.post(
        `/api/store/casts/${id}/resumption`,
        { headers: headers(token) },
      );
      expect(repeatedResume.status()).toBe(400);
      await withdrawCast(request, token, id);
      const repeatedWithdrawal = await request.post(
        `/api/store/casts/${id}/withdrawal`,
        { headers: headers(token) },
      );
      expect(repeatedWithdrawal.status()).toBe(400);
      const histories = (await castStatusHistory(request, token, id)).content;
      expect(histories).toHaveLength(4);
      expect(histories.every((row) => row.actor_id === actorId)).toBe(true);
      cases.push({ id, actorId, elevated: isElevated, histories });
    }
  },
);

Then(
  "キャストの実変更だけが安全な前後値と正本参照で監査される",
  async ({ request }) => {
    for (const item of cases) {
      let profileId = "";
      for (const [index, action] of actions.entries()) {
        const events = (
          await listAuditEvents(request, auditor, action)
        ).content.filter((event) =>
          action === "CAST_PROFILE_PUBLICATION_CHANGED"
            ? event.source_id === item.id
            : event.target_id === item.id,
        );
        expect(events).toHaveLength(1);
        const event = events[0];
        expect(event.actor_id).toBe(item.actorId);
        expect(event.actor_type).toBe("STAFF");
        expect(event.store_id).toBe(Number(STORE1_ID));
        expect(event.result).toBe("SUCCEEDED");
        const detail = await getAuditEvent(request, auditor, event.id);
        const before = detail.before_values,
          after = detail.after_values;
        const keys =
          index < 2
            ? enrollmentKeys
            : index === 2
              ? profileKeys
              : lifecycleKeys;
        expect(Object.keys(after).sort()).toEqual(
          [
            ...keys,
            ...(index === 1 ? ["redacted_fields_changed"] : []),
            ...(item.elevated ? ["emergency_elevation_id"] : []),
          ].sort(),
        );
        expect(Object.keys(before).sort()).toEqual(
          index === 0 ? [] : [...keys].sort(),
        );
        expect(after.emergency_elevation_id).toBe(
          item.elevated ? String(elevationId) : undefined,
        );
        expect(event.target_type).toBe(
          index === 2 ? "CAST_PROFILE" : "CAST_ENROLLMENT",
        );
        if (index === 0) {
          profileId = after.profile_id;
          expect(profileId).toBeTruthy();
          expect(after.enrollment_version).toBe("0");
          expect(after.profile_version).toBe("0");
          expect(after.status).toBe("ENROLLED");
          expect(after.publication_status).toBe("UNPUBLISHED");
          expect(event.source_type).toBeUndefined();
          expect(event.source_id).toBeUndefined();
        } else if (index === 1) {
          expect(before.profile_id).toBe(profileId);
          expect(after.profile_id).toBe(profileId);
          expect(before.enrollment_version).toBe("0");
          expect(after.enrollment_version).toBe("0");
          expect(before.profile_version).toBe("0");
          expect(after.profile_version).toBe("1");
          expect(after.redacted_fields_changed).toBe("name");
          expect(event.source_type).toBeUndefined();
          expect(event.source_id).toBeUndefined();
        } else if (index === 2) {
          expect(event.target_id).toBe(profileId);
          expect(event.source_type).toBe("CAST_ENROLLMENT");
          expect(event.source_id).toBe(item.id);
          expect(after.enrollment_id).toBe(item.id);
          expect(before.version).toBe("1");
          expect(after.version).toBe("2");
          expect(before.publication_status).toBe("UNPUBLISHED");
          expect(after.publication_status).toBe("PUBLISHED");
        } else {
          expect(event.source_type).toBe("CAST_ENROLLMENT_STATUS_HISTORY");
          const history = item.histories.find(
            (row) => row.id === event.source_id,
          );
          expect(history).toBeDefined();
          expect(history!.new_status).toBe(after.status);
          expect(before.version).toBe(String(index - 3));
          expect(after.version).toBe(String(index - 2));
          expect(before.status).toBe(index === 4 ? "SUSPENDED" : "ENROLLED");
          expect(after.status).toBe(
            index === 3 ? "SUSPENDED" : index === 4 ? "ENROLLED" : "WITHDRAWN",
          );
          expect(before.ended_at).toBe("");
          if (index === 5) expect(after.ended_at).toBeTruthy();
          else expect(after.ended_at).toBe("");
        }
        for (const value of [secret, elevated])
          expect(JSON.stringify(detail)).not.toContain(value);
      }
    }
  },
);

After({ tags: "@cast-audit" }, async ({ request }) => {
  if (elevationId) {
    await revokeEmergencyElevation(request, auditor, elevationId);
    elevationId = 0;
  }
});
