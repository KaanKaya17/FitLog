-- Supabase güvenlik denetçisi (lint 0028/0029) bulgularına göre sıkılaştırma.

-- Trigger fonksiyonu API'den çağrılamasın (trigger olarak çalışmaya devam eder).
revoke all on function public.handle_new_auth_user() from public, anon, authenticated;

-- ai_usage yalnızca consume_ai_quota() (SECURITY DEFINER) üzerinden yazılır; doğrudan erişim yok.
comment on table public.ai_usage is 'AI istek kotası. Doğrudan erişim yok; yalnızca consume_ai_quota() yazar.';
comment on function public.current_user_id() is 'RLS politikalarında kullanılır; yalnızca çağıranın kendi user_id değerini döndürür.';
