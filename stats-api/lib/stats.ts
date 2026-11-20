import { createHmac } from "node:crypto";
import { Redis } from "@upstash/redis";

export type PlaybackSource = "spotify_catalog" | "youtube_music" | "local" | "cast" | "other";
export type PlaybackError = "network" | "decoder" | "authorization" | "unavailable" | "other";
export type Feature = "search" | "radio" | "lyrics" | "downloads" | "queue" | "podcasts" | "other";

export type Metric =
  | { type: "play_start"; source: PlaybackSource }
  | { type: "reported_listening_ms"; amount: number; source: PlaybackSource }
  | { type: "buffering_ms"; amount: number; source: PlaybackSource }
  | { type: "playback_error"; source: PlaybackSource; category: PlaybackError }
  | { type: "feature_use"; feature: Feature };

export type PresenceInput = {
  id: string;
  platform: "android";
  appOpen: boolean;
  playbackActive: boolean;
  appVersion: string;
  metrics: Metric[];
};

const HEARTBEAT_SECONDS = 300;
const PRESENCE_TTL_MS = (HEARTBEAT_SECONDS + 90) * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const INSTALL_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SOURCES = new Set<PlaybackSource>(["spotify_catalog", "youtube_music", "local", "cast", "other"]);
const ERRORS = new Set<PlaybackError>(["network", "decoder", "authorization", "unavailable", "other"]);
const FEATURES = new Set<Feature>(["search", "radio", "lyrics", "downloads", "queue", "podcasts", "other"]);

let redisClient: Redis | undefined;

function redis(): Redis {
  if (redisClient) return redisClient;
  const url = process.env.UPSTASH_REDIS_REST_URL ?? process.env.STORAGE_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN ?? process.env.STORAGE_TOKEN;
  if (!url || !token) {
    throw new Error("Stats storage is not configured.");
  }
  redisClient = new Redis({ url, token });
  return redisClient;
}

function installationKey(id: string): string {
  const secret = process.env.MELO_STATS_HASH_SECRET;
  if (!secret || secret.length < 32) throw new Error("Stats hashing is not configured.");
  return createHmac("sha256", secret).update(id).digest("hex");
}

function dateKey(time: number): string {
  return new Date(time).toISOString().slice(0, 10);
}

function metricField(metric: Metric, appVersion: string): [string, number] {
  switch (metric.type) {
    case "play_start": return [`starts:${metric.source}:${appVersion}`, 1];
    case "reported_listening_ms": return [`listening_ms:${metric.source}:${appVersion}`, metric.amount];
    case "buffering_ms": return [`buffering_ms:${metric.source}:${appVersion}`, metric.amount];
    case "playback_error": return [`errors:${metric.source}:${metric.category}:${appVersion}`, 1];
    case "feature_use": return [`features:${metric.feature}:${appVersion}`, 1];
  }
}

export function parsePresence(value: unknown): PresenceInput | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const body = value as Record<string, unknown>;
  if (typeof body.id !== "string" || !INSTALL_ID_PATTERN.test(body.id)) return null;
  if (body.platform !== "android") return null;
  if (typeof body.appOpen !== "boolean" || typeof body.playbackActive !== "boolean") return null;
  if (typeof body.appVersion !== "string" || !/^[0-9A-Za-z.+_-]{1,32}$/.test(body.appVersion)) return null;
  if (!Array.isArray(body.metrics) || body.metrics.length > 24) return null;

  const metrics: Metric[] = [];
  for (const raw of body.metrics) {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
    const event = raw as Record<string, unknown>;
    if (event.type === "play_start" && SOURCES.has(event.source as PlaybackSource)) {
      metrics.push({ type: "play_start", source: event.source as PlaybackSource });
    } else if ((event.type === "reported_listening_ms" || event.type === "buffering_ms") &&
      SOURCES.has(event.source as PlaybackSource) && Number.isInteger(event.amount) &&
      (event.amount as number) > 0 && (event.amount as number) <= HEARTBEAT_SECONDS * 1000) {
      metrics.push({ type: event.type, source: event.source as PlaybackSource, amount: event.amount as number });
    } else if (event.type === "playback_error" && SOURCES.has(event.source as PlaybackSource) &&
      ERRORS.has(event.category as PlaybackError)) {
      metrics.push({ type: "playback_error", source: event.source as PlaybackSource, category: event.category as PlaybackError });
    } else if (event.type === "feature_use" && FEATURES.has(event.feature as Feature)) {
      metrics.push({ type: "feature_use", feature: event.feature as Feature });
    } else {
      return null;
    }
  }

  return {
    id: body.id,
    platform: "android",
    appOpen: body.appOpen,
    playbackActive: body.playbackActive,
    appVersion: body.appVersion,
    metrics,
  };
}

