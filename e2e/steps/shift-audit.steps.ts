import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import {
  activateEmergencyElevation,
  createShift,
  deleteShift,
  approveShiftRequest,
  submitCastShiftRequest,
  submitCastShiftChangeRequest,
  declineShiftRequest,
  updateShift,
  changeShiftPublication,
  recordAttendance,
  correctAttendance,
  cancelAttendance,
  revokeEmergencyElevation,
  listAuditEvents,
  getAuditEvent,
  createCast,
  issueCastInvitation,
  acceptCastInvitation,
  createPermissionRole,
  createPlatformStaffFixture,
  getAuthorizedStores,
  loginAsStoreAdmin,
  loginPlatformUser,
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
  secret = "",
  password = "",
  otherStore = "";
let elevationId = 0,
  auditorId = 0;
type Expected = {
  action: string;
  target: string;
  type: string;
  actor: "STAFF" | "CAST";
  elevated: boolean;
  source?: string;
  sourceType?: string;
  before?: Record<string, string>;
  after?: Record<string, string>;
};
let expected: Expected[] = [];
const secrets: string[] = [];

Given(
  "シフト監査専用の担当と本人と昇格セッションが用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    expected = [];
    secrets.length = 0;
    const suffix = Date.now().toString();
    secret = `監査に複写しない私的入力-${suffix}`;
    password = `Shift-${suffix}-fixture`;
    staff = await loginAsStoreAdmin(request);
    const owner = await loginPlatformUser(request, "admin@kizuna.test", "pass");
    const role = await createPermissionRole(
      request,
      owner,
      `シフト監査-${suffix}`,
      ["AUDIT_VIEW", "EMERGENCY_ELEVATE", "PLATFORM_MENU_VIEW"],
    );
    const email = `shift-auditor-${suffix}@example.test`;
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
      "シフト監査検証",
      password,
    );
    elevated = session.token;
    elevationId = session.id;
    const stores = await getAuthorizedStores(request, staff);
    otherStore = String(
      stores.find((store: { id: number }) => String(store.id) !== STORE1_ID)!
        .id,
    );
    secrets.push(secret, password, elevated);
  },
);

