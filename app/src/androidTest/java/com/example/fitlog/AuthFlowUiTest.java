package com.example.fitlog;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.navigation.NavController;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Ağ ve gerçek hesap gerektirmeyen arayüz akışları: giriş ekranı doğrulaması,
 * gezinme, onay penceresi, gizlilik ayarı ve auth-callback deep link'leri.
 *
 * Not: Testler cihazdaki yerel oturumu temizler (sunucudaki hesaba dokunmaz).
 */
@RunWith(AndroidJUnit4.class)
public class AuthFlowUiTest {

    private Context ctx;
    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        AuthManager.clearLocalSession(ctx);
        ctx.getSharedPreferences("fitlog_consent", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void tearDown() {
        if (scenario != null) scenario.close();
    }

    private void launchWithConsentAnswered() {
        ConsentManager.setGranted(ctx, false);
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    private void navigate(int destination, Bundle args) {
        scenario.onActivity(a -> {
            NavController nav = a.getNavController();
            nav.navigate(destination, args);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Uygulama açıkken gelen deep link (singleTask → onNewIntent). */
    private void deliverLink(String uri) {
        scenario.onActivity(a -> {
            // ActivityScenario etkinliği kendi intent'iyle izler; işledikten sonra geri koy.
            Intent original = a.getIntent();
            a.onNewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
            a.setIntent(original);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private int currentDestination() {
        int[] id = new int[1];
        scenario.onActivity(a -> id[0] = a.getNavController().getCurrentDestination().getId());
        return id[0];
    }

    // ── Onay penceresi ─────────────────────────────────────────────────

    @Test
    public void consentDialog_shownOnFirstLaunch_andDeclineIsRemembered() {
        scenario = ActivityScenario.launch(MainActivity.class);
        onView(withText(R.string.consent_title)).check(matches(isDisplayed()));
        onView(withText(R.string.consent_decline)).perform(click());

        assertTrue(ConsentManager.wasAsked(ctx));
        assertFalse(ConsentManager.isGranted(ctx));

        scenario.recreate();
        onView(withText(R.string.consent_title)).check(doesNotExist());
    }

    @Test
    public void consentDialog_acceptEnablesAnalytics() {
        scenario = ActivityScenario.launch(MainActivity.class);
        onView(withText(R.string.consent_accept)).perform(click());
        assertTrue(ConsentManager.isGranted(ctx));
    }

    @Test
    public void settingsPrivacySwitch_reflectsAndChangesConsent() {
        launchWithConsentAnswered();
        navigate(R.id.settingsFragment, null);

        onView(withId(R.id.switchAnalytics)).perform(scrollTo()).check(matches(isNotChecked()));
        onView(withId(R.id.switchAnalytics)).perform(click()).check(matches(isChecked()));
        assertTrue(ConsentManager.isGranted(ctx));

        onView(withId(R.id.switchAnalytics)).perform(click());
        assertFalse(ConsentManager.isGranted(ctx));
    }

    // ── Giriş ekranı ───────────────────────────────────────────────────

    @Test
    public void profileTab_whenLoggedOut_opensLogin() {
        launchWithConsentAnswered();
        scenario.onActivity(a -> a.navProfile((View) null));
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
    }

    @Test
    public void login_invalidEmail_staysOnLoginWithoutRequest() {
        launchWithConsentAnswered();
        navigate(R.id.loginFragment, null);

        onView(withId(R.id.editTextEmail)).perform(replaceText("not-an-email"));
        onView(withId(R.id.editTextPassword)).perform(replaceText("whatever"), closeSoftKeyboard());
        onView(withId(R.id.btnLogin)).perform(click());

        // Doğrulama yerelde başarısız olur: buton kilitlenmez, ekran değişmez.
        onView(withId(R.id.btnLogin)).check(matches(isEnabled()));
        assertEquals(R.id.loginFragment, currentDestination());
    }

    @Test
    public void login_forgotPasswordWithoutEmail_doesNotStartRecovery() {
        launchWithConsentAnswered();
        navigate(R.id.loginFragment, null);

        onView(withId(R.id.editTextEmail)).perform(replaceText(""), closeSoftKeyboard());
        onView(withId(R.id.tvForgotPassword)).perform(click());

        // E-posta yoksa istek atılmaz, bekleyen bir sıfırlama akışı oluşmaz.
        assertEquals(AuthManager.FLOW_OAUTH, AuthManager.pendingFlow(ctx));
    }

    @Test
    public void login_toRegisterAndBack() {
        launchWithConsentAnswered();
        navigate(R.id.loginFragment, null);

        onView(withId(R.id.tvGoToRegister)).perform(click());
        onView(withId(R.id.btnAddUser)).check(matches(isDisplayed()));
        onView(withId(R.id.tvGoToLogin)).perform(scrollTo(), click());
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
    }

    // ── Şifre sıfırlama ekranı ve deep link'ler ────────────────────────

    @Test
    public void resetScreen_withoutRecoverySession_returnsToLogin() {
        launchWithConsentAnswered();
        Bundle args = new Bundle();
        args.putBoolean(ChangePasswordFragment.ARG_RECOVERY, true);
        navigate(R.id.changePasswordFragment, args);

        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
    }

    @Test
    public void expiredResetLink_isHandledWithoutCrash() {
        ConsentManager.setGranted(ctx, false);
        new PrefManager(ctx).setPkceVerifier("test-verifier", AuthManager.FLOW_RECOVERY);

        scenario = ActivityScenario.launch(MainActivity.class);
        deliverLink("fitlog://auth-callback#error=access_denied&error_code=otp_expired");

        // Uygulama açık kalır, ana sayfada başlar.
        onView(withId(R.id.tvGreeting)).check(matches(isDisplayed()));
        assertFalse(AuthManager.hasPendingRecovery());
    }

    @Test
    public void callbackWithoutVerifier_doesNotLogIn() {
        ConsentManager.setGranted(ctx, false);
        new PrefManager(ctx).takePkceVerifier();

        scenario = ActivityScenario.launch(MainActivity.class);
        deliverLink("fitlog://auth-callback?code=bogus");

        assertFalse(AuthManager.hasSession(ctx));
    }
}
