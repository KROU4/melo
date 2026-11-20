# Melo public stats API

This is a standalone Vercel Functions project. Create a Vercel project from this repository with the project root set to `stats-api`, then add an Upstash Redis integration and the environment variables shown in `.env.example`.

## Endpoints

- `POST /api/presence` receives an installation UUID, `android` platform, app-open and active-playback booleans, app version, and an allowlisted list of aggregate metric deltas.
- `GET /api/stats` returns public counts and aggregate metrics for today, the last 7 days, and the last 30 days.
- `GET /api/stats/live/badge.svg` returns the GitHub README badge for recent active playback sessions.

The server HMACs installation UUIDs before storage. It stores no account identifiers, track identifiers, titles, artists, search text, or request bodies. Source categories distinguish Spotify catalog provenance from YouTube Music streams and local files; they do not claim the audio provider for Spotify catalog tracks. Presence expires 90 seconds after a missed five-minute heartbeat. Distinct-install rolling windows retain pseudonymous IDs for at most 30 days; daily aggregate counters expire after 400 days. All telemetry is sent only while the user has **Share usage analytics** enabled in Melo.

## Required Vercel environment variables

- Upstash REST URL and token. The API accepts `UPSTASH_REDIS_REST_URL` / `UPSTASH_REDIS_REST_TOKEN` or Vercel Marketplace's default `STORAGE_URL` / `STORAGE_TOKEN` names.
- `MELO_STATS_HASH_SECRET` — a randomly generated secret of at least 32 characters. Keep it in Vercel environment variables; never place it in Android code or commit it.

The Android build defaults to `https://api.melo.aarushsagar.com/api/presence`; override it with `-PmeloStatsEndpoint=...` when building for another deployment.

After the first deployment, attach `api.melo.aarushsagar.com` to this Vercel project and add the DNS record Vercel requests. The live badge uses one Redis script call and has a 30-second CDN cache. The app sends a heartbeat every five minutes while it is open or playback is active, and batches aggregate metric deltas into that request.

Vercel Hobby currently includes 1 million Function invocations per month. Upstash Redis Free currently includes 500,000 commands, 250 MB, and 10 GB of monthly bandwidth. These limits fit a small project; monitor usage as the install base grows. The free database limit is one per Upstash account.
