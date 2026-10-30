// SPOT AI's talent check for the post composer: Google's Gemini looks at the post's first photo (or a frame of its
// video) and says which of SPOT's talent categories it shows. It replaces the model that ran on Render, which slept
// between uses and took up to a minute to wake. The reply keeps that service's shape (TalentPredictionResponse in the
// app), so the composer shows it the same way. Nothing is applied until the member taps a suggestion.
// Like post-ai: deployed with JWT verification, which still lets the publishable key through on its own, so a
// member's session is required below and public.claim_talent_ai confirms it and counts the check against the member.
// Needs the GEMINI_API_KEY secret, shared with post-ai.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

// Fast, low-cost and on Gemini's free tier; reads images.
const MODEL = "gemini-3.5-flash-lite";
// The app sends a JPEG of at most 512 × 512, well under this.
const MAX_IMAGE_BASE64 = 2_000_000;

type TalentRequest = { image?: unknown; categories?: unknown };

const INSTRUCTIONS = `You recognize talents for SPOT, an app where college students share them.
Look at the image and decide which of the given talent categories it shows someone doing or presenting.
Reply with JSON only: {"suggestions": [{"category": "<a given category, spelled exactly>", "confidence": <0 to 1>}]}
List up to 3 categories that fit, best first, with how sure you are (1 means certain). Use only the given categories.
If none of them is clearly shown (a plain selfie, scenery, a meme, a screenshot), reply {"suggestions": []}.`;

function failure(message: string, status: number): Response {
  return Response.json({ error: message }, { status });
}

// The project's publishable key, or the legacy anon key on projects that don't have one.
function publishableKey(): string {
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
    if (typeof keys?.default === "string" && keys.default) return keys.default;
  } catch { /* fall back below */ }
  return Deno.env.get("SUPABASE_ANON_KEY") ?? "";
}

// Gemini's picks, kept only when they name one of the app's categories; at most 3, best first, each once.
function suggestions(value: unknown, categories: string[]): { category: string; confidence: number }[] {
  if (!Array.isArray(value)) return [];
  const seen = new Set<string>();
  return value.flatMap((item) => {
    const named = typeof item?.category === "string" ? item.category.trim().toLowerCase() : "";
    const category = categories.find((known) => known.toLowerCase() === named);
    const confidence = Number(item?.confidence);
    if (!category || seen.has(category) || !Number.isFinite(confidence)) return [];
    seen.add(category);
    return [{ category, confidence: Math.min(Math.max(confidence, 0), 1) }];
  }).sort((a, b) => b.confidence - a.confidence).slice(0, 3);
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return failure("Method not allowed", 405);
  if (!/^Bearer [\w-]+\.[\w-]+\.[\w-]+$/.test(request.headers.get("Authorization") ?? "")) {
    return failure("Sign in to use SPOT AI.", 401);
  }
  const body = await request.json().catch(() => null) as TalentRequest | null;
  const image = typeof body?.image === "string" ? body.image : "";
  if (!image || image.length > MAX_IMAGE_BASE64 || !/^[A-Za-z0-9+/]+={0,2}$/.test(image)) {
    return failure("SPOT AI couldn't read this media. Choose a category yourself.", 400);
  }
  const categories = Array.isArray(body?.categories)
    ? body.categories.filter((item): item is string => typeof item === "string" && item.trim().length > 0 && item.length <= 40)
      .map((item) => item.trim()).slice(0, 40)
    : [];
  if (categories.length === 0) return failure("SPOT AI needs the talent categories. Update SPOT and try again.", 400);

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) {
    console.error("GEMINI_API_KEY is missing, so SPOT AI is off.");
    return failure("SPOT AI isn't set up yet. Choose a category yourself.", 503);
  }

  // Runs as the member: it fails for anyone signed out, and counts this check toward their allowance.
  const member = createClient(Deno.env.get("SUPABASE_URL")!, publishableKey(), {
    global: { headers: { Authorization: request.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { error: refused } = await member.rpc("claim_talent_ai");
  if (refused) {
    if (refused.code === "P0001") return failure(refused.message, 403);
    console.error("claim_talent_ai failed:", refused.code, refused.message);
    return failure("Sign in again to use SPOT AI.", 401);
  }

  let response: globalThis.Response;
  try {
    response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: INSTRUCTIONS }] },
        contents: [{ role: "user", parts: [
          { inline_data: { mime_type: "image/jpeg", data: image } },
          { text: `Talent categories: ${categories.join(", ")}.` },
        ] }],
        generationConfig: { responseMimeType: "application/json", temperature: 0.2, maxOutputTokens: 512 },
      }),
      signal: AbortSignal.timeout(30_000),
    });
  } catch (error) {
    console.error("Gemini request failed:", error instanceof Error ? error.message : error);
    return failure("SPOT AI took too long. Try again.", 504);
  }
  if (!response.ok) {
    const detail = (await response.text()).slice(0, 500);
    if (response.status === 429) {
      console.warn("Gemini quota reached:", detail);
      return failure("SPOT AI is busy right now. Choose a category yourself or try again in a minute.", 503);
    }
    console.error("Gemini error:", response.status, detail);
    return failure("SPOT AI isn't available right now. Choose a category yourself.", 502);
  }

  const result = await response.json().catch(() => null);
  const candidate = result?.candidates?.[0];
  if (result?.promptFeedback?.blockReason || candidate?.finishReason === "SAFETY" || candidate?.finishReason === "PROHIBITED_CONTENT") {
    return failure("SPOT AI can't check this media. Choose a category yourself.", 422);
  }
  const text = (candidate?.content?.parts ?? []).map((part: { text?: string }) => part.text ?? "").join("");
  let parsed: { suggestions?: unknown } | null = null;
  try {
    parsed = JSON.parse(text.replace(/^\s*```(?:json)?|```\s*$/g, ""));
  } catch {
    console.error("Gemini reply wasn't JSON:", text.slice(0, 300));
    return failure("SPOT AI couldn't check this media this time. Try again.", 502);
  }

  const picks = suggestions(parsed?.suggestions, categories);
  const top = picks[0];
  const percent = (confidence: number) => Math.round(confidence * 1000) / 10;
  const name = top?.category ?? "other";
  return Response.json({
    success: true,
    prediction: name,
    raw_class: name,
    spot_category: name,
    confidence: top?.confidence ?? 0,
    confidence_percent: percent(top?.confidence ?? 0),
    recognized_talent: !!top && top.confidence >= 0.5,
    needs_user_confirmation: !top || top.confidence < 0.8,
    message: top ? `Looks like ${top.category}.` : "No talent category was recognized. Choose one yourself.",
    suggestions: picks.map((pick) => ({
      tag: pick.category,
      raw_class: pick.category,
      spot_category: pick.category,
      confidence: pick.confidence,
      confidence_percent: percent(pick.confidence),
    })),
  });
});