When(
  "申請と排班と実績の11入口と拒否と削除の関連解除を操作する",
  async ({ request }) => {
    for (const [token, isElevated] of [
      [staff, false],
      [elevated, true],
    ] as const) {
      const suffix = `${Date.now()}-${isElevated}`;
      const cast = await createCast(request, staff, `シフト監査-${suffix}`);
      const invitation = await issueCastInvitation(request, staff, cast);
      const email = `shift-cast-${suffix}@example.test`;
      await acceptCastInvitation(
        request,
        invitation,
        email,
        password,
        "監査本人",
      );
      const own = await loginPlatformUser(request, email, password);
      secrets.push(invitation, own, email);
      const date = new Date(Date.now() + 2 * 86400000)
        .toISOString()
        .slice(0, 10);
      const slot = {
        store_id: Number(STORE1_ID),
        work_date: date,
        start_time: "10:00",
        end_time: "18:00",
        note: secret,
      };
      const track = (
        action: string,
        target: string,
        type: string,
        extra: Partial<Expected> = {},
      ) =>
        expected.push({
          action,
          target,
          type,
          actor: "STAFF",
          elevated: isElevated,
          ...extra,
        });
      const submit = async () => {
        const r = await submitCastShiftRequest(request, own, slot);
        track("SHIFT_REQUEST_SUBMITTED", r.id, "SHIFT_REQUEST", {
          actor: "CAST",
          elevated: false,
          after: {
            status: "PENDING",
            redacted_fields_changed: "note",
            version: "0",
          },
        });
        return r.id;
      };
      const proposed = await submit();
      const approved = await approveShiftRequest(
        request,
        token,
        proposed,
        false,
      );
      const shift = approved.shift_id;
      track("SHIFT_CREATED", shift, "SHIFT", {
        source: proposed,
        sourceType: "SHIFT_REQUEST",
        after: { published: "false", version: "0" },
      });
      track("SHIFT_REQUEST_APPROVED", proposed, "SHIFT_REQUEST", {
        before: { status: "PENDING", version: "0" },
        after: { status: "APPROVED", shift_id: shift, version: "1" },
      });
      expect(
        (
          await request.post(`/api/store/shift-requests/${proposed}/approval`, {
            headers: headers(token),
          })
        ).status(),
      ).toBe(400);
      const change = await submitCastShiftChangeRequest(request, own, {
        ...slot,
        shift_id: shift,
        start_time: "11:00",
      });
      track("SHIFT_CHANGE_REQUEST_SUBMITTED", change.id, "SHIFT_REQUEST", {
        actor: "CAST",
        elevated: false,
        after: { shift_id: shift, redacted_fields_changed: "note" },
      });
      await approveShiftRequest(request, token, change.id);
      track("SHIFT_UPDATED", shift, "SHIFT", {
        source: change.id,
        sourceType: "SHIFT_REQUEST",
        before: { start_time: "10:00", version: "0" },
        after: { start_time: "11:00", published: "false", version: "1" },
      });
      track("SHIFT_REQUEST_APPROVED", change.id, "SHIFT_REQUEST", {
        after: { status: "APPROVED" },
      });
      const declined = await submit();
      await declineShiftRequest(request, token, declined);
      track("SHIFT_REQUEST_DECLINED", declined, "SHIFT_REQUEST", {
        after: { status: "DECLINED" },
      });
      expect(
        (
          await request.post(
            `/api/store/shift-requests/${declined}/rejection`,
            { headers: headers(token) },
          )
        ).status(),
      ).toBe(400);
      for (let repeat = 0; repeat < 2; repeat++)
        await changeShiftPublication(request, token, shift, true);
      track("SHIFT_PUBLICATION_CHANGED", shift, "SHIFT", {
        before: { published: "false" },
        after: { published: "true", version: "2" },
      });

      const directId = await createShift(request, token, {
        castId: cast,
        workDate: date,
        startTime: "10:00",
        endTime: "18:00",
        status: "TENTATIVE",
      });
      track("SHIFT_CREATED", directId, "SHIFT", {
        after: { status: "TENTATIVE" },
      });
      for (let repeat = 0; repeat < 2; repeat++)
        await updateShift(request, token, directId, { start_time: "12:00" });
      track("SHIFT_UPDATED", directId, "SHIFT", {
        before: { start_time: "10:00" },
        after: { start_time: "12:00" },
      });
      expect(
        (
          await request.put(`/api/store/shifts/${directId}`, {
            headers: headers(auditor),
            data: { start_time: "13:00" },
          })
        ).status(),
      ).toBe(403);
      expect(
        (
          await request.put(`/api/store/shifts/${directId}`, {
            headers: { ...headers(staff), "X-Store-ID": otherStore },
            data: { start_time: "13:00" },
          })
        ).status(),
      ).toBe(404);
      expect(
        (
          await request.put(`/api/store/shifts/${directId}`, {
            headers: headers(token),
            data: { start_time: "18:00" },
          })
        ).status(),
      ).toBe(400);
      await deleteShift(request, token, directId);
      track("SHIFT_DELETED", directId, "SHIFT", { after: { exists: "false" } });

      const removable = await submit();
      const removedShift = (
        await approveShiftRequest(request, token, removable, false)
      ).shift_id;
      track("SHIFT_CREATED", removedShift, "SHIFT", {
        source: removable,
        sourceType: "SHIFT_REQUEST",
      });
      track("SHIFT_REQUEST_APPROVED", removable, "SHIFT_REQUEST");
      const pending = await submitCastShiftChangeRequest(request, own, {
        ...slot,
        shift_id: removedShift,
        start_time: "12:00",
      });
      track("SHIFT_CHANGE_REQUEST_SUBMITTED", pending.id, "SHIFT_REQUEST", {
        actor: "CAST",
        elevated: false,
      });
      await deleteShift(request, token, removedShift);
      for (const id of [removable, pending.id])
        track("SHIFT_REQUEST_UNLINKED", id, "SHIFT_REQUEST", {
          source: removedShift,
          sourceType: "SHIFT",
          before: { shift_id: removedShift },
          after: { shift_id: "" },
        });
      track("SHIFT_DELETED", removedShift, "SHIFT", {
        after: { exists: "false" },
      });
      expect(
        (
          await request.post(
            `/api/store/shift-requests/${pending.id}/approval`,
            { headers: headers(token) },
          )
        ).status(),
      ).toBe(400);

      const attendance = {
        cast_id: cast,
        shift_id: shift,
        actual_start_at: `${date}T11:00:00`,
        waiting_place: secret,
      };
      const actualId = (await recordAttendance(request, token, attendance)).id;
      track("ATTENDANCE_RECORDED", actualId, "ATTENDANCE", {
        after: {
          shift_id: shift,
          redacted_fields_changed: "waiting_place",
          version: "0",
        },
      });
      expect(
        (
          await request.post("/api/store/attendances", {
            headers: headers(token),
            data: attendance,
          })
        ).status(),
      ).toBe(409);
      const correction = {
        business_date: date,
        actual_start_at: `${date}T11:00:00`,
        waiting_place: `${secret}-訂正`,
      };
      for (let repeat = 0; repeat < 2; repeat++) {
        await correctAttendance(request, token, actualId, correction);
        track("ATTENDANCE_CORRECTED", actualId, "ATTENDANCE", {
          sourceType: "ATTENDANCE_CORRECTION",
          after:
            repeat === 0 ? { redacted_fields_changed: "waiting_place" } : {},
        });
      }
      await cancelAttendance(request, token, actualId, secret);
      track("ATTENDANCE_CANCELLED", actualId, "ATTENDANCE", {
        after: {
          cancelled: "true",
          redacted_fields_changed: "cancelled_reason",
        },
      });
      expect(
        (
          await request.post(
            `/api/store/attendances/${actualId}/cancellation`,
            { headers: headers(token), data: { reason: secret } },
          )
        ).status(),
      ).toBe(400);
      expect(
        (
          await request.delete(`/api/store/shifts/${shift}`, {
            headers: headers(token),
          })
        ).status(),
      ).toBe(409);
      expect(
        (
          await request.get(`/api/store/shift-requests`, {
            headers: headers(token),
          })
        ).status(),
      ).toBe(200);
    }
  },
);

