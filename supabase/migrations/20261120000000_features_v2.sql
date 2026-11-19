-- =====================================================================
-- FitLog v2: kilo takibi, favori öğünler, öğün türü, profil hedefleri,
-- AI kullanım kotası ve hesap silme.
-- Önce 20261119000000_auth_and_rls.sql çalışmış olmalı. Idempotent.
-- =====================================================================

-- 1) Profil alanları ------------------------------------------------------
alter table public.users
    add column if not exists birth_year          integer,
    add column if not exists activity_level      text,
    add column if not exists target_weight_kg    real,
    add column if not exists daily_protein_goal_g integer,
    add column if not exists daily_carbs_goal_g  integer,
    add column if not exists daily_fat_goal_g    integer;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'users_birth_year_check') then
        alter table public.users add constraint users_birth_year_check
            check (birth_year is null or birth_year between 1900 and 2100);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'users_activity_level_check') then
        alter table public.users add constraint users_activity_level_check
            check (activity_level is null or activity_level in
                   ('sedentary', 'light', 'moderate', 'active', 'very_active'));
    end if;
    if not exists (select 1 from pg_constraint where conname = 'users_target_weight_check') then
        alter table public.users add constraint users_target_weight_check
            check (target_weight_kg is null or target_weight_kg between 20 and 400);
    end if;
end $$;

grant update (birth_year, activity_level, target_weight_kg,
              daily_protein_goal_g, daily_carbs_goal_g, daily_fat_goal_g)
    on public.users to authenticated;

-- Google ile girişte meta veride first_name yerine full_name/name gelir.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
    full_name text := coalesce(meta->>'full_name', meta->>'name', '');
begin
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
            coalesce(nullif(meta->>'first_name', ''), split_part(full_name, ' ', 1), ''),
            coalesce(nullif(meta->>'last_name', ''),
                     nullif(trim(substr(full_name, length(split_part(full_name, ' ', 1)) + 1)), ''), '')
        );
    end if;
    return new;
end;
$$;

-- 2) Öğün türü + sorgu indeksleri -----------------------------------------
alter table public.meal_log add column if not exists meal_type text;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'meal_log_meal_type_check') then
        alter table public.meal_log add constraint meal_log_meal_type_check
            check (meal_type is null or meal_type in ('breakfast', 'lunch', 'dinner', 'snack'));
    end if;
end $$;

create index if not exists meal_log_user_eaten_idx   on public.meal_log  (user_id, eaten_at desc);
create index if not exists water_log_user_consumed_idx on public.water_log (user_id, consumed_at desc);

-- 3) Kilo kayıtları -------------------------------------------------------
create table if not exists public.weight_log (
    id         bigint generated always as identity primary key,
    user_id    integer not null references public.users(user_id) on delete cascade,
    weight_kg  real    not null check (weight_kg between 20 and 400),
    logged_at  timestamptz not null default now()
);
create index if not exists weight_log_user_logged_idx on public.weight_log (user_id, logged_at desc);

-- 4) Favori öğünler -------------------------------------------------------
create table if not exists public.favorite_meals (
    id            bigint generated always as identity primary key,
    user_id       integer not null references public.users(user_id) on delete cascade,
    food_name     text    not null check (length(food_name) between 1 and 200),
    serving_size  text,
    calories      integer not null check (calories between 0 and 10000),
    protein       real    not null default 0,
    carbohydrates real    not null default 0,
    fat           real    not null default 0,
    fiber         real    not null default 0,
    created_at    timestamptz not null default now()
);
create unique index if not exists favorite_meals_user_name_uq
    on public.favorite_meals (user_id, lower(food_name));

-- RLS: kullanıcı yalnızca kendi kayıtları
do $$
declare
    t text;
begin
    foreach t in array array['weight_log', 'favorite_meals'] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on public.%I from anon', t);
        execute format('grant select, insert, update, delete on public.%I to authenticated', t);
        execute format('drop policy if exists %I on public.%I', t || '_own', t);
        execute format(
            'create policy %I on public.%I for all to authenticated '
            'using (user_id = public.current_user_id()) '
            'with check (user_id = public.current_user_id())',
            t || '_own', t);
    end loop;
end $$;

-- 5) AI kullanım kotası (maliyet / kötüye kullanım sınırı) ---------------
create table if not exists public.ai_usage (
    user_id  integer not null references public.users(user_id) on delete cascade,
    day      date    not null,
    count    integer not null default 0,
    primary key (user_id, day)
);
alter table public.ai_usage enable row level security;
revoke all on public.ai_usage from anon, authenticated;   -- yalnızca aşağıdaki fonksiyon yazar

-- Kota uygunsa sayacı artırıp true döner. Edge Function her istekte çağırır.
create or replace function public.consume_ai_quota(daily_limit integer default 60)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid integer := public.current_user_id();
    new_count integer;
begin
    if uid is null then
        return false;
    end if;
    insert into public.ai_usage (user_id, day, count)
    values (uid, current_date, 1)
    on conflict (user_id, day) do update set count = public.ai_usage.count + 1
    returning count into new_count;
    return new_count <= daily_limit;
end;
$$;

revoke all on function public.consume_ai_quota(integer) from public, anon;
grant execute on function public.consume_ai_quota(integer) to authenticated;

-- 6) Hesap silme (Play Store zorunluluğu) --------------------------------
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    auth_uid uuid := auth.uid();
    uid integer;
begin
    if auth_uid is null then
        raise exception 'not authenticated';
    end if;
    select user_id into uid from public.users where auth_id = auth_uid;

    if uid is not null then
        delete from public.water_log           where user_id = uid;
        delete from public.meal_log            where user_id = uid;
        delete from public.ai_meal_plans       where user_id = uid;
        delete from public.admin_notifications where user_id = uid;
        delete from public.weight_log          where user_id = uid;
        delete from public.favorite_meals      where user_id = uid;
        delete from public.ai_usage            where user_id = uid;
        delete from public.users               where user_id = uid;
    end if;
    delete from auth.users where id = auth_uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
