// FitLog — Gemini proxy (Supabase Edge Function)
//
// Gemini API anahtarı artık APK içinde DEĞİL; Supabase secret olarak
// sunucuda tutulur:
//   supabase secrets set GEMINI_API_KEY=...
//   supabase functions deploy gemini-proxy
//
// İstemci, kullanıcının Supabase oturum JWT'si ile bu fonksiyonu çağırır.
// Fonksiyon:
//   * Geçerli, giriş yapmış ve engellenmemiş bir kullanıcı olduğunu doğrular,
//   * İstek gövdesinden yalnızca izin verilen alanları alır,
//   * Sabit modele (gemini-2.5-flash) iletir ve yanıtı geri döndürür.

import { createClient } from "jsr:@supabase/supabase-js@2";

const GEMINI_MODEL = "gemini-2.5-flash";
const GEMINI_URL =
  `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
const MAX_BODY_BYTES = 4 * 1024 * 1024; // ~4 MB (fotoğraf base64 dahil)

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: { message: "Method not allowed" } });

  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  if (!geminiKey) return json(500, { error: { message: "Server is not configured" } });

  // 1) Kullanıcıyı doğrula (anon key ile gelen istekler reddedilir)
  const authHeader = req.headers.get("Authorization") ?? "";
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );

  const { data: userData, error: userError } = await supabase.auth.getUser();
  if (userError || !userData?.user) return json(401, { error: { message: "Unauthorized" } });

  // Engellenmiş kullanıcılar current_user_id() için null döner
  const { data: appUserId, error: rpcError } = await supabase.rpc("current_user_id");
  if (rpcError || appUserId === null) return json(403, { error: { message: "Forbidden" } });

  // 2) Gövdeyi doğrula
  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return json(413, { error: { message: "Request too large" } });

  let body: Record<string, unknown>;
  try {
    body = JSON.parse(raw);
  } catch {
    return json(400, { error: { message: "Invalid JSON" } });
  }
  if (!Array.isArray(body.contents) || body.contents.length === 0 || body.contents.length > 4) {
    return json(400, { error: { message: "Invalid contents" } });
  }

  const forwarded: Record<string, unknown> = { contents: body.contents };
  if (body.generationConfig && typeof body.generationConfig === "object") {
    forwarded.generationConfig = body.generationConfig;
  }

  // 3) Gemini'ye ilet (anahtar URL yerine header'da)
  const geminiResp = await fetch(GEMINI_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": geminiKey },
    body: JSON.stringify(forwarded),
  });

  return new Response(await geminiResp.text(), {
    status: geminiResp.status,
    headers: { "Content-Type": "application/json" },
  });
});
