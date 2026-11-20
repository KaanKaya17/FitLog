# Setting up your own FitLog

🇬🇧 English · [🇹🇷 Türkçe](../tr/kurulum.md)

This guide takes you from a fresh clone to FitLog running on your phone with **your own** Supabase backend. Plan on about 30 minutes. Every service used here has a free tier.

- [What you need](#what-you-need)
- [1. Get the code](#1-get-the-code)
- [2. Create a Supabase project](#2-create-a-supabase-project)
- [3. Create the database](#3-create-the-database)
- [4. Configure authentication](#4-configure-authentication)
- [5. Deploy the AI function](#5-deploy-the-ai-function)
- [6. Firebase (optional)](#6-firebase-optional)
- [7. Connect the app](#7-connect-the-app)
- [8. Run it](#8-run-it)
- [Optional: Google sign-in](#optional-google-sign-in)
- [Optional: your own package name](#optional-your-own-package-name)
- [Admin tasks](#admin-tasks)
- [Troubleshooting](#troubleshooting)

## What you need

| | Why | Required |
|---|---|---|
| [Android Studio](https://developer.android.com/studio) | Build and run the app (it bundles the right JDK) | Yes |
| An Android 13+ phone or emulator | `minSdk` is 33 | Yes |
| [Supabase](https://supabase.com) account | Database, sign-in, AI proxy | Yes |
| [Gemini API key](https://aistudio.google.com/apikey) | Meal analysis, AI coach, shopping list | For AI features |
| [Supabase CLI](https://supabase.com/docs/guides/cli) | One-command database setup and function deploy | Recommended |
| [Firebase](https://console.firebase.google.com) project | Push notifications, crash reports, analytics | No |

## 1. Get the code

```bash
git clone https://github.com/KaanKaya17/FitLog.git
```

Open the `FitLog` folder in Android Studio and let Gradle sync. The first sync will fail until step 6 and 7 are done; that is expected.

## 2. Create a Supabase project

1. In the [Supabase dashboard](https://supabase.com/dashboard) click **New project**. Pick a region close to your users and save the database password somewhere safe.
2. When it is ready, open **Project Settings → API Keys** and note:
   - the **Project URL** (`https://<project-ref>.supabase.co`)
   - the **anon** (or **publishable**) key

> ⚠️ Never use the `service_role` / secret key in the app. Everything compiled into an APK can be read by anyone who has the APK.

## 3. Create the database

The schema lives in [`supabase/migrations/`](../../supabase/migrations). The files run in name order:

| File | What it does |
|---|---|
| `20261118000000_base_schema.sql` | Core tables: `users`, `meal_log`, `water_log`, `ai_meal_plans`, `admin_notifications` |
| `20261119000000_auth_and_rls.sql` | Links `users` to Supabase Auth, adds the sign-up trigger and Row Level Security |
| `20261119000100_migrate_existing_users.sql` | Only for the original FitLog database; does nothing on a new project |
| `20261120000000_features_v2.sql` | Weight log, favourites, meal types, profile goals, AI quota, account deletion |
| `20261120000100_harden_function_grants.sql` | Tightens function permissions |

Every file is idempotent, so running one twice is harmless.

**Option A: Supabase CLI (recommended)**

```bash
supabase login
supabase link --project-ref <project-ref>
supabase db push
```

**Option B: dashboard only**

Open **SQL Editor → New query**, paste the contents of each file **in the order above**, and click **Run** after each one.

When you are done, **Table Editor** should list eight tables, each marked with RLS enabled: `users`, `meal_log`, `water_log`, `weight_log`, `favorite_meals`, `ai_meal_plans`, `ai_usage`, `admin_notifications`.

## 4. Configure authentication

In **Authentication → URL Configuration → Redirect URLs**, click **Add URL** and add:

```
fitlog://auth-callback
```

Password-reset links, email-confirmation links and Google sign-in all return to the app through this address. Without it, those links open a browser page instead of the app.

Other settings worth checking under **Authentication → Sign In / Providers → Email**:

- **Confirm email**: when on, new users must click the link in their inbox before they can sign in. The app handles both modes.
- **Minimum password length**: the app asks for 8–72 characters. Keep the Supabase minimum at 8 or lower, or some passwords the app accepts will be rejected by the server.

> 📧 Supabase's built-in email service only sends a few emails per hour and is meant for testing. Before real users sign up, set up your own SMTP server under **Authentication → Emails → SMTP Settings**.

## 5. Deploy the AI function

The Gemini key never ships in the app. The app calls the `gemini-proxy` Edge Function with the user's session. The function then checks the user, enforces a daily quota and forwards the request to Gemini.

**Option A: Supabase CLI**

```bash
supabase secrets set GEMINI_API_KEY=<your-gemini-key>
supabase secrets set AI_DAILY_LIMIT=60        # optional: requests per user per day (default 60)
supabase functions deploy gemini-proxy
```

**Option B: dashboard only**

1. **Edge Functions → Deploy a new function → Via Editor**. Name it `gemini-proxy`, replace the sample code with [`supabase/functions/gemini-proxy/index.ts`](../../supabase/functions/gemini-proxy/index.ts), and deploy. Leave **Verify JWT** turned on.
2. **Edge Functions → Secrets**: add `GEMINI_API_KEY` with your key (and `AI_DAILY_LIMIT` if you want a different limit).

Without the secret the app works, but the AI features show a server error.

## 6. Firebase (optional)

Firebase is used for push notifications (FCM), Crashlytics and Analytics. Analytics and crash reporting only switch on after the user agrees in the in-app consent dialog.

**With Firebase**

1. Create a project in the [Firebase console](https://console.firebase.google.com).
2. **Add app → Android**, package name `com.example.fitlog` (or your own, see [below](#optional-your-own-package-name)).
3. Download `google-services.json` and put it in the `app/` folder. It is already git-ignored.

**Without Firebase**

The build still needs a `google-services.json`. Copy the placeholder:

```bash
cp docs/google-services.placeholder.json app/google-services.json
```

Everything else works. Only push notifications, crash reports and analytics stay off.

## 7. Connect the app

Create or edit `local.properties` in the project root. It is git-ignored, and Android Studio usually creates it with your `sdk.dir`. Add:

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_API_KEY=<anon-or-publishable-key>
```

Then click **File → Sync Project with Gradle Files**.

## 8. Run it

Pick a device and press **Run ▶** in Android Studio, or from the terminal:

```bash
./gradlew installDebug
```

Register a new account. A matching row appears in the `users` table automatically. Add a meal, some water and a weight entry to check that everything is wired up.

To run the tests:

```bash
./gradlew testDebugUnitTest lintDebug
```

```bash
./gradlew connectedDebugAndroidTest
```

The second command needs a connected device or emulator, and it clears the local session on that device.

## Optional: Google sign-in

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), go to **Create credentials → OAuth client ID → Web application**.
2. Under **Authorized redirect URIs**, add `https://<project-ref>.supabase.co/auth/v1/callback`.
3. In Supabase, open **Authentication → Sign In / Providers → Google**. Turn it on, paste the **Client ID** and **Client Secret**, and save.

The app opens Google sign-in in a Custom Tab and comes back through `fitlog://auth-callback` (PKCE), so no Android client ID or SHA-1 is needed.

## Optional: your own package name

Google Play does not accept `com.example.*` package names. To publish your own build:

1. In `app/build.gradle.kts`, change `applicationId` (you can leave `namespace` as it is).
2. If you use Firebase, register the new package name there and download a fresh `google-services.json`.
3. Set up release signing as described in the README, then run `./gradlew bundleRelease`.

## Admin tasks

There is no admin panel. Use the **SQL Editor**, which runs with full rights.

```sql
-- Block a user: they can no longer read or write anything
update public.users set is_blocked = true where email = 'someone@example.com';

-- Send an in-app notification (shown as a dialog the next time they open the home screen)
insert into public.admin_notifications (user_id, title, message)
select user_id, 'Hello', 'Welcome to FitLog!' from public.users where email = 'someone@example.com';

-- AI usage today
select u.email, a.count from public.ai_usage a join public.users u using (user_id)
where a.day = current_date order by a.count desc;
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Build error: `File google-services.json is missing` | Do [step 6](#6-firebase-optional) |
| Gradle warning: `SUPABASE_URL / SUPABASE_API_KEY tanımlı değil` | `local.properties` is missing the values from [step 7](#7-connect-the-app) |
| Sign-in fails with "Invalid API key" | You used the wrong key, or put quotes or spaces around it |
| Reset or confirmation link opens a browser page | `fitlog://auth-callback` is missing from Redirect URLs ([step 4](#4-configure-authentication)) |
| AI shows "server error" | The `GEMINI_API_KEY` secret is missing, or the function isn't deployed ([step 5](#5-deploy-the-ai-function)) |
| AI says the daily limit is reached | Raise `AI_DAILY_LIMIT` |
| No confirmation email arrives | The built-in email rate limit was hit. Set up custom SMTP |
| App suddenly can't reach the server | Free Supabase projects pause after a week without activity. Restore it from the dashboard |
| Gradle fails with an unusual Java version | Use Android Studio's bundled JDK (**Settings → Build Tools → Gradle → Gradle JDK**) |
