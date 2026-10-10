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
  createCastFieldDefinition,
  deleteCastFieldDefinition,
  listCastFieldDefinitions,
  updateCastFieldDefinition,
  setCastCustomFields,
  getCastCustomFields,
  castFieldSnapshots,
  deleteCast,
  getAuditEvent,
  listAuditEvents,
  getAuthorizedStores,
  loginAsStoreAdmin,
  loginPlatformUser,
  type AuditEventSummary,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let ordinary = "",
  limited = "",
  auditor = "",
  elevated = "",
  secret = "",
  otherStore = "",
  manager = "";
let ordinaryId = 0,
  auditorId = 0,
  elevationId = 0;
type AuditCase = {
  internal: string;
  external: string;
  id: string;
  actorId: number;
  elevated: boolean;
  snapshotId: string;
  initialOrder: number;
};
let cases: AuditCase[] = [];
let castIds: string[] = [];
const definitionIds = new Set<string>();
const headers = (token: string, storeId = STORE1_ID) => ({
  ...STORE_HEADERS,
  "X-Store-ID": storeId,
  Authorization: `Bearer ${token}`,
});
const definitionKeys = ["exists", "is_public", "display_order", "version"];
const actions = [
  "CAST_FIELD_DEFINITION_CREATED",
  "CAST_FIELD_DEFINITION_UPDATED",
  "CAST_FIELD_DEFINITION_DELETED",
  "CAST_INTERNAL_FIELD_REMOVED",
  "CAST_PROFILE_FIELD_REMOVED",
];

