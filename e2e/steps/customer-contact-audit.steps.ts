import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import {
  activateEmergencyElevation,
  revokeEmergencyElevation,
  listAuditEvents,
  getAuditEvent,
  mergeCustomers,
  addCustomerContact,
  changeCustomerContactPermission,
  createCustomer,
  createPermissionRole,
  createPlatformStaffFixture,
  deleteCustomerContact,
  getAuthorizedStores,
  loginAsStoreAdmin,
  loginPlatformUser,
  preferCustomerContact,
  updateCustomerContact,
  STORE1_ID,
  STORE_HEADERS,
} from "./store-api";

const { Given, When, Then } = createBdd();
const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });
const headers = (token: string) => ({ ...STORE_HEADERS, ...bearer(token) });
let staff = "",
  auditor = "",
  elevated = "",
  privateValue = "",
  privateEvidence = "",
  password = "",
  otherStore = "";
let auditorId = 0,
  elevationId = 0;
type Expected = {
  target: string;
  action: string;
  before: Record<string, string> | null;
  after: Record<string, string>;
  elevated: boolean;
  group?: string;
};
let expected: Expected[] = [];
let existingHistoryIds = new Set<string>();
const actions = [
  "CREATED",
  "UPDATED",
  "PERMISSION_RECORDED",
  "PREFERENCE_CHANGED",
  "RESTRICTION_INHERITED",
  "DELETED",
];

Given(
  "連絡先監査専用の担当と昇格セッションが用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    expected = [];
    existingHistoryIds = new Set<string>();
    const suffix = Date.now().toString();
    privateValue = `private-contact-${suffix}@example.test`;
    privateEvidence = `監査へ転記しない根拠-${suffix}`;
    password = `Contact-${suffix}-fixture`;
    staff = await loginAsStoreAdmin(request);
    const owner = await loginPlatformUser(request, "admin@kizuna.test", "pass");
    const role = await createPermissionRole(
      request,
      owner,
      `連絡先監査-${suffix}`,
      ["AUDIT_VIEW", "EMERGENCY_ELEVATE", "PLATFORM_MENU_VIEW"],
    );
    const email = `contact-auditor-${suffix}@example.test`;
    auditorId = await createPlatformStaffFixture(
      request,
      owner,
      email,
      password,
      [role],
    );
    auditor = await loginPlatformUser(request, email, password);
    const session = await activateEmergencyElevation(
      request,
      auditor,
      STORE1_ID,
      "連絡先監査の検証",
      password,
    );
    elevated = session.token;
    elevationId = session.id;
    const stores = await getAuthorizedStores(request, staff);
    otherStore = String(
      stores.find((store: { id: number }) => String(store.id) !== STORE1_ID)!
        .id,
    );
  },
);

When(
  "通常と昇格の担当で連絡先の編集と許諾と優先切替を操作する",
  async ({ request }) => {
    for (const [token, isElevated] of [
      [staff, false],
      [elevated, true],
    ] as const) {
      const mark = (
        target: string,
        action: string,
        before: Record<string, string> | null,
        after: Record<string, string>,
        group?: string,
      ) =>
        expected.push({
          target,
          action: `CUSTOMER_CONTACT_${action}`,
          before,
          after,
          elevated: isElevated,
          group: group ? `${isElevated}-${group}` : undefined,
        });
      const customer = await createCustomer(request, token, privateEvidence, [
        { type: "EMAIL", value: privateValue },
      ]);
      const listing = await request.get(
        `/api/store/customers/${customer}/contacts`,
        { headers: headers(token) },
      );
      expect(listing.status()).toBe(200);
      const source = (await listing.json()).content[0].id as string;
      mark(source, "CREATED", null, {
        type: "EMAIL",
        business_status: "UNKNOWN",
        customer_id: customer,
        version: "0",
      });
      const duplicate = await addCustomerContact(
        request,
        token,
        customer,
        "EMAIL",
        privateValue,
      );
      mark(duplicate, "CREATED", null, {
        type: "EMAIL",
        business_status: "UNKNOWN",
        version: "0",
      });
      for (let i = 0; i < 2; i++) {
        await changeCustomerContactPermission(
          request,
          token,
          customer,
          source,
          "BUSINESS",
          "DENIED",
          privateEvidence,
          privateEvidence,
        );
        mark(
          source,
          "PERMISSION_RECORDED",
          { business_status: i ? "DENIED" : "UNKNOWN" },
          { business_status: "DENIED", purpose: "BUSINESS" },
        );
      }
      await preferCustomerContact(request, token, customer, "EMAIL", source);
      mark(
        source,
        "PREFERENCE_CHANGED",
        { preferred: "false" },
        { preferred: "true" },
      );
      await preferCustomerContact(request, token, customer, "EMAIL", duplicate);
      mark(
        source,
        "PREFERENCE_CHANGED",
        { preferred: "true" },
        { preferred: "false" },
        "switch",
      );
      mark(
        duplicate,
        "PREFERENCE_CHANGED",
        { preferred: "false" },
        { preferred: "true" },
        "switch",
      );
      await preferCustomerContact(request, token, customer, "EMAIL", duplicate);
      const path = `/api/store/customers/${customer}/contacts/${source}`;
      expect(
        (await request.delete(path, { headers: headers(auditor) })).status(),
      ).toBe(403);
      expect(
        (
          await request.delete(path, {
            headers: { ...headers(token), "X-Store-ID": otherStore },
          })
        ).status(),
      ).toBe(isElevated ? 403 : 404);
      expect(
        (
          await request.put(`${path}/permissions/BUSINESS`, {
            headers: headers(token),
            data: {
              status: "UNKNOWN",
              source: privateEvidence,
              reason: privateEvidence,
            },
          })
        ).status(),
      ).toBe(400);
      const changedValue = `changed-${privateValue}`;
      await updateCustomerContact(
        request,
        token,
        customer,
        source,
        "EMAIL",
        changedValue,
      );
      mark(
        duplicate,
        "RESTRICTION_INHERITED",
        { business_status: "UNKNOWN" },
        { business_status: "DENIED", source_contact_id: source },
        "edit",
      );
      mark(
        source,
        "UPDATED",
        { business_status: "DENIED" },
        { business_status: "UNKNOWN", redacted_fields_changed: "value" },
        "edit",
      );
      await updateCustomerContact(
        request,
        token,
        customer,
        source,
        "EMAIL",
        changedValue,
      );
      const remaining = await addCustomerContact(
        request,
        token,
        customer,
        "EMAIL",
        privateValue,
      );
      mark(remaining, "CREATED", null, {
        type: "EMAIL",
        business_status: "UNKNOWN",
        version: "0",
      });
      await deleteCustomerContact(request, token, customer, duplicate);
      mark(
        remaining,
        "RESTRICTION_INHERITED",
        { business_status: "UNKNOWN" },
        { business_status: "DENIED", source_contact_id: duplicate },
        "delete",
      );
      mark(
        duplicate,
        "DELETED",
        { deleted: "false", preferred: "true" },
        { deleted: "true", preferred: "false" },
        "delete",
      );
      expect(
        (
          await request.delete(
            `/api/store/customers/${customer}/contacts/${duplicate}`,
            { headers: headers(token) },
          )
        ).status(),
      ).toBe(404);
      const history = await request.get(
        `/api/store/customers/${customer}/contact-history`,
        { headers: headers(token), params: { size: 100 } },
      );
      expect(history.status()).toBe(200);
      for (const row of (await history.json()).content)
        existingHistoryIds.add(row.id);
      const destination = await createCustomer(request, token, "統合先");
      await mergeCustomers(
        request,
        token,
        destination,
        customer,
        "監査拒否の検証",
      );
      expect(
        (
          await request.put(path, {
            headers: headers(token),
            data: { type: "EMAIL", value: changedValue },
          })
        ).status(),
      ).toBe(409);
    }
  },
);

