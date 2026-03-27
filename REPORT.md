# Timezone Resolution from Country + State — Analysis Report

## Problem Statement

Given a country code and an optional state/province code, resolve the IANA timezone. The solution must be:

- International (not US-only)
- Best-effort: if state is unavailable, fall back to the country's primary timezone
- Simple, lightweight, and ideally zero-maintenance
- Java 8 compatible

## Approaches Considered

### 1. GeoNames Data Files (`cities15000.txt`)

**How it works:** Download the GeoNames cities file (~7.5MB), parse it at startup, and build in-memory maps keyed by country and country+state. For each region, the timezone of the most populous city is used as the representative timezone.

| Pros | Cons |
|---|---|
| Free, well-maintained, covers 244 countries | Requires shipping/downloading a 7.5MB data file |
| State-level granularity | File parsing on startup (~1s) |
| No external API calls at runtime | Need to re-download periodically to pick up changes |

**Verdict:** Good solution but introduces a runtime data file dependency and a loading step. Maintenance is low but not zero.

---

### 2. GeoNames Data Files with Zip Code Support

**How it works:** Extends approach 1 by adding per-country postal code files (e.g. US.txt at 619KB, CN.txt at 41KB, CA.txt at 37KB). Each postal code has lat/lng coordinates, which are matched to the nearest city using a grid-based spatial index to resolve edge cases within states (e.g. Indiana split-timezone counties, Arizona Navajo Nation DST).

| Pros | Cons |
|---|---|
| Highest accuracy for within-state edge cases | More data files to manage |
| Grid spatial index keeps lookups fast | Heavier memory footprint |
| Critical for US accuracy (IN, AZ, OR, ND edge cases) | allCountries.zip is 398MB (must use per-country files) |

**Tested edge cases that resolved correctly:**

| Zip | Location | Result |
|---|---|---|
| US 46360 | Michigan City, IN | `America/Chicago` (Central) |
| US 46204 | Indianapolis, IN | `America/Indiana/Indianapolis` (Eastern) |
| US 86515 | Window Rock, AZ (Navajo) | `America/Denver` (observes DST) |
| US 85001 | Phoenix, AZ | `America/Phoenix` (no DST) |
| US 97910 | Jordan Valley, OR | `America/Boise` (Mountain) |
| US 97201 | Portland, OR | `America/Los_Angeles` (Pacific) |
| CN 830000 | Urumqi | `Asia/Urumqi` |
| CA S4P | Regina, SK | `America/Regina` (no DST) |
| CA A1B | St. John's, NL | `America/St_Johns` (UTC-3:30) |

**Verdict:** Most accurate, but adds complexity. Eliminated because zip-level precision was deemed unnecessary for the use case, and state-level fallback is sufficient.

---

### 3. ICU4J Library (`com.ibm.icu:icu4j`)

**How it works:** IBM's Unicode library includes `TimeZone.getAvailableIDs(country)` which returns all IANA timezone IDs for a given country code. Ships its own timezone database.

| Pros | Cons |
|---|---|
| Well-maintained by IBM | ~14MB jar — very heavy for just timezone resolution |
| Ships its own tzdata | Only supports country-level, no state resolution |
| Programmatic access | Overkill dependency for this use case |

**Verdict:** Eliminated. Too heavy, and doesn't solve the state-level problem.

---

### 4. Google Time Zone API

**Endpoints:**
- Geocoding: `https://maps.googleapis.com/maps/api/geocode/json?address={country+state}&key={API_KEY}`
- Timezone: `https://maps.googleapis.com/maps/api/timezone/json?location={lat,lng}&timestamp={unix}&key={API_KEY}`

**Cost estimate for 100k lookups:**
- Geocoding API: $5.00 per 1,000 requests → **$500 for 100k**
- Timezone API: $5.00 per 1,000 requests → **$500 for 100k**
- **Total: ~$1,000 for 100k lookups** (two API calls per resolution)

| Pros | Cons |
|---|---|
| Always up to date | Requires two API calls per lookup (geocode + timezone) |
| Highly accurate | $1,000 per 100k lookups |
| Well-documented | Adds network latency per call |
| | External dependency — outage = your feature breaks |
| | Requires API key management |

**Verdict:** Eliminated. Too expensive at scale, adds latency, and the two-step geocode+timezone flow is unnecessarily complex for country+state inputs.

---

### 5. GeoNames Web API

**Endpoint:**
- `http://api.geonames.org/searchJSON?country={CC}&adminCode1={state}&maxRows=1&username={user}`
- Returns timezone in the response object

**Cost estimate for 100k lookups:**
- Free tier: 20,000 requests/day, 1,000/hour
- Premium: starts at ~$100/year for higher limits

| Pros | Cons |
|---|---|
| Free tier available | 20k/day limit — **100k lookups would take 5 days** on free tier |
| Single endpoint (no geocoding step) | Rate limited to 1,000/hour on free tier |
| Same data source as our file-based approach | Network latency per call |
| | External dependency |
| | Premium plan needed for any real throughput |

**Verdict:** Eliminated. Free tier is too restrictive, and adding a network call per timezone resolution is unnecessary when the data is static enough to hardcode.