Given(
  "項目監査専用の担当と昇格セッションが用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    cases = [];
    definitionIds.clear();
    castIds = [];
    elevationId = 0;
    const suffix = randomUUID();
    secret = `監査へ複写しない秘密-${suffix}`;
    manager = await loginAsStoreAdmin(request);
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
      `項目担当-${suffix}`,
      [
        "CAST_MANAGE",
        "CAST_FIELD_DEF_MANAGE",
        "CAST_FIELD_DEF_VIEW",
        "STORE_VIEW",
        "STORE_MENU_VIEW",
      ],
    );
    const ordinaryEmail = `field-audit-${suffix}@example.test`;
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
      ["CAST_FIELD_DEF_VIEW", "STORE_VIEW", "STORE_MENU_VIEW"],
    );
    const limitedEmail = `field-limited-${suffix}@example.test`;
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
      `項目監査-${suffix}`,
      ["AUDIT_VIEW", "EMERGENCY_ELEVATE", "PLATFORM_MENU_VIEW"],
    );
    const auditEmail = `field-elevation-${suffix}@example.test`;
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
      "項目監査の検証",
      password,
    );
    elevated = session.token;
    elevationId = session.id;
  },
);
When(
  "通常と昇格の担当が項目定義と関連値を変更し拒否条件も確認する",
  async ({ request }) => {
    for (const actor of [
      { token: ordinary, id: ordinaryId, elevated: false },
      { token: elevated, id: auditorId, elevated: true },
    ]) {
      const suffix = randomUUID().replaceAll("-", "");
      const privateKey = `private_${suffix}`,
        publicKey = `public_${suffix}`;
      const internal = await createTracked(
        request,
        actor.token,
        privateKey,
        false,
      );
      const external = await createTracked(
        request,
        actor.token,
        publicKey,
        true,
      );
      const initial = (
        await listCastFieldDefinitions(request, actor.token)
      ).find((row) => row.id === internal)!;
      const id = await createUnpublishedCast(request, actor.token, secret);
      castIds.push(id);
      await setCastCustomFields(request, actor.token, id, {
        [privateKey]: secret,
        [publicKey]: `${secret}-公開`,
      });
      await updateCastFieldDefinition(request, actor.token, internal, {
        label: secret,
        display_order: initial.display_order,
        is_public: false,
      });
      const beforeDenials = await auditIds(request);
      const mutations = [
        {
          path: "/api/store/casts/fields",
          method: "POST",
          data: { key: `denied_${suffix}`, label: secret, is_public: false },
        },
        {
          path: `/api/store/casts/fields/${internal}`,
          method: "PUT",
          data: { label: "拒否される変更" },
        },
        { path: `/api/store/casts/fields/${internal}`, method: "DELETE" },
      ];
      for (const mutation of mutations) {
        const denied = await request.fetch(mutation.path, {
          ...mutation,
          headers: headers(limited),
        });
        expect(denied.status(), await denied.text()).toBe(403);
      }
      for (const mutation of mutations.slice(1)) {
        const denied = await request.fetch(mutation.path, {
          ...mutation,
          headers: headers(actor.token, otherStore),
        });
        expect(denied.status(), await denied.text()).toBe(
          actor.elevated ? 403 : 404,
        );
      }
      const duplicate = await request.post("/api/store/casts/fields", {
        headers: headers(actor.token),
        data: { key: privateKey, label: secret },
      });
      expect(duplicate.status(), await duplicate.text()).toBe(400);
      const visibility = await request.put(
        `/api/store/casts/fields/${internal}`,
        { headers: headers(actor.token), data: { is_public: true } },
      );
      expect(visibility.status(), await visibility.text()).toBe(400);
      expect(await auditIds(request)).toEqual(beforeDenials);
      await updateCastFieldDefinition(request, actor.token, internal, {
        label: `${secret}-変更`,
      });
      await updateCastFieldDefinition(request, actor.token, internal, {
        label: `${secret}-変更`,
      });
      await updateCastFieldDefinition(request, actor.token, internal, {
        display_order: 42,
      });
      await deleteTracked(request, actor.token, internal);
      expect(await getCastCustomFields(request, actor.token, id)).toEqual({
        [publicKey]: `${secret}-公開`,
      });
      const snapshots = (await castFieldSnapshots(request, actor.token, id))
        .content;
      expect(snapshots).toHaveLength(2);
      const snapshot = snapshots.find(
        (row) => row.custom_fields[privateKey] === secret,
      )!;
      expect(snapshot).toBeDefined();
      expect(snapshot.actor_id).toBe(actor.id);
      await deleteTracked(request, actor.token, external);
      expect(await getCastCustomFields(request, actor.token, id)).toEqual({});
      const beforeRepeated = await auditIds(request);
      const repeated = await request.delete(
        `/api/store/casts/fields/${internal}`,
        { headers: headers(actor.token) },
      );
      expect(repeated.status(), await repeated.text()).toBe(404);
      expect(await auditIds(request)).toEqual(beforeRepeated);
      const rebuilt = await createTracked(
        request,
        actor.token,
        privateKey,
        true,
      );
      expect(rebuilt).not.toBe(internal);
      expect(await getCastCustomFields(request, actor.token, id)).toEqual({});
      await deleteTracked(request, actor.token, rebuilt);
      cases.push({
        internal,
        external,
        id,
        actorId: actor.id,
        elevated: actor.elevated,
        snapshotId: snapshot.id,
        initialOrder: initial.display_order,
      });
    }
    const existing = await listCastFieldDefinitions(request, ordinary);
    const extra: string[] = [];
    for (let i = existing.length; i < 20; i++)
      extra.push(
        await createTracked(
          request,
          ordinary,
          `capacity_${randomUUID().replaceAll("-", "")}`,
          false,
        ),
      );
    const beforeCapacity = await auditIds(request);
    const excess = await request.post("/api/store/casts/fields", {
      headers: headers(ordinary),
      data: {
        key: `excess_${randomUUID().replaceAll("-", "")}`,
        label: secret,
      },
    });
    expect(excess.status(), await excess.text()).toBe(400);
    expect(await listCastFieldDefinitions(request, ordinary)).toHaveLength(20);
    expect(await auditIds(request)).toEqual(beforeCapacity);
    for (const id of extra) await deleteTracked(request, ordinary, id);
  },
);

