// Sends SPOT's phone notifications through Firebase Cloud Messaging: new chat messages, and activity such as
// someone joining your event or answering your invitation.
// Called by the messages and notifications triggers (private.push_new_message, private.push_new_notification).
// The database decides who gets one and checks the trigger's secret, so this function is deployed without JWT
// verification.
// Needs the FIREBASE_SERVICE_ACCOUNT secret: the JSON key of a Firebase service account for project login-a39d3.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

type Target = { token: string; recipient_id: string; title: string; body: string };
type ServiceAccount = { project_id: string; client_email: string; private_key: string };

const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});

let google: { token: string; expires: number } | null = null;

function serviceAccount(): ServiceAccount | null {
  try {
    const account = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "");
    return account?.project_id && account?.client_email && account?.private_key ? account : null;
  } catch {
    return null;
  }
}

function base64url(data: ArrayBuffer | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// A Google OAuth token for FCM, signed with the service account's key and reused until shortly before it expires.
async function accessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (google && google.expires - 60 > now) return google.token;
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = account.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${base64url(signature)}`,
    }),
  });
  if (!response.ok) throw new Error(`Google token request failed: ${response.status} ${await response.text()}`);
  const result = await response.json();
  google = { token: result.access_token, expires: now + result.expires_in };
  return google.token;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const secret = request.headers.get("x-spot-push-secret") ?? "";
  const { conversation_id, message_id, recipient_id, notification_id } = await request.json().catch(() => ({}));
  const message = typeof conversation_id === "string" && typeof message_id === "string";
  const activity = typeof recipient_id === "string" && typeof notification_id === "string";
  if (!secret || (!message && !activity)) return new Response("Bad request", { status: 400 });
  // Checked before the database marks the message as notified.
  const account = serviceAccount();
  if (!account) {
    console.error("FIREBASE_SERVICE_ACCOUNT is missing or invalid, so phone notifications are off.");
    return new Response("Notifications are not set up", { status: 503 });
  }
  const lookup = message ? "message_push_targets" : "activity_push_targets";
  const { data, error } = await supabase.rpc(lookup, message
    ? { p_secret: secret, p_conversation_id: conversation_id, p_message_id: message_id }
    : { p_secret: secret, p_recipient_id: recipient_id, p_notification_id: notification_id });
  if (error) {
    const denied = error.message.includes("Not allowed");
    if (!denied) console.error(`${lookup} failed:`, error.message);
    return new Response(denied ? "Not allowed" : "Couldn't load recipients", { status: denied ? 403 : 500 });
  }
  const targets = (data ?? []) as Target[];
  if (targets.length === 0) return Response.json({ sent: 0 });

  const token = await accessToken(account);
  const stale: string[] = [];
  let sent = 0;
  await Promise.all(targets.map(async (target) => {
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token: target.token,
          // Data only: the app checks who is signed in on the phone and which chat is open before showing it.
          data: {
            ...(message ? { type: "message", conversation_id } : { type: "activity", notification_id }),
            recipient_id: target.recipient_id,
            title: target.title,
            body: target.body,
          },
          // A chat's newer message replaces its older one while the phone is offline; activity is kept apart.
          android: { priority: "HIGH", ttl: "86400s", ...(message ? { collapse_key: conversation_id } : {}) },
        },
      }),
    });
    if (response.ok) {
      sent++;
      return;
    }
    const detail = await response.text();
    // SPOT was uninstalled or the phone has a new address: forget this one.
    if (response.status === 404 || detail.includes("UNREGISTERED")) stale.push(target.token);
    else console.error("FCM send failed:", response.status, detail);
  }));
  if (stale.length > 0) await supabase.from("push_tokens").delete().in("token", stale);
  return Response.json({ sent, removed: stale.length });
});