Then(
  "関連する全連絡先の実変更と根拠記録だけが安全な監査に残る",
  async ({ request }) => {
    const operations = new Map<string, Set<string>>();
    const historyIds = new Set<string>();
    for (const action of actions.map((value) => `CUSTOMER_CONTACT_${value}`)) {
      const listing = await listAuditEvents(request, auditor, action);
      const ids = new Set(
        expected
          .filter((item) => item.action === action)
          .map((item) => item.target),
      );
      const events = listing.content
        .filter((event: { target_id: string }) => ids.has(event.target_id))
        .sort((a: { id: number }, b: { id: number }) => a.id - b.id);
      const matches = expected.filter((item) => item.action === action);
      expect(events).toHaveLength(matches.length);
      for (let index = 0; index < matches.length; index++) {
        const item = matches[index],
          event = events[index];
        expect(event.target_id).toBe(item.target);
        expect(event.actor_type).toBe("STAFF");
        expect(event.actor_id === auditorId).toBe(item.elevated);
        expect(event.store_id).toBe(Number(STORE1_ID));
        expect(event.result).toBe("SUCCEEDED");
        expect(event.source_type).toBe("CUSTOMER_CONTACT_HISTORY");
        if (!event.source_id)
          throw new Error("監査に業務履歴の由来がありません");
        expect(existingHistoryIds.has(event.source_id)).toBe(true);
        expect(historyIds.has(event.source_id)).toBe(false);
        historyIds.add(event.source_id);
        const detail = await getAuditEvent(request, auditor, event.id);
        if (item.before === null) expect(detail.before_values).toEqual({});
        else expect(detail.before_values).toMatchObject(item.before);
        expect(detail.after_values).toMatchObject(item.after);
        expect(detail.after_values.operation_id).toBeTruthy();
        if (item.elevated)
          expect(detail.after_values.emergency_elevation_id).toBe(
            String(elevationId),
          );
        else expect(detail.after_values.emergency_elevation_id).toBeUndefined();
        if (item.group) {
          const group = operations.get(item.group) ?? new Set<string>();
          group.add(detail.after_values.operation_id);
          operations.set(item.group, group);
        }
        if (item.before && !action.endsWith("PERMISSION_RECORDED"))
          expect(Number(detail.after_values.version)).toBeGreaterThan(
            Number(detail.before_values.version),
          );
        for (const secret of [
          privateValue,
          privateEvidence,
          password,
          elevated,
        ])
          expect(JSON.stringify(detail)).not.toContain(secret);
        const safe = new Set([
          "version",
          "customer_id",
          "origin_customer_id",
          "type",
          "preferred",
          "deleted",
          "business_status",
          "marketing_status",
          "operation_id",
          "purpose",
          "source_contact_id",
          "redacted_fields_changed",
          "emergency_elevation_id",
        ]);
        expect(
          Object.keys(detail.before_values).every((key) => safe.has(key)),
        ).toBe(true);
        expect(
          Object.keys(detail.after_values).every((key) => safe.has(key)),
        ).toBe(true);
      }
    }
    expect(historyIds.size).toBe(24);
    expect(operations.size).toBe(6);
    for (const group of operations.values()) expect(group.size).toBe(1);
    await revokeEmergencyElevation(request, auditor, elevationId);
  },
);
