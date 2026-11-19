-- =====================================================================
-- FitLog: Supabase Auth'a geçiş + Row Level Security (RLS)
-- =====================================================================
-- Bu migration şunları yapar:
--   1. public.users tablosunu auth.users'a bağlar (auth_id kolonu).
--   2. Supabase Auth'ta yeni kullanıcı oluşunca public.users satırını
--      otomatik oluşturan / mevcut satıra bağlayan bir trigger ekler.
--   3. Tüm tablolarda RLS'i açar: herkes yalnızca KENDİ verisini görür.
--   4. anon rolünün tablolara doğrudan erişimini kaldırır.
--   5. Engellenmiş (is_blocked) kullanıcıların veri erişimini sunucu
--      tarafında keser.
--
-- Supabase SQL Editor'de ya da `supabase db push` ile çalıştırılabilir.
-- Tekrar çalıştırılması güvenlidir (idempotent).
-- =====================================================================

-- 1) users <-> auth.users bağlantısı ----------------------------------
alter table public.users
    add column if not exists auth_id uuid unique references auth.users(id) on delete cascade;

-- Aynı e-postayla iki hesap açılamasın (trigger e-postayla eşleştirir).
create unique index if not exists users_email_lower_uq on public.users (lower(email));

-- Yeni kayıtlar artık şifre tutmayacak; NOT NULL kısıtı varsa kaldır.
do $$
begin
    if exists (select 1 from information_schema.columns
               where table_schema = 'public' and table_name = 'users'
                 and column_name = 'password_hash') then
        execute 'alter table public.users alter column password_hash drop not null';
    end if;
end $$;

-- 2) Auth kullanıcısı -> public.users satırı ---------------------------
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
begin
    -- Aynı e-postaya sahip eski (migrate edilen) satır varsa ona bağla.
    update public.users
       set auth_id = new.id
     where lower(email) = lower(new.email)
       and auth_id is null;

    if not found then
        insert into public.users (auth_id, email, username, first_name, last_name)
        values (
            new.id,
            new.email,
            coalesce(nullif(meta->>'username', ''), split_part(new.email, '@', 1)),
            coalesce(meta->>'first_name', ''),
            coalesce(meta->>'last_name', '')
        );
    end if;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_auth_user();

-- 3) Oturumdaki kullanıcının (engelli değilse) user_id'si --------------
create or replace function public.current_user_id()
returns bigint
language sql
stable
security definer
set search_path = ''
as $$
    select u.user_id
      from public.users u
     where u.auth_id = auth.uid()
       and coalesce(u.is_blocked, false) = false
$$;

revoke all on function public.current_user_id() from public, anon;
grant execute on function public.current_user_id() to authenticated;

-- 4) RLS: users ----------------------------------------------------------
alter table public.users enable row level security;

revoke all on public.users from anon, authenticated;
grant select on public.users to authenticated;
-- Kullanıcı yalnızca bu kolonları güncelleyebilir (is_blocked, email, auth_id DEĞİL).
grant update (first_name, last_name, gender, goal, height_cm, weight_kg,
              daily_calorie_goal, daily_water_goal_ml, fcm_token)
    on public.users to authenticated;

drop policy if exists users_select_own on public.users;
create policy users_select_own on public.users
    for select to authenticated
    using (auth_id = (select auth.uid()));          -- engelli kullanıcı da kendi is_blocked durumunu görebilsin

drop policy if exists users_update_own on public.users;
create policy users_update_own on public.users
    for update to authenticated
    using (auth_id = (select auth.uid()) and coalesce(is_blocked, false) = false)
    with check (auth_id = (select auth.uid()));

-- 5) RLS: kullanıcıya ait veri tabloları -------------------------------
do $$
declare
    t text;
begin
    foreach t in array array['water_log', 'meal_log', 'ai_meal_plans'] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on public.%I from anon', t);
        execute format('grant select, insert, update, delete on public.%I to authenticated', t);

        execute format('drop policy if exists %I on public.%I', t || '_own', t);
        execute format(
            'create policy %I on public.%I for all to authenticated '
            'using (user_id = (select public.current_user_id())) '
            'with check (user_id = (select public.current_user_id()))',
            t || '_own', t);
    end loop;
end $$;

-- 6) RLS: admin_notifications -----------------------------------------
alter table public.admin_notifications enable row level security;

revoke all on public.admin_notifications from anon, authenticated;
grant select on public.admin_notifications to authenticated;
grant update (is_read) on public.admin_notifications to authenticated;

drop policy if exists notifications_select_own on public.admin_notifications;
create policy notifications_select_own on public.admin_notifications
    for select to authenticated
    using (user_id = (select public.current_user_id()));

drop policy if exists notifications_mark_read_own on public.admin_notifications;
create policy notifications_mark_read_own on public.admin_notifications
    for update to authenticated
    using (user_id = (select public.current_user_id()))
    with check (user_id = (select public.current_user_id()));
