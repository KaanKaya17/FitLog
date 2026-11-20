-- =====================================================================
-- FitLog: temel şema / base schema
-- =====================================================================
-- Boş bir Supabase projesinde ilk çalışan migration. Uygulamanın ilk
-- sürümünden kalan ana tabloları oluşturur; sonraki migration'lar
-- (auth + RLS, v2 özellikleri) bunların üzerine kurulur.
--
-- First migration for an empty Supabase project. Creates the core tables
-- the later migrations (auth + RLS, v2 features) build on.
--
-- Idempotent: mevcut bir veritabanında hiçbir şeyi değiştirmez.
-- Idempotent: it changes nothing on an existing database.
-- =====================================================================

create table if not exists public.users (
    user_id             bigint generated always as identity primary key,
    username            varchar(50)  not null,
    email               varchar(255) not null,
    first_name          varchar(50),
    last_name           varchar(50),
    created_at          timestamptz default current_timestamp,
    daily_water_goal_ml integer default 2000,
    daily_calorie_goal  integer default 2000,
    is_blocked          boolean default false,
    gender              varchar(10),
    height_cm           numeric,
    weight_kg           numeric,
    goal                varchar(20),
    fcm_token           text
);

create table if not exists public.meal_log (
    log_id        bigint generated always as identity primary key,
    user_id       bigint not null,
    food_name     varchar(255) not null,
    serving_size  varchar(100),
    calories      integer not null,
    protein       numeric,
    carbohydrates numeric,
    fat           numeric,
    fiber         numeric,
    confidence    varchar(20),
    eaten_at      timestamptz default current_timestamp,
    constraint fk_user foreign key (user_id) references public.users(user_id) on delete cascade
);

create table if not exists public.water_log (
    log_id      bigint generated always as identity primary key,
    user_id     bigint not null,
    amount_ml   integer not null,
    consumed_at timestamptz default current_timestamp,
    constraint fk_user foreign key (user_id) references public.users(user_id) on delete cascade
);

create table if not exists public.ai_meal_plans (
    id           serial primary key,
    user_id      bigint not null references public.users(user_id) on delete cascade,
    plan_content text   not null,
    created_date date   not null default current_date
);
-- serial kolonun sayacı: oturum açmış kullanıcı satır ekleyebilsin.
-- The serial column's sequence: lets signed-in users insert rows.
grant usage, select on sequence public.ai_meal_plans_id_seq to authenticated;

-- Yönetici tarafından kullanıcılara gönderilen uygulama içi bildirimler.
-- In-app notifications sent to users by an admin.
create table if not exists public.admin_notifications (
    id         uuid primary key default gen_random_uuid(),
    user_id    integer references public.users(user_id),
    title      text,
    message    text,
    is_read    boolean default false,
    created_at timestamptz default now()
);
