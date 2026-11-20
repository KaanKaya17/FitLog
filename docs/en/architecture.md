# How FitLog works

🇬🇧 English · [🇹🇷 Türkçe](../tr/mimari.md)

This document explains how the code is organised, so you can find your way around before changing it. For installation, see the [setup guide](setup.md).

## The big picture

```
┌──────────────────────── Android app (Java) ────────────────────────┐
│ Fragment ──► ViewModel (LiveData) ──► FitLogRepository             │
│                                         │        │                  │
│                                         │        └─► OfflineQueue ──► SyncWorker
│                                         ▼            (Room)          (WorkManager)
│                         Retrofit / OkHttp                           │
└─────────────┬───────────────────┬────────────────────┬─────────────┘
              ▼                   ▼                    ▼
     Supabase PostgREST    Supabase Auth      Edge Function gemini-proxy ──► Gemini 2.5 Flash
     (tables + RLS)        (email, Google)    (checks user, quota)
              
     Open Food Facts (barcodes, separate HTTP client, no Supabase token)
```

- **There is no custom backend server.** The app talks to Supabase directly, and **Row Level Security** decides what each user may read or write.
- **The only server code is one Edge Function**, `gemini-proxy`, which keeps the Gemini key off the device.

## Project layout

```
app/src/main/java/com/example/fitlog/
├── *Fragment.java         screens (one per destination in nav_graph.xml)
├── ui/                    ViewModels, chart views, shared UI helpers
├── data/                  repository, offline queue, caches, background workers
├── domain/                pure Java logic with no Android dependencies, fully unit-tested
├── AuthManager.java       sign-in, sign-up, PKCE, password reset, token refresh
├── *Receiver.java         alarms: water and meal reminders, boot
├── TodayWidget.java       home-screen widget
└── WaterTileService.java  "+250 ml" quick-settings tile
supabase/
├── migrations/            database schema, applied in name order
└── functions/gemini-proxy Edge Function (Deno / TypeScript)
```

### Screens → ViewModels