Then(
  "シフト監査の実変更と由来と主体だけが安全に照会できる",
  async ({ request }) => {
    const targets = new Set(expected.map((e) => e.target));
    for (const action of new Set(expected.map((e) => e.action))) {
      const events = (await listAuditEvents(request, auditor, action)).content
        .filter((e) => targets.has(e.target_id))
        .sort((a, b) => a.id - b.id);
      const matches = expected.filter((e) => e.action === action);
      expect(events).toHaveLength(matches.length);
      for (let i = 0; i < matches.length; i++) {
        const item = matches[i],
          event = events[i];
        expect(event.target_id).toBe(item.target);
        expect(event.target_type).toBe(item.type);
        expect(event.actor_type).toBe(item.actor);
        expect(event.actor_id === auditorId).toBe(item.elevated);
        expect(event.store_id).toBe(Number(STORE1_ID));
        expect(event.result).toBe("SUCCEEDED");
        expect(event.source_type ?? null).toBe(item.sourceType ?? null);
        if (item.source) expect(event.source_id).toBe(item.source);
        else if (item.sourceType) expect(event.source_id).toBeTruthy();
        else expect(event.source_id ?? null).toBeNull();
        const detail = await getAuditEvent(request, auditor, event.id);
        expect(detail.before_values).toMatchObject(item.before ?? {});
        expect(detail.after_values).toMatchObject(item.after ?? {});
        if (item.elevated)
          expect(detail.after_values.emergency_elevation_id).toBe(
            String(elevationId),
          );
        else
          expect(detail.after_values).not.toHaveProperty(
            "emergency_elevation_id",
          );
        for (const value of secrets)
          expect(JSON.stringify(detail)).not.toContain(value);
        for (const field of [
          "note",
          "waiting_place",
          "cancelled_reason",
          "name",
          "email",
          "token",
        ]) {
          expect(detail.before_values).not.toHaveProperty(field);
          expect(detail.after_values).not.toHaveProperty(field);
        }
      }
    }
    await revokeEmergencyElevation(request, auditor, elevationId);
  },
);
