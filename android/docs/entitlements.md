# ClearVoice Android: entitlement plumbing

ClearVoice stays free. Every feature that exists runs on the phone and is unlocked for everyone. The existing Google Play
subscriptions (`clearvoice_premium_weekly`, `clearvoice_premium_monthly`, `clearvoice_premium_yearly`) are kept alive, and
the only thing a subscription will ever unlock is a feature that needs our servers (written answers about recordings, cloud
live transcript, server voices). None of those exists yet.

## What the code does

- `billing/PlayBillingGateway`: connects to Play Billing, lists existing SUBS purchases (state PURCHASED), disconnects.
  Read-only: no purchase flow, no acknowledge, no call to our own servers.
- `billing/EntitlementRepository`: `isPremium` is true when any of the three legacy ids is active. `billingAvailable` is
  false when Play cannot be reached; premium UI rows must be hidden then. The last good `isPremium` answer is kept across
  failed checks and restarts.
- `service/PremiumManager`: exposes the real entitlement. Its `can*` checks still return true for everything.
- Restore on launch: `EchoNoteApplication` calls `EntitlementRepository.refresh()` in the background.
- `BuildConfig.ENTITLEMENT_CHECK_ENABLED` (build.gradle.kts, default true) switches the check off at compile time. No remote
  config. Nothing shows a paywall or purchase button.

## Testing on the side-by-side debug build

The debug package `com.kreativekoala.echonote.debug` is not known to Google Play, so real billing cannot answer there. In
debug builds only, an override can force the answer:

    adb shell run-as com.kreativekoala.echonote.debug sh -c 'mkdir -p shared_prefs && cat > shared_prefs/entitlement_debug.xml' <<'X'
    <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
    <map><boolean name="billing_available" value="true" /><boolean name="is_premium" value="false" /></map>
    X

Remove the file to return to the real answer. Release builds ignore it.
