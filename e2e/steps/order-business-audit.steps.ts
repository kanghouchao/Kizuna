import { expect, type APIRequestContext } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import {
  listAuditEvents,
  getAuditEvent,
  createCast,
  createCourse,
  createPermissionRole,
  createStaffFixture,
  getAuthorizedStores,
  getOrder,
  getOrderReceptionists,
  loginAsStoreAdmin,
  loginUser,
  registerMember,
  submitConfirmedStoreRequest,
  STORE1_ID,
  STORE_HEADERS,
} from "./store-api";

const { Given, When, Then } = createBdd();
let staff = "";
let auditor = "";
let member = "";
let castId = "";
let courseId = "";
let receptionistId = 0;
let otherStoreId = "";
let privateText = "";
let privateMail = "";
let privatePassword = "";
const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });
const storeHeaders = (token: string) => ({
  ...STORE_HEADERS,
  ...bearer(token),
});
const today = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo" }).format(
    new Date(),
  );
type Expected = {
  target: string;
  action: string;
  before: string | null;
  after: string;
  source?: string;
};
let expected: Expected[] = [];
let receiptSecrets: string[] = [];

Given(
  "受注監査専用の担当と監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    expected = [];
    receiptSecrets = [];
    const suffix = Date.now().toString();
    privateText = "監査へ転記しない私的内容-" + suffix;
    privateMail = "private-" + suffix + "@example.test";
    privatePassword = "Audit-" + suffix + "-fixture";
    staff = await loginAsStoreAdmin(request);
    const owner = await loginUser(request, "admin@kizuna.test", "pass1234");
    const role = await createPermissionRole(
      request,
      owner,
      "受注監査-" + suffix,
      ["AUDIT_VIEW",],
    );
    const email = "order-auditor-" + suffix + "@example.test";
    await createStaffFixture(
      request,
      owner,
      email,
      privatePassword,
      [role],
    );
    auditor = await loginUser(request, email, privatePassword);
    castId = await createCast(request, staff, "監査対象-" + suffix);
    courseId = await createCourse(request, staff, "監査コース-" + suffix);
    receptionistId = (await getOrderReceptionists(request, staff))[0].id;
    const stores = await getAuthorizedStores(request, staff);
    otherStoreId = String(
      stores.find((store: { id: number }) => String(store.id) !== STORE1_ID)!
        .id,
    );
    const memberEmail = "order-audit-member-" + suffix + "@example.test";
    await registerMember(request, memberEmail, privatePassword, privateText);
    member = await loginUser(request, memberEmail, privatePassword);
  },
);

async function createOrder(request: APIRequestContext, token: string) {
  const data = {
    cast_id: castId,
    course_id: courseId,
    receptionist_id: receptionistId,
    customer_selection: { mode: "NONE" },
    contact_snapshot: { name: privateText, email: privateMail },
    remarks: privateText,
    address: privateText,
    business_date: today(),
    pax: 2,
  };
  const created = await (
    await submitConfirmedStoreRequest(
      request,
      token,
      "/api/orders/preview",
      "/api/orders",
      data,
    )
  ).json();
  expected.push({
    target: created.id,
    action: "ORDER_CREATED",
    before: null,
    after: "CONFIRMED",
  });
  return created;
}

