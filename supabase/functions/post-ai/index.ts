// AI Assist for the post composer: caption ideas and hashtags written by Google's Gemini from the post's first photo
// (or a frame of its video). Signed-in members call it from the app; nothing is applied until they choose a suggestion.
// Deployed with JWT verification, which still lets the publishable key through on its own, so a member's session is
// required below and public.claim_post_ai confirms it. That call also counts each request against the member, so one
// account can't use up the free Gemini quota that the whole app shares.
// Needs the GEMINI_API_KEY secret: an API key from Google AI Studio.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

// Fast, low-cost and on Gemini's free tier; reads images.
const MODEL = "gemini-3.5-flash-lite";
// The app sends a JPEG of at most 512 × 512, well under this.
const MAX_IMAGE_BASE64 = 2_000_000;

type AssistRequest = { image?: unknown; categories?: unknown; caption?: unknown; personal?: unknown };

const INSTRUCTIONS = `You write post captions and hashtags for SPOT, an app where college students share their talents:
singing, dancing, music, art, sports, coding, photography, cooking and more.
Look at the image and write:
- "captions": 3 short captions (each under 150 characters) in the first person, as the student posting. Make them
  natural and different in tone: one heartfelt, one playful, one simple. At most one emoji each. No hashtags.
- "tags": 8 hashtags without the # sign, lowercase letters, numbers and underscores only, each under 30 characters.
  Mix the specific talent with broader creative and campus tags.
If the student wrote a draft caption, keep its meaning and language, and make one caption a polished version of it.
Use the talent categories if given. Never invent names, places, events or facts that aren't visible or given.
Keep everything friendly and appropriate for all ages.
Reply with JSON only: {"captions": ["..."], "tags": ["..."]}`;

function json(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

function failure(message: string, status: number): Response {
  return json({ error: message }, status);
}

// The project's publishable key, or the legacy anon key on projects that don't have one.
function publishableKey(): string {
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
    if (typeof keys?.default === "string" && keys.default) return keys.default;
  } catch { /* fall back below */ }
  return Deno.env.get("SUPABASE_ANON_KEY") ?? "";
}

// Keeps the first of each distinct value (ignoring case).
function distinct(values: string[]): string[] {
  const seen = new Set<string>();
  return values.filter((value) => {
    const key = value.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

// Up to 3 captions, without wrapping quotes or hashtags (hashtags at the end are dropped; one mid-sentence keeps its word).
function captions(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return distinct(value.filter((item): item is string => typeof item === "string")
    .map((item) => item.replace(/(\s*#[\p{L}\p{N}_]+)+\s*$/u, "").replace(/#([\p{L}\p{N}_]+)/gu, "$1").replace(/\s+/g, " ").trim()
      .replace(/^["'“”]+|["'“”]+$/g, "").trim())
    .filter((item) => item.length > 0 && item.length <= 300)).slice(0, 3);
}

// Up to 10 hashtags of letters, numbers and underscores, each at most 30 characters.
function tags(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return distinct(value.filter((item): item is string => typeof item === "string")
    .map((item) => item.replace(/^#+/, "").replace(/[^\p{L}\p{N}_]/gu, ""))
    .filter((item) => item.length > 0 && item.length <= 30)).slice(0, 10);
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return failure("Method not allowed", 405);
  if (!/^Bearer [\w-]+\.[\w-]+\.[\w-]+$/.test(request.headers.get("Authorization") ?? "")) {
    return failure("Sign in to use AI Assist.", 401);
  }
  const body = await request.json().catch(() => null) as AssistRequest | null;
  const image = typeof body?.image === "string" ? body.image : "";
  if (!image || image.length > MAX_IMAGE_BASE64 || !/^[A-Za-z0-9+/]+={0,2}$/.test(image)) {
    return failure("AI Assist couldn't read this photo. Try again.", 400);
  }
  const categories = Array.isArray(body?.categories)
    ? body.categories.filter((item): item is string => typeof item === "string").map((item) => item.slice(0, 60)).slice(0, 8)
    : [];
  const draft = typeof body?.caption === "string" ? body.caption.trim().slice(0, 2000) : "";
  const personal = body?.personal === true;

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) {
    console.error("GEMINI_API_KEY is missing, so AI Assist is off.");
    return failure("AI Assist isn't set up yet.", 503);
  }

  // Runs as the member: it fails for anyone signed out, and counts this request toward their limit.
  const member = createClient(Deno.env.get("SUPABASE_URL")!, publishableKey(), {
    global: { headers: { Authorization: request.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { error: refused } = await member.rpc("claim_post_ai");
  if (refused) {
    // Messages raised for people (limits, unfinished profiles) are shown as they are.
    if (refused.code === "P0001") return failure(refused.message, 403);
    console.error("claim_post_ai failed:", refused.code, refused.message);
    return failure("Sign in again to use AI Assist.", 401);
  }

  const details = [
    `Post type: ${personal ? "a personal moment" : "a talent post"}.`,
    `Talent categories: ${categories.length ? categories.join(", ") : "none chosen"}.`,
    draft ? `The student's draft caption:\n${draft}` : "The student hasn't written a caption yet.",
  ].join("\n");

  let response: globalThis.Response;
  try {
    response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: INSTRUCTIONS }] },
        contents: [{ role: "user", parts: [{ inline_data: { mime_type: "image/jpeg", data: image } }, { text: details }] }],
        generationConfig: { responseMimeType: "application/json", temperature: 0.9, maxOutputTokens: 2048 },
      }),
      signal: AbortSignal.timeout(30_000),
    });
  } catch (error) {
    console.error("Gemini request failed:", error instanceof Error ? error.message : error);
    return failure("AI Assist took too long. Try again.", 504);
  }
  if (!response.ok) {
    const detail = (await response.text()).slice(0, 500);
    if (response.status === 429) {
      console.warn("Gemini quota reached:", detail);
      return failure("AI Assist is busy right now. Try again in a minute.", 503);
    }
    console.error("Gemini error:", response.status, detail);
    return failure("AI Assist isn't available right now. Try again later.", 502);
  }

  const result = await response.json().catch(() => null);
  const candidate = result?.candidates?.[0];
  if (result?.promptFeedback?.blockReason || candidate?.finishReason === "SAFETY" || candidate?.finishReason === "PROHIBITED_CONTENT") {
    return failure("AI Assist can't write suggestions for this photo.", 422);
  }
  const text = (candidate?.content?.parts ?? []).map((part: { text?: string }) => part.text ?? "").join("");
  let parsed: { captions?: unknown; tags?: unknown } | null = null;
  try {
    parsed = JSON.parse(text.replace(/^\s*```(?:json)?|```\s*$/g, ""));
  } catch {
    console.error("Gemini reply wasn't JSON:", text.slice(0, 300));
  }
  const suggestions = { captions: captions(parsed?.captions), tags: tags(parsed?.tags) };
  if (suggestions.captions.length === 0 && suggestions.tags.length === 0) {
    return failure("AI Assist couldn't write suggestions this time. Try again.", 502);
  }
  return json(suggestions);
});