const RECORD_SCRIPT = `
local now = tonumber(ARGV[1])
local id = ARGV[2]
local open = ARGV[3] == '1'
local playing = ARGV[4] == '1'
local ttl = tonumber(ARGV[5])
local metrics = cjson.decode(ARGV[6])
local requests = redis.call('INCR', KEYS[8])
if requests == 1 then redis.call('EXPIRE', KEYS[8], 60) end
if requests > 45 then return {-1} end

redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - ttl)
redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, now - ttl)
if open then redis.call('ZADD', KEYS[1], now, id) else redis.call('ZREM', KEYS[1], id) end
if playing then redis.call('ZADD', KEYS[2], now, id) else redis.call('ZREM', KEYS[2], id) end

redis.call('ZADD', KEYS[3], now, id)
redis.call('ZADD', KEYS[4], now, id)
redis.call('ZADD', KEYS[5], now, id)
redis.call('ZREMRANGEBYSCORE', KEYS[3], 0, now - 86400000)
redis.call('ZREMRANGEBYSCORE', KEYS[4], 0, now - 604800000)
redis.call('ZREMRANGEBYSCORE', KEYS[5], 0, now - 2592000000)
redis.call('EXPIRE', KEYS[3], 3024000)
redis.call('EXPIRE', KEYS[4], 3024000)
redis.call('EXPIRE', KEYS[5], 3024000)

redis.call('ZREMRANGEBYSCORE', KEYS[6], 0, now - ttl)
if open or playing then redis.call('ZADD', KEYS[6], now, id) else redis.call('ZREM', KEYS[6], id) end
redis.call('EXPIRE', KEYS[6], ttl * 2 / 1000)
redis.call('SADD', KEYS[9], ARGV[7])
redis.call('EXPIRE', KEYS[9], 34560000)

if #metrics > 0 then
  for _, metric in ipairs(metrics) do redis.call('HINCRBY', KEYS[7], metric[1], metric[2]) end
  redis.call('EXPIRE', KEYS[7], 34560000)
end
return {1}
`;

const COUNTS_SCRIPT = `
local now = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - ttl)
redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, now - ttl)
redis.call('ZREMRANGEBYSCORE', KEYS[3], 0, now - 86400000)
redis.call('ZREMRANGEBYSCORE', KEYS[4], 0, now - 604800000)
redis.call('ZREMRANGEBYSCORE', KEYS[5], 0, now - 2592000000)
return {
  redis.call('ZCARD', KEYS[1]), redis.call('ZCARD', KEYS[2]),
  redis.call('ZCARD', KEYS[3]), redis.call('ZCARD', KEYS[4]), redis.call('ZCARD', KEYS[5])
}
`;

async function activeCounts(now: number, db = redis()): Promise<number[]> {
  const result = await db.eval(COUNTS_SCRIPT,
    ["melo:presence:open", "melo:presence:playback", "melo:active:24h", "melo:active:7d", "melo:active:30d"],
    [now, PRESENCE_TTL_MS]) as number[];
  return result.map(Number);
}

export async function recordPresence(input: PresenceInput): Promise<boolean> {
  const db = redis();
  const now = Date.now();
  const id = installationKey(input.id);
  const currentDay = dateKey(now);
  const metrics = input.metrics.map(metric => metricField(metric, input.appVersion));
  const rateKey = `melo:rate:${id}:${Math.floor(now / 60_000)}`;
  const result = await db.eval(RECORD_SCRIPT,
    ["melo:presence:open", "melo:presence:playback", "melo:active:24h", "melo:active:7d",
      "melo:active:30d", `melo:presence:version:${input.appVersion}`, `melo:metrics:${currentDay}`, rateKey,
      "melo:presence:versions"],
    [now, id, input.appOpen ? 1 : 0, input.playbackActive ? 1 : 0, PRESENCE_TTL_MS, JSON.stringify(metrics), input.appVersion]) as number[];
  return Number(result[0]) === 1;
}

async function metricsForDays(days: number, now: number, db: Redis): Promise<Record<string, number>> {
  const keys = Array.from({ length: days }, (_, offset) => {
    const day = dateKey(now - offset * DAY_MS);
    return `melo:metrics:${day}`;
  });
  const hashes = await Promise.all(keys.map(key => db.hgetall<Record<string, string | number>>(key)));
  const result: Record<string, number> = {};
  for (const hash of hashes) {
    for (const [field, value] of Object.entries(hash ?? {})) {
      result[field] = (result[field] ?? 0) + Number(value || 0);
    }
  }
  return result;
}

export async function publicStats(): Promise<Record<string, unknown>> {
  const db = redis();
  const now = Date.now();
  const [counts, today, last7Days, last30Days, versions] = await Promise.all([
    activeCounts(now, db),
    metricsForDays(1, now, db),
    metricsForDays(7, now, db),
    metricsForDays(30, now, db),
    db.smembers<string[]>("melo:presence:versions"),
  ]);
  const activeByVersion = Object.fromEntries(await Promise.all((versions ?? []).map(async version => {
    const key = `melo:presence:version:${version}`;
    await db.zremrangebyscore(key, 0, now - PRESENCE_TTL_MS);
    return [version, Number(await db.zcard(key))] as const;
  })));
  return {
    updatedAt: new Date(now).toISOString(),
    appOpenNow: counts[0],
    activePlaybackNow: counts[1],
    activeInstalls: { last24Hours: counts[2], last7Days: counts[3], last30Days: counts[4] },
    activeByVersion,
    todayUtc: today,
    last7DaysUtc: last7Days,
    last30DaysUtc: last30Days,
    presenceExpiresAfterSeconds: Math.floor(PRESENCE_TTL_MS / 1000),
  };
}

export async function livePlaybackCount(): Promise<number> {
  return (await activeCounts(Date.now()))[1] ?? 0;
}