---

### 6. TimeZoneDB API (`timezonedb.com`)

**Endpoint:**
- `http://api.timezonedb.com/v2.1/get-time-zone?key={KEY}&format=json&by=zone&zone={IANA_ID}`
- Only supports lookup by IANA zone ID or lat/lng — **not by country+state**

**Verdict:** Eliminated. Doesn't support country+state input. Would require geocoding first, adding the same two-step problem as Google's API.

---

### 7. WorldTimeAPI / Abstract API

- WorldTimeAPI: `worldtimeapi.org/api/timezone/{IANA_ID}` — only by IANA zone ID, not country+state
- Abstract API: `abstractapi.com/api/timezone` — by lat/lng, requires geocoding

**Verdict:** Both eliminated. Same fundamental limitation — no country+state input, would need geocoding.

---

### 8. Hardcoded Static Map (Chosen Solution)

**How it works:** Extract all country→timezone and state→timezone mappings from GeoNames `cities15000.txt` once, then hardcode them as a Java `HashMap` in a static initializer. The class has zero dependencies, zero I/O, and resolves timezones via pure in-memory lookup.

| Pros | Cons |
|---|---|
| Zero dependencies (only `java.util.*`) | State codes use GeoNames admin1 format (may need mapping) |
| Zero I/O, zero network calls | Doesn't handle within-state edge cases (AZ Navajo, IN counties) |
| Instant resolution (HashMap lookup) | Would need a code change if a state switches timezone ID (extremely rare) |
| Java 8 compatible | |
| No API keys, no billing, no rate limits | |
| 100k lookups = microseconds, $0 cost | |

**Cost estimate for 100k lookups:** Effectively **$0** and **sub-millisecond** total.

## Solution Summary

### What the map covers

| Level | Entries | Source |
|---|---|---|
| Country → timezone | 244 | Most populous city per country from `cities15000.txt` |
| State → timezone | 566 | Most populous city per state, only for 24 multi-timezone countries |

### Multi-timezone countries (24 total, 566 state entries)

| Country | Zones | Country | Zones |
|---|---|---|---|
| AR (Argentina) | 12 | MN (Mongolia) | 2 |
| AU (Australia) | 8 | MX (Mexico) | 12 |
| BR (Brazil) | 15 | MY (Malaysia) | 2 |
| CA (Canada) | 12 | PG (Papua New Guinea) | 2 |
| CD (DR Congo) | 2 | PS (Palestine) | 3 |
| CL (Chile) | 3 | PT (Portugal) | 3 |
| CN (China) | 3 | RU (Russia) | 23 |
| CY (Cyprus) | 2 | SO (Somalia) | 2 |
| ES (Spain) | 3 | UA (Ukraine) | 2 |
| GE (Georgia) | 2 | US (United States) | 14 |
| ID (Indonesia) | 5 | UZ (Uzbekistan) | 3 |
| KZ (Kazakhstan) | 7 | VN (Vietnam) | 2 |

### Maintenance model

| Concern | Who handles it | Action required |
|---|---|---|
| DST rule changes (country abolishes/adopts DST) | JVM `tzdata` updates | Keep JDK updated (already done for security) |
| DST transition date changes | JVM `tzdata` updates | Keep JDK updated |
| UTC offset changes for a timezone | JVM `tzdata` updates | Keep JDK updated |
| State switches to a different IANA timezone ID | Our hardcoded map | Change the entry (happens ~once per decade globally) |
| New country appears | Our hardcoded map | Add one entry |

The hardcoded map stores **IANA timezone identifiers** (e.g., `America/New_York`), not offsets or DST rules. The JVM's built-in `TimeZone` class resolves the identifier to the correct offset and DST state at runtime, using its bundled `tzdata` database. As long as the JDK is kept updated, DST changes are handled automatically without any map changes.

### API

```java
// State known → state-level resolution
Optional<TimeZone> tz = TimezoneResolver.resolve("US", "CA");
// → America/Los_Angeles

// State unknown → country-level fallback
Optional<TimeZone> tz = TimezoneResolver.resolve("BR", null);
// → America/Sao_Paulo

// Unknown country → empty
Optional<TimeZone> tz = TimezoneResolver.resolve("XX", null);
// → Optional.empty()

// Unknown state → country fallback
Optional<TimeZone> tz = TimezoneResolver.resolve("US", "NOPE");
// → America/New_York
```

### File

Single self-contained class: `TimezoneResolver.java` (902 lines, ~810 of which are map entries).

### Known Limitations

1. **Within-state timezone splits** (e.g., Indiana counties, Arizona Navajo Nation, eastern Oregon) are not resolved — the dominant timezone per state is used. Zip-level precision was evaluated and is available as an enhancement if needed.
2. **State codes follow GeoNames admin1 conventions.** US states use two-letter codes (`CA`, `NY`), but other countries use numeric codes (`BR: 27`, `CA: 08`). The mapping from human-readable names to admin1 codes is available at `https://download.geonames.org/export/dump/admin1CodesASCII.txt`.
3. **Overseas territories** may have their own country code (e.g., `GF` for French Guiana instead of `FR`), which is handled correctly since they appear as separate entries in the country map.
