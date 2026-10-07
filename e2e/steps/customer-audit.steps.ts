import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import {
  activateEmergencyElevation,
  revokeEmergencyElevation,
  listAuditEvents,
  getAuditEvent,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  createCast,
  createCourse,
  createPermissionRole,
  createPlatformStaffFixture,
  getAuthorizedStores,
  getOrderReceptionists,
  loginAsStoreAdmin,
  loginPlatformUser,
  mergeCustomers,
  submitConfirmedStoreRequest,
  STORE1_ID,
  STORE_HEADERS,
} from "./store-api";

const { Given, When, Then } = createBdd();
const headers = (token: string) => ({
  ...STORE_HEADERS,
  Authorization: `Bearer ${token}`,
});
let staff = "",
  auditor = "",
  elevated = "",
  password = "",
  secret = "",
  otherStore = "";
let auditorId = 0,
  elevationId = 0;
type Expected = {
  target: string;
  action: string;
  before: Record<string, string>;
  after: Record<string, string>;
  elevated: boolean;
};
let expected: Expected[] = [];
let nestedCustomers: string[] = [];

Given(
  "顧客監査専用の担当と昇格セッションが用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    expected = [];
    nestedCustomers = [];
    const suffix = Date.now().toString();
    secret = `顧客監査へ複写しない私的入力-${suffix}`;
    password = `Customer-${suffix}-fixture`;
    staff = await loginAsStoreAdmin(request);
    const owner = await loginPlatformUser(request, "admin@kizuna.test", "pass");
    const role = await createPermissionRole(
      request,
      owner,
      `顧客監査-${suffix}`,
      ["AUDIT_VIEW", "EMERGENCY_ELEVATE", "PLATFORM_MENU_VIEW"],
    );
    const email = `customer-auditor-${suffix}@example.test`;
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
      "顧客監査の検証",
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
  "通常と昇格の担当で顧客作成と編集と削除を操作する",
  async ({ request }) => {
    const cast = await createCast(request, staff, "顧客監査担当");
    const course = await createCourse(request, staff, "顧客監査コース");
    const receptionist = (await getOrderReceptionists(request, staff))[0].id;
    for (const [token, isElevated] of [
      [staff, false],
      [elevated, true],
    ] as const) {
      const mark = (
        target: string,
        action: string,
        before: Record<string, string>,
        after: Record<string, string>,
      ) =>
        expected.push({
          target,
          action: `CUSTOMER_${action}`,
          before,
          after,
          elevated: isElevated,
        });
      const created = (id: string) =>
        mark(
          id,
          "CREATED",
          {},
          { exists: "true", version: "0", redacted_fields_changed: "name" },
        );
      const customer = await createCustomer(request, token, secret);
      created(customer);
      const path = `/api/store/customers/${customer}`;
      const patch = {
        name: `${secret}-編集`,
        address: secret,
        building_name: secret,
        landmark: secret,
        classification: secret,
        has_pet: false,
        usage_areas: secret,
        ng_type: secret,
        ng_content: secret,
      };
      await updateCustomer(request, token, customer, patch);
      mark(
        customer,
        "UPDATED",
        { exists: "true", version: "0" },
        {
          exists: "true",
          version: "1",
          redacted_fields_changed:
            "address,building_name,classification,has_pet,landmark,name,ng_content,ng_type,usage_areas",
        },
      );
      await updateCustomer(request, token, customer, patch);
      await updateCustomer(request, token, customer, {});
      expect(
        (
          await request.put(path, {
            headers: headers(token),
            data: { name: null },
          })
        ).status(),
      ).toBe(200);
      expect(
        (await request.get(path, { headers: headers(token) })).status(),
      ).toBe(200);
      expect(
        (
          await request.put(path, {
            headers: headers(auditor),
            data: { name: secret },
          })
        ).status(),
      ).toBe(403);
      expect(
        (await request.delete(path, { headers: headers(auditor) })).status(),
      ).toBe(403);
      expect(
        (
          await request.post("/api/store/customers", {
            headers: headers(auditor),
            data: { name: secret },
          })
        ).status(),
      ).toBe(403);
      expect(
        (
          await request.post("/api/store/customers", {
            headers: headers(token),
            data: { name: "" },
          })
        ).status(),
      ).toBe(400);
      expect(
        (
          await request.put(path, {
            headers: { ...headers(token), "X-Store-ID": otherStore },
            data: { name: secret },
          })
        ).status(),
      ).toBe(isElevated ? 403 : 404);
      expect(
        (
          await request.delete(path, {
            headers: { ...headers(token), "X-Store-ID": otherStore },
          })
        ).status(),
      ).toBe(isElevated ? 403 : 404);
      await deleteCustomer(request, token, customer);
      mark(
        customer,
        "DELETED",
        { exists: "true", version: "1" },
        { exists: "false" },
      );
      expect(
        (await request.get(path, { headers: headers(token) })).status(),
      ).toBe(404);
      expect(
        (await request.delete(path, { headers: headers(token) })).status(),
      ).toBe(404);
      expect(
        (
          await request.put(path, {
            headers: headers(token),
            data: { name: secret },
          })
        ).status(),
      ).toBe(404);

      const contactParent = await createCustomer(request, token, secret, [
        { type: "LINE", value: secret },
      ]);
      created(contactParent);
      nestedCustomers.push(contactParent);
      expect(
        (
          await request.delete(`/api/store/customers/${contactParent}`, {
            headers: headers(token),
          })
        ).status(),
      ).toBe(409);

      const ordered = await createCustomer(request, token, secret);
      created(ordered);
      await submitConfirmedStoreRequest(
        request,
        token,
        "/api/store/orders/preview",
        "/api/store/orders",
        {
          cast_id: cast,
          receptionist_id: receptionist,
          course_id: course,
          customer_selection: { mode: "EXISTING", customer_id: ordered },
          business_date: new Intl.DateTimeFormat("en-CA", {
            timeZone: "Asia/Tokyo",
          }).format(new Date()),
        },
      );
      expect(
        (
          await request.delete(`/api/store/customers/${ordered}`, {
            headers: headers(token),
          })
        ).status(),
      ).toBe(409);

      const surviving = await createCustomer(request, token, secret);
      const merged = await createCustomer(request, token, secret);
      created(surviving);
      created(merged);
      await mergeCustomers(
        request,
        token,
        surviving,
        merged,
        "顧客監査拒否の検証",
      );
      expect(
        (
          await request.put(`/api/store/customers/${merged}`, {
            headers: headers(token),
            data: { name: secret },
          })
        ).status(),
      ).toBe(409);
      for (const id of [surviving, merged])
        expect(
          (
            await request.delete(`/api/store/customers/${id}`, {
              headers: headers(token),
            })
          ).status(),
        ).toBe(409);
    }
  },
);