| Screen | Fragment | ViewModel |
|---|---|---|
| Home (today's summary) | `HomeFragment` | `HomeViewModel` |
| Add meal (photo, text, barcode) | `AddMealFragment` | `AddMealViewModel` |
| Meals of a day | `MealListFragment` | `MealListViewModel` |
| Water | `WaterTrackFragment` | `WaterViewModel` |
| Weight | `WeightFragment` | `WeightViewModel` |
| History (7/30 days, streaks) | `HistoryFragment` | `HistoryViewModel` |
| AI coach / chat | `AiCoachFragment`, `AiChatFragment` | `AiCoachViewModel`, `AiChatViewModel` |
| Profile, settings, account | `ProfileFragment`, `EditProfileFragment`, `SettingsFragment`, `ChangePasswordFragment` | `ProfileViewModel`, `AccountViewModel`, `ExportViewModel` |
| Sign-in / sign-up | `LoginFragment`, `RegisterFragment` | (uses `AuthManager` directly) |

## Data flow

1. A **Fragment** observes `LiveData<Resource<T>>` from its **ViewModel**. `Resource` is loading, success or error.
2. The **ViewModel** runs repository calls on a background executor (`AppExecutors`) through `BaseViewModel.load(...)`.
3. **`FitLogRepository`** is the only class that knows about the network. It builds PostgREST queries (`SupabaseApi`), maps errors to `ApiException`, and:
   - caches recent reads (`ReadCache`), so screens open instantly and also work offline;
   - when a write fails because the device is offline, stores it in **`OfflineQueue`** (Room). **`SyncWorker`** sends it once the network is back.
4. **`ServiceLocator`** wires these objects together. There is no DI framework.

## Authentication

- Sign-in uses the **Supabase Auth** REST API (`SupabaseAuthApi`). The access and refresh tokens are stored in `EncryptedSharedPreferences` (`PrefManager`). An OkHttp interceptor in `RetrofitClient` refreshes the access token shortly before it expires, and retries a request once if the server still answers 401.
- Google sign-in, password reset and email confirmation all use **PKCE**:
  1. The app creates a verifier and challenge (`domain/Pkce`).
  2. It opens the link, and Supabase redirects back to `fitlog://auth-callback?code=…`.
  3. `MainActivity` catches the link and `AuthManager.handleAuthCallback` exchanges the code for a session.
  4. A password-reset link opens `ChangePasswordFragment` in "set new password" mode.
- When someone signs up, the database trigger `handle_new_auth_user` creates their `public.users` row. The app never writes that row's ID itself.

## Database and security

| Table | Contents |
|---|---|
| `users` | Profile and goals. Linked to `auth.users` through `auth_id` |
| `meal_log` | Meals: name, portion, kcal, macros, meal type, time |
| `water_log` | Water entries in ml |
| `weight_log` | Weight entries |
| `favorite_meals` | Saved meals for one-tap logging |
| `ai_meal_plans` | The AI coach's daily plan (cached per day) |
| `ai_usage` | AI requests per user per day (quota) |
| `admin_notifications` | Messages an admin sends to a user |

- **RLS is on for every table.** Policies compare `user_id` with `current_user_id()`, which maps the signed-in Supabase user to their row. For a blocked user (`is_blocked = true`) it returns nothing, which cuts off all of their access on the server.
- Users can update only some columns of `users` (column-level `GRANT`). For example, they cannot unblock themselves.
- `delete_my_account()` deletes all of the user's rows and their auth account. The Play Store requires in-app account deletion.
- `consume_ai_quota()` is the only way to write to `ai_usage`.

## AI features

All Gemini calls go through `GeminiNutritionService` → `gemini-proxy`. The function:

1. rejects requests without a valid user session, and blocked users;
2. counts the request against `AI_DAILY_LIMIT` (`consume_ai_quota`);
3. passes on only whitelisted fields, pins the model (`gemini-2.5-flash`), and limits body size and output length.

| Feature | Input | Output |
|---|---|---|
| Meal analysis | Photo (base64) or text | JSON: name, portion, kcal, macros, confidence |
| Daily plan | Profile and last 7 days (`CoachContext`) | Text plan, saved in `ai_meal_plans` |
| Coach chat | Conversation and context | Text; history stays in memory on the device |
| Shopping list | Today's plan | Grouped list, shareable |

Prompts live in `res/values*/strings.xml`, so each language gets answers in its own language.

## Background work and system integration

| What | Where | How |
|---|---|---|
| Water reminders | `WaterReminderReceiver`, `ReminderScheduler` | AlarmManager; respects quiet hours |
| Meal reminders | `MealReminderReceiver` | Alarms at 10:00, 14:00 and 20:00; only if that meal is still missing |
| Weekly summary | `WeeklySummaryWorker` | WorkManager, Sundays at 20:00 |
| Offline sync | `SyncWorker` | WorkManager, runs when the network is available |
| Reschedule after reboot | `BootReceiver` | |
| Quick settings tile | `WaterTileService` | Adds 250 ml without opening the app |
| Home-screen widget | `TodayWidget` | Calories and water progress, +250 ml button |
| Push notifications | `MyFirebaseMessagingService` | FCM; the token is saved in `users.fcm_token` |

## Pure logic (`domain/`)

These classes have no Android dependencies and are covered by unit tests:

- `NutritionCalculator`: Mifflin-St Jeor BMR × activity level → calorie, macro and water goals
- `DateRanges`: the user's local day converted to UTC ranges for queries
- `StreakCalculator`, `HistoryAggregator`: streaks, averages and chart data
- `FoodMath`: portion scaling
- `CsvExporter`: data export (CSV in a ZIP)
- `Pkce`, `MealType`, `WeeklySummary`

## Localisation and theming

- Languages: English (`values`), Turkish (`values-tr`), Greek (`values-el`). `LocaleHelper` switches the language inside the app. Lint fails the build if a string is missing a translation, so add every new string to all three files.
- Light, dark and system themes. Charts are lightweight custom views (`BarChartView`, `LineChartView`) that follow the theme.

## Privacy

- Firebase Analytics and Crashlytics are **off by default** (in the manifest). `ConsentManager` turns them on only after the user agrees, and the user can change it any time under Settings → Privacy.
- Without your own `google-services.json`, Gradle generates a placeholder and sets `BuildConfig.FIREBASE_CONFIGURED = false`. The app then skips FCM and never enables analytics or crash reporting.
- All per-installation values (`SUPABASE_URL`, `SUPABASE_API_KEY`, `PRIVACY_POLICY_URL`, release signing) come from `local.properties` or environment variables. See `app/build.gradle.kts`.
- Barcode lookups use a separate HTTP client, so the Supabase token never reaches Open Food Facts.
- Debug logs are removed from release builds by R8.

## Tests and CI

| Kind | Location | Command |
|---|---|---|
| Unit tests (domain, parsing, repository with `MockWebServer`) | `app/src/test` | `./gradlew testDebugUnitTest` |
| UI tests (Espresso: auth flows, deep links) | `app/src/androidTest` | `./gradlew connectedDebugAndroidTest` |
| Lint (including missing translations) | | `./gradlew lintDebug` |

GitHub Actions ([`.github/workflows/android.yml`](../../.github/workflows/android.yml)) runs unit tests, lint and a debug build on every push to `master` and on every pull request. It uses placeholder config, so no secrets are needed.

## Contributing

1. Fork the repo and create a branch.
2. Keep business logic in `domain/` when you can, and add a unit test for it.
3. Add new strings in all three languages.
4. Put database changes in a **new** migration file. Never edit one that has already run, and keep it idempotent (`if not exists`, `drop policy if exists`, …).
5. Run `./gradlew testDebugUnitTest lintDebug` before opening a pull request.
