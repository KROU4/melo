# Melo Privacy Policy

Last updated: November 20, 2026

Melo uses basic app-usage analytics to understand how the app is used and help improve it.

## Usage analytics

Analytics is on by default. When enabled, Firebase Analytics helps us estimate app sessions and daily or monthly active installations. Firebase automatically receives app-lifecycle and engagement signals, a per-install app-instance identifier, app and Android version details, and basic device information. Google Analytics may also infer a broad region, such as a country, from network information.

When enabled, Melo also sends aggregate telemetry to `api.melo.aarushsagar.com`. It includes a random installation ID, app version, whether the app is open, whether the player reports active playback, and aggregate counters for playback starts, reported playback time, buffering, broad error categories, and selected feature use. Installation IDs are HMAC-hashed on the server. Rolling active-installation metrics retain those pseudonymous hashes for up to 30 days; daily aggregate counters expire after 400 days; presence expires 90 seconds after a missed five-minute heartbeat. Public stats contain counts only.

Melo does not send music account details, Spotify cookies, track or artist names, track IDs, searches, playlists, queue contents, or listening history. Playback time is an estimate based on player-reported progress and does not prove that audio was audible. Automatic screen-view reporting, Android Advertising ID collection, and ad-personalization signals are disabled.

You can switch analytics off anytime in **Settings → Privacy**. This stops future analytics collection by Melo. A presence count may remain visible for up to the heartbeat expiry period; previously sent pseudonymous activity can remain in rolling active-installation counts for up to 30 days, and daily aggregate counters expire after 400 days. Data already sent to Firebase remains subject to Firebase/Google Analytics retention.

Google processes Analytics data to provide its reporting service. See [Google’s privacy information](https://policies.google.com/privacy) and [Firebase’s Android data disclosure](https://firebase.google.com/docs/android/play-data-disclosure).

## Music services and on-device data

When you connect a music service, search its catalog, or play content, that service receives the requests needed to provide the feature. Its own privacy policy applies. Melo stores app settings, caches, and local downloads on your device; these are not sent to Firebase Analytics.

Questions or corrections? [Open an issue in the Melo repository](https://github.com/aarush-io/melo/issues).
