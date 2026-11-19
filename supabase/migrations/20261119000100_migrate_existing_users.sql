-- =====================================================================
-- FitLog: Mevcut kullanıcıları Supabase Auth'a taşı ve düz metin
-- şifreleri sil.
-- =====================================================================
-- Eski sürümde şifreler public.users.password_hash kolonunda DÜZ METİN
-- olarak tutuluyordu. Bu script:
--   1. Her eski kullanıcı için auth.users'ta bcrypt ile hash'lenmiş
--      şifreli bir hesap oluşturur (kullanıcılar aynı şifreyle giriş
--      yapmaya devam eder).
--   2. on_auth_user_created trigger'ı satırları e-posta ile bağlar.
--   3. password_hash kolonunu tamamen siler.
--
-- ÖNCE 20261119000000_auth_and_rls.sql çalışmış olmalı.
-- Çalıştırmadan önce veritabanının yedeğini alın.
-- =====================================================================

create extension if not exists pgcrypto with schema extensions;

do $$
declare
    r record;
    new_id uuid;
begin
    if not exists (select 1 from information_schema.columns
                   where table_schema = 'public' and table_name = 'users'
                     and column_name = 'password_hash') then
        raise notice 'password_hash kolonu yok, migration zaten yapılmış.';
        return;
    end if;

    for r in
        execute $q$
            select distinct on (lower(u.email)) u.email, u.password_hash
              from public.users u
             where u.auth_id is null
               and u.email is not null
               and u.password_hash is not null
               and u.password_hash <> ''
               and not exists (select 1 from auth.users a where lower(a.email) = lower(u.email))
             order by lower(u.email), u.user_id
        $q$
    loop
        new_id := gen_random_uuid();

        insert into auth.users (
            instance_id, id, aud, role, email, encrypted_password,
            email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
            created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change
        ) values (
            '00000000-0000-0000-0000-000000000000', new_id, 'authenticated', 'authenticated',
            lower(r.email), extensions.crypt(r.password_hash, extensions.gen_salt('bf')),
            now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
            now(), now(),
            '', '', '', ''
        );

        insert into auth.identities (
            id, user_id, provider_id, identity_data, provider,
            last_sign_in_at, created_at, updated_at
        ) values (
            gen_random_uuid(), new_id, new_id::text,
            jsonb_build_object('sub', new_id::text, 'email', lower(r.email), 'email_verified', true),
            'email', now(), now(), now()
        );
    end loop;

    -- Güvenlik: bağlanamayan kullanıcı varsa şifreleri silmeden dur.
    if exists (select 1 from public.users where auth_id is null) then
        raise exception 'Bazı kullanıcılar Auth''a bağlanamadı; şifreler silinmedi.';
    end if;

    -- Düz metin şifreleri kalıcı olarak sil.
    execute 'update public.users set password_hash = null';
    execute 'alter table public.users drop column password_hash';
end $$;

-- Bağlanamayan (ör. e-postası olmayan) eski satırları kontrol etmek için:
--   select user_id, email from public.users where auth_id is null;