async function createApplication(request: APIRequestContext) {
  const response = await request.post("/api/me/order-applications", {
    headers: bearer(member),
    data: {
      store_id: Number(STORE1_ID),
      declared_name: privateText,
      remarks: privateText,
      business_date: today(),
      pax: 2,
    },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id as string;
}

When(
  "通常担当で受注の作成から申請決定までを操作する",
  async ({ request }) => {
    for (const token of [staff]) {
      const created = await createOrder(request, token);
      const initial = await getOrder(request, token, STORE1_ID, created.id);
      const update = {
        expected_version: initial.version,
        cast_id: castId,
        receptionist_id: receptionistId,
        pax: 3,
        remarks: privateText + "-変更",
      };
      await submitConfirmedStoreRequest(
        request,
        token,
        `/api/orders/${created.id}/preview`,
        `/api/orders/${created.id}`,
        update,
        "put",
      );
      expected.push({
        target: created.id,
        action: "ORDER_UPDATED",
        before: "CONFIRMED",
        after: "CONFIRMED",
      });
      const stale = await request.put(`/api/orders/${created.id}`, {
        headers: storeHeaders(token),
        data: update,
      });
      expect(stale.status(), await stale.text()).toBe(409);
      const updated = await getOrder(request, token, STORE1_ID, created.id);
      const start = await request.post(
        `/api/orders/${created.id}/start`,
        {
          headers: storeHeaders(token),
          data: { expected_version: updated.version, reason: privateText },
        },
      );
      expect(start.status(), await start.text()).toBe(200);
      expected.push({
        target: created.id,
        action: "ORDER_STARTED",
        before: "CONFIRMED",
        after: "IN_SERVICE",
      });
      const started = await getOrder(request, token, STORE1_ID, created.id);
      const completed = await (
        await submitConfirmedStoreRequest(
          request,
          token,
          `/api/orders/${created.id}/completion-preview`,
          `/api/orders/${created.id}/completion`,
          { expected_version: started.version, fee_lines: [] },
        )
      ).json();
      if (completed.receipt_token) receiptSecrets.push(completed.receipt_token);
      expected.push({
        target: created.id,
        action: "ORDER_COMPLETED",
        before: "IN_SERVICE",
        after: "COMPLETED",
      });
      const refused = await request.post(
        `/api/orders/${created.id}/cancellation`,
        { headers: storeHeaders(token), data: { reason: privateText } },
      );
      expect(refused.status()).toBe(400);

      const cancelled = await createOrder(request, token);
      const forbidden = await request.post(
        `/api/orders/${cancelled.id}/cancellation`,
        { headers: storeHeaders(auditor), data: { reason: privateText } },
      );
      expect(forbidden.status()).toBe(403);
      const crossStore = await request.post(
        `/api/orders/${cancelled.id}/cancellation`,
        {
          headers: { ...storeHeaders(token), "X-Store-ID": otherStoreId },
          data: { reason: privateText },
        },
      );
      expect(crossStore.status()).toBe(404);
      const cancellation = await request.post(
        `/api/orders/${cancelled.id}/cancellation`,
        { headers: storeHeaders(token), data: { reason: privateText } },
      );
      expect(cancellation.status()).toBe(204);
      expected.push({
        target: cancelled.id,
        action: "ORDER_CANCELLED",
        before: "CONFIRMED",
        after: "CANCELLED",
      });

      const application = await createApplication(request);
      const confirmed = await (
        await submitConfirmedStoreRequest(
          request,
          token,
          `/api/order-applications/${application}/confirmation-preview`,
          `/api/order-applications/${application}/confirmation`,
          {
            course_id: courseId,
            business_date: today(),
            pax: 2,
            receptionist_id: receptionistId,
          },
        )
      ).json();
      expected.push({
        target: application,
        action: "ORDER_APPLICATION_CONFIRMED",
        before: "PENDING",
        after: "CONFIRMED",
      });
      expected.push({
        target: confirmed.id,
        action: "ORDER_CREATED",
        before: null,
        after: "CONFIRMED",
        source: application,
      });
      const declinedId = await createApplication(request);
      const declined = await request.post(
        `/api/order-applications/${declinedId}/refusal`,
        { headers: storeHeaders(token), data: { reason: privateText } },
      );
      expect(declined.status()).toBe(204);
      expected.push({
        target: declinedId,
        action: "ORDER_APPLICATION_DECLINED",
        before: "PENDING",
        after: "DECLINED",
      });
    }
  },
);

Then(
  "成功した受注変更だけが前後値と主体を伴い秘密なしで監査に残る",
  async ({ request }) => {
    for (const item of expected) {
      const listing = await listAuditEvents(request, auditor, item.action);
      const events = listing.content.filter(
        (event: { target_id: string }) => event.target_id === item.target,
      );
      expect(events).toHaveLength(1);
      const event = events[0];
      expect(event.actor_type).toBe("STAFF");
      expect(event.store_id).toBe(Number(STORE1_ID));
      expect(event.result).toBe("SUCCEEDED");
      if (item.source) {
        expect(event.source_type).toBe("ORDER_APPLICATION");
        expect(event.source_id).toBe(item.source);
      }
      const detail = await getAuditEvent(request, auditor, event.id);
      if (item.before === null) expect(detail.before_values).toEqual({});
      else expect(detail.before_values.status).toBe(item.before);
      expect(detail.after_values.status).toBe(item.after);
      if (item.action === "ORDER_UPDATED") {
        expect(detail.before_values.pax).toBe("2");
        expect(detail.after_values.pax).toBe("3");
        expect(detail.after_values.cast_enrollment_id).toBe(castId);
        expect(detail.after_values.receptionist_id).toBe(
          String(receptionistId),
        );
        expect(detail.after_values.redacted_fields_changed).toContain(
          "remarks",
        );
        expect(Number(detail.after_values.version)).toBeGreaterThan(
          Number(detail.before_values.version),
        );
      }
      if (item.action === "ORDER_COMPLETED") {
        expect(detail.after_values.total_fee).toBe("12000");
        expect(detail.after_values.accrued_remuneration).toBe("7000");
      }
      const serialized = JSON.stringify(detail);
      for (const secret of [
        privateText,
        privateMail,
        privatePassword,
        ...receiptSecrets,
      ])
        expect(serialized).not.toContain(secret);
    }
  },
);