Then(
  "定義と実際の値除去だけが安全な版本と快照参照で監査される",
  async ({ request }) => {
    for (const item of cases) {
      for (const definition of [
        { id: item.internal, public: false },
        { id: item.external, public: true },
      ]) {
        for (const action of [
          "CAST_FIELD_DEFINITION_CREATED",
          "CAST_FIELD_DEFINITION_UPDATED",
          "CAST_FIELD_DEFINITION_DELETED",
        ]) {
          const events = (
            await listAuditEvents(request, auditor, action)
          ).content
            .filter((event) => event.target_id === definition.id)
            .sort((a, b) => a.id - b.id);
          expect(events).toHaveLength(
            action.endsWith("UPDATED") ? (definition.public ? 0 : 2) : 1,
          );
          for (const [index, event] of events.entries()) {
            expect(event.target_type).toBe("CAST_FIELD_DEFINITION");
            expect(event.source_id).toBeUndefined();
            const detail = await checkedDetail(request, event, item);
            const before = detail.before_values,
              after = detail.after_values;
            const created = action.endsWith("CREATED"),
              deleted = action.endsWith("DELETED");
            const labelChange = action.endsWith("UPDATED") && index === 0;
            expect(Object.keys(before).sort()).toEqual(
              created ? [] : [...definitionKeys].sort(),
            );
            expect(Object.keys(after).sort()).toEqual(
              [
                ...(deleted ? ["exists"] : definitionKeys),
                ...(labelChange ? ["redacted_fields_changed"] : []),
                ...(item.elevated ? ["emergency_elevation_id"] : []),
              ].sort(),
            );
            expect(after.exists).toBe(deleted ? "false" : "true");
            if (created) {
              expect(after.version).toBe("0");
              expect(after.is_public).toBe(String(definition.public));
            } else if (deleted) {
              expect(before.version).toBe(definition.public ? "0" : "2");
              expect(after.version).toBeUndefined();
            } else {
              expect(before.version).toBe(String(index));
              expect(after.version).toBe(String(index + 1));
              expect(after.is_public).toBe("false");
              if (labelChange) {
                expect(after.redacted_fields_changed).toBe("label");
                expect(after.display_order).toBe(String(item.initialOrder));
              } else {
                expect(before.display_order).toBe(String(item.initialOrder));
                expect(after.display_order).toBe("42");
              }
            }
          }
        }
      }
      for (const side of [
        {
          action: "CAST_INTERNAL_FIELD_REMOVED",
          source: item.internal,
          type: "CAST_ENROLLMENT",
          changed: "internal_custom_fields",
          keys: ["exists", "version"],
        },
        {
          action: "CAST_PROFILE_FIELD_REMOVED",
          source: item.external,
          type: "CAST_PROFILE",
          changed: "public_custom_fields",
          keys: ["exists", "version", "enrollment_id"],
        },
      ]) {
        const events = (
          await listAuditEvents(request, auditor, side.action)
        ).content.filter((event) => event.source_id === side.source);
        expect(events).toHaveLength(1);
        const event = events[0];
        expect(event.source_type).toBe("CAST_FIELD_DEFINITION");
        expect(event.target_type).toBe(side.type);
        const detail = await checkedDetail(request, event, item);
        expect(Object.keys(detail.before_values).sort()).toEqual(
          [...side.keys].sort(),
        );
        expect(Object.keys(detail.after_values).sort()).toEqual(
          [
            ...side.keys,
            "redacted_fields_changed",
            ...(side.type === "CAST_ENROLLMENT" ? ["snapshot_id"] : []),
            ...(item.elevated ? ["emergency_elevation_id"] : []),
          ].sort(),
        );
        expect(detail.before_values.version).toBe("1");
        expect(detail.after_values.version).toBe("2");
        expect(detail.after_values.redacted_fields_changed).toBe(side.changed);
        if (side.type === "CAST_ENROLLMENT") {
          expect(event.target_id).toBe(item.id);
          expect(detail.after_values.snapshot_id).toBe(item.snapshotId);
        } else {
          expect(detail.before_values.enrollment_id).toBe(item.id);
          expect(detail.after_values.enrollment_id).toBe(item.id);
        }
      }
    }
  },
);

async function checkedDetail(
  request: APIRequestContext,
  event: AuditEventSummary,
  item: AuditCase,
) {
  expect(event.actor_id).toBe(item.actorId);
  expect(event.actor_type).toBe("STAFF");
  expect(event.store_id).toBe(Number(STORE1_ID));
  expect(event.result).toBe("SUCCEEDED");
  const detail = await getAuditEvent(request, auditor, event.id);
  expect(detail.after_values.emergency_elevation_id).toBe(
    item.elevated ? String(elevationId) : undefined,
  );
  for (const hidden of [secret, elevated])
    expect(JSON.stringify(detail)).not.toContain(hidden);
  return detail;
}

async function auditIds(request: APIRequestContext) {
  const ids: number[] = [];
  for (const action of actions)
    ids.push(
      ...(await listAuditEvents(request, auditor, action)).content.map(
        (event) => event.id,
      ),
    );
  return ids.sort((a, b) => a - b);
}

async function createTracked(
  request: APIRequestContext,
  token: string,
  key: string,
  isPublic: boolean,
) {
  const id = await createCastFieldDefinition(request, token, {
    key,
    label: secret,
    isPublic,
  });
  definitionIds.add(id);
  return id;
}

async function deleteTracked(
  request: APIRequestContext,
  token: string,
  id: string,
) {
  await deleteCastFieldDefinition(request, token, id);
  definitionIds.delete(id);
}

After({ tags: "@cast-field-audit" }, async ({ request }) => {
  for (const id of definitionIds) await deleteTracked(request, manager, id);
  for (const id of castIds) await deleteCast(request, manager, id);
  if (elevationId) {
    await revokeEmergencyElevation(request, auditor, elevationId);
    elevationId = 0;
  }
});