Then("実変更と削除後の顧客監査だけが安全に照会できる", async ({ request }) => {
  const targets = new Set(expected.map((item) => item.target));
  for (const action of [
    "CUSTOMER_CREATED",
    "CUSTOMER_UPDATED",
    "CUSTOMER_DELETED",
  ]) {
    const events = (await listAuditEvents(request, auditor, action)).content
      .filter((event) => targets.has(event.target_id))
      .sort((a, b) => a.id - b.id);
    const matches = expected.filter((item) => item.action === action);
    expect(events).toHaveLength(matches.length);
    for (let i = 0; i < matches.length; i++) {
      const event = events[i],
        item = matches[i];
      expect(event.target_id).toBe(item.target);
      expect(event.target_type).toBe("CUSTOMER");
      expect(event.actor_type).toBe("STAFF");
      expect(event.actor_id === auditorId).toBe(item.elevated);
      expect(event.store_id).toBe(Number(STORE1_ID));
      expect(event.result).toBe("SUCCEEDED");
      expect(event.source_type ?? null).toBeNull();
      expect(event.source_id ?? null).toBeNull();
      const detail = await getAuditEvent(request, auditor, event.id);
      expect(detail.before_values).toEqual(item.before);
      expect(detail.after_values).toEqual({
        ...item.after,
        ...(item.elevated
          ? { emergency_elevation_id: String(elevationId) }
          : {}),
      });
      for (const value of [secret, password, elevated])
        expect(JSON.stringify(detail)).not.toContain(value);
    }
  }
  const contacts = (
    await listAuditEvents(request, auditor, "CUSTOMER_CONTACT_CREATED")
  ).content;
  for (const customer of nestedCustomers) {
    const matches = [];
    for (const event of contacts) {
      const detail = await getAuditEvent(request, auditor, event.id);
      if (detail.after_values.customer_id === customer) matches.push(detail);
    }
    expect(matches).toHaveLength(1);
    expect(matches[0].event.source_type).toBe("CUSTOMER_CONTACT_HISTORY");
    expect(matches[0].event.source_id).toBeTruthy();
    expect(JSON.stringify(matches[0])).not.toContain(secret);
  }
  expect(expected).toHaveLength(14);
  await revokeEmergencyElevation(request, auditor, elevationId);
});
