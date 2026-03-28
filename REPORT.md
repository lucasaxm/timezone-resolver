# Timezone Resolution from Country + State + Phone — Analysis Report

## Problem Statement

Given a country code, an optional state/province code, and an optional phone number, resolve the [IANA timezone](https://www.iana.org/time-zones). The solution must be:

- International (not US-only)
- Best-effort with fallback chain: state → phone → country (varies by country, see below)
- Simple, lightweight, and ideally zero-maintenance
- Java 8 compatible

## Approaches Considered

### 1. GeoNames Data Files ([`cities15000.txt`](https://download.geonames.org/export/dump/cities15000.zip))

**How it works:** Download the [GeoNames cities file](https://download.geonames.org/export/dump/) (~7.5MB), parse it at startup, and build in-memory maps keyed by country and country+state. For each region, the timezone of the most populous city is used as the representative timezone.

- Data file: [`cities15000.zip`](https://download.geonames.org/export/dump/cities15000.zip)
- File format documentation: [`readme.txt`](https://download.geonames.org/export/dump/readme.txt)
- License: [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)

| Pros | Cons |
|---|---|
| Free, well-maintained, covers 244 countries | Requires shipping/downloading a 7.5MB data file |
| State-level granularity | File parsing on startup (~1s) |
| No external API calls at runtime | Need to re-download periodically to pick up changes |

**Verdict:** Good solution but introduces a runtime data file dependency and a loading step. Maintenance is low but not zero.

---

### 2. [Google Time Zone API](https://developers.google.com/maps/documentation/timezone/overview)

**Endpoints:**
- [Geocoding API](https://developers.google.com/maps/documentation/geocoding/overview): `https://maps.googleapis.com/maps/api/geocode/json?address={country+state}&key={API_KEY}`
- [Timezone API](https://developers.google.com/maps/documentation/timezone/requests-timezone): `https://maps.googleapis.com/maps/api/timezone/json?location={lat,lng}&timestamp={unix}&key={API_KEY}`

**Cost estimate for 100k lookups** (see [Google Maps pricing](https://developers.google.com/maps/billing-and-pricing/pricing)):
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

### 3. [GeoNames Web API](https://www.geonames.org/export/web-services.html)

**Endpoint:**
- [`searchJSON`](https://www.geonames.org/export/geonames-search.html): `http://api.geonames.org/searchJSON?country={CC}&adminCode1={state}&maxRows=1&username={user}`
- Returns timezone in the response object

**Cost estimate for 100k lookups** (see [GeoNames account types](https://www.geonames.org/export/)):
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

### 4. [TimeZoneDB API](https://timezonedb.com/api)

**Endpoint:**
- `http://api.timezonedb.com/v2.1/get-time-zone?key={KEY}&format=json&by=zone&zone={IANA_ID}`
- Only supports lookup by [IANA zone ID](https://www.iana.org/time-zones) or lat/lng — **not by country+state**

**Verdict:** Eliminated. Doesn't support country+state input. Would require geocoding first, adding the same two-step problem as Google's API.

---

### 5. [WorldTimeAPI](http://worldtimeapi.org/) / [Abstract API](https://www.abstractapi.com/api/timezone-api)

- WorldTimeAPI: `worldtimeapi.org/api/timezone/{IANA_ID}` — only by IANA zone ID, not country+state. **Service has been shut down.**
- Abstract API: `abstractapi.com/api/timezone` — by lat/lng, requires geocoding. Standard plan caps at 60k requests/month ($99/month), not enough for 100k. See [pricing](https://www.abstractapi.com/api/timezone-api#pricing).

**Verdict:** Both eliminated. Same fundamental limitation — no country+state input, would need geocoding.

---

### 6. Hardcoded Static Map + [libphonenumber](https://github.com/google/libphonenumber) (Chosen Solution)

**How it works:** Country and state→timezone mappings are hardcoded from GeoNames [`cities15000.txt`](https://download.geonames.org/export/dump/cities15000.zip). When country/state lookup fails, Google's [libphonenumber](https://github.com/google/libphonenumber) ([`geocoder` module](https://github.com/google/libphonenumber/tree/master/java/geocoder)) resolves the phone number's area code to an IANA timezone as a fallback.

| Pros | Cons |
|---|---|
| Zero I/O, zero network calls | Limited to US/CA state-level; other multi-tz countries fall back to country |
| Instant resolution (HashMap + in-memory phone lookup) | Doesn't handle within-state edge cases (AZ Navajo, IN counties) |
| Phone fallback covers cases where country/state is missing | Would need a code change if a state switches timezone ID (extremely rare) |
| Java 8 compatible | Single dependency: [`com.googlecode.libphonenumber:geocoder`](https://mvnrepository.com/artifact/com.googlecode.libphonenumber/geocoder) |
| No API keys, no billing, no rate limits | |
| 100k lookups = microseconds, $0 cost | |

**Cost estimate for 100k lookups:** Effectively **$0** and **sub-millisecond** total.

## Solution Summary

### Resolution logic

```
For US and Canada:
  1. State → if valid US/CA state code, return timezone
  2. Phone → if valid phone number, resolve via libphonenumber area code mapping
  3. Country → return primary timezone (US: New York, CA: Toronto)

For all other countries:
  1. Country → if valid country code, return timezone
  2. Phone → if valid phone number, resolve via libphonenumber area code mapping
```

"Invalid" inputs (unknown country code, unknown state code, malformed phone number) are treated the same as null — the resolver falls through to the next level.

### What the static map covers

| Level | Entries | Description |
|---|---|---|
| Country → timezone | 244 | Most populous city per country from [`cities15000.txt`](https://download.geonames.org/export/dump/cities15000.zip) |
| State → timezone (US) | 51 | Two-letter state codes (CA, NY, TX, etc.) |
| State → timezone (Canada) | 13 | Standard two-letter abbreviations (AB, BC, ON, etc.) |

### About the 244 country entries

The country map uses [ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) codes, which include not only 193 UN member states but also ~50 territories and dependencies. Each has its own distinct timezone and a valid ISO code:

| Category | Count | Examples |
|---|---|---|
| UN member states | 193 | US, BR, JP, DE, etc. |
| Observer states | 2 | VA (Vatican), PS (Palestine) |
| Territories & dependencies | ~49 | PR (Puerto Rico), GU (Guam), HK (Hong Kong), GF (French Guiana), etc. |
| **Total** | **244** | |

These are kept intentionally — many territories have their own timezone (e.g., `GU` → `Pacific/Guam`, `HK` → `Asia/Hong_Kong`). If your data uses these codes, you get the correct timezone. If not, they're just unused entries with negligible cost.

### State-level support

State-level entries are limited to the US and Canada since they are the primary use case. Both use standard **two-letter abbreviations** (US: `CA`, `NY`, `TX`; Canada: `ON`, `BC`, `SK`).

**Data sources for state→timezone mapping:**

| Data | Source |
|---|---|
| US state codes | GeoNames [`cities15000.txt`](https://download.geonames.org/export/dump/cities15000.zip) — already uses two-letter [USPS abbreviations](https://pe.usps.com/text/pub28/28apb.htm) as admin1 codes |
| US state→timezone | GeoNames [`cities15000.txt`](https://download.geonames.org/export/dump/cities15000.zip) — timezone of the most populous city per state |
| Canada province codes | Cross-referenced GeoNames [`admin1CodesASCII.txt`](https://download.geonames.org/export/dump/admin1CodesASCII.txt) (numeric codes: `CA.01`=Alberta, `CA.02`=British Columbia, etc.) with [ISO 3166-2:CA](https://en.wikipedia.org/wiki/ISO_3166-2:CA) standard abbreviations (`AB`, `BC`, etc.) |
| Canada province→timezone | GeoNames [`cities15000.txt`](https://download.geonames.org/export/dump/cities15000.zip) — timezone of the most populous city per province. Nunavut (`NU`) was added manually as it has no city with 15k+ population in the dataset |

22 other countries span multiple timezones (AR, AU, BR, CD, CL, CN, CY, ES, GE, ID, KZ, MN, MX, MY, PG, PS, PT, RU, SO, UA, UZ, VN) but will fall back to the country's primary timezone (most populous city). State-level entries for these countries can be added to the map if needed in the future.

### Phone number fallback

When country/state lookup fails (or isn't available), the resolver uses Google's [libphonenumber](https://github.com/google/libphonenumber) ([`geocoder` module](https://github.com/google/libphonenumber/tree/master/java/geocoder)) to extract the timezone from the phone number's area code.

| Detail | Value |
|---|---|
| Dependency | [`com.googlecode.libphonenumber:geocoder:3.8`](https://mvnrepository.com/artifact/com.googlecode.libphonenumber/geocoder/3.8) (transitively includes [`libphonenumber:9.0.8`](https://mvnrepository.com/artifact/com.googlecode.libphonenumber/libphonenumber/9.0.8) and [`prefixmapper:3.8`](https://mvnrepository.com/artifact/com.googlecode.libphonenumber/prefixmapper/3.8)) |
| Input format | [E.164](https://en.wikipedia.org/wiki/E.164) or international format (e.g., `+12125551234`, `+5511999999999`) |
| Resolution | Area-code-level for US/CA; country-level for most other countries |
| When it helps | Phone is always available but country/state may be null or invalid |
| Failure mode | Malformed numbers or unknown prefixes return `Optional.empty()`, falling through gracefully |
| Key class | [`PhoneNumberToTimeZonesMapper`](https://www.javadoc.io/doc/com.googlecode.libphonenumber/geocoder/latest/com/google/i18n/phonenumbers/PhoneNumberToTimeZonesMapper.html) |

libphonenumber ships its own mapping data (baked into the jar), updated with each library release. No external API calls or data files needed.

### How DST and offset changes work (JVM `tzdata`)

The hardcoded map stores **[IANA timezone identifiers](https://www.iana.org/time-zones)** (e.g., `America/New_York`), not offsets or DST rules. The actual rules live in the **[IANA Time Zone Database](https://www.iana.org/time-zones)** (`tzdata`) bundled inside the JVM (see [Oracle's tzdata documentation](https://www.oracle.com/java/technologies/javase/tzdata-versions.html)).

When you call `TimeZone.getTimeZone("America/New_York").getOffset(timestamp)`, the JVM consults its `tzdata` to determine the correct offset — including whether DST is active at that moment. This database is updated ~3-4 times per year via JDK patch releases (see [tzdata release history](https://data.iana.org/time-zones/releases/)).

**Example — Turkey 2016:** Turkey abolished DST in September 2016, permanently staying at UTC+3.
- Before JVM update: `Europe/Istanbul` still returned UTC+2 in winter. **Wrong.**
- After JVM update: `Europe/Istanbul` returns UTC+3 always. **Correct.**

The timezone ID `Europe/Istanbul` didn't change. Our map entry `TR → Europe/Istanbul` didn't change. Only the JVM's internal rules changed.

### Maintenance model

| Concern | Who handles it | Action required |
|---|---|---|
| DST rule changes (country abolishes/adopts DST) | JVM [`tzdata`](https://www.oracle.com/java/technologies/javase/tzdata-versions.html) updates | Keep JDK updated (already done for security) |
| DST transition date changes | JVM [`tzdata`](https://www.oracle.com/java/technologies/javase/tzdata-versions.html) updates | Keep JDK updated |
| UTC offset changes for a timezone | JVM [`tzdata`](https://www.oracle.com/java/technologies/javase/tzdata-versions.html) updates | Keep JDK updated |
| Phone area code → timezone mapping changes | [libphonenumber](https://github.com/google/libphonenumber) updates | Bump library version |
| State switches to a different IANA timezone ID | Our hardcoded map | Change the entry (happens ~once per decade globally) |
| New country appears | Our hardcoded map | Add one entry |

**Estimated effort: near zero.** Keep your JDK and libphonenumber dependency updated, and the map itself is effectively permanent.

### API

```java
// US with state → state-level resolution
TimezoneResolver.resolve("US", "CA", "+12125551234");
// → America/Los_Angeles (state wins, phone ignored)

// US with invalid state → phone fallback
TimezoneResolver.resolve("US", "NOPE", "+12125551234");
// → America/New_York (phone area code 212 = NYC)

// US with no state, no phone → country fallback
TimezoneResolver.resolve("US", null, null);
// → America/New_York (most populous US city)

// Non-US country → country-level resolution
TimezoneResolver.resolve("JP", null, null);
// → Asia/Tokyo

// Invalid country → phone fallback
TimezoneResolver.resolve("XX", null, "+5511999999999");
// → America/Sao_Paulo (phone = São Paulo area code)

// Nothing works → empty
TimezoneResolver.resolve("XX", null, "not-a-number");
// → Optional.empty()
```

### Files

| File | Description |
|---|---|
| `TimezoneResolver.java` | Single class with hardcoded maps + phone fallback (~450 lines, ~310 are map entries) |
| `build.gradle` | Gradle build with [`geocoder`](https://mvnrepository.com/artifact/com.googlecode.libphonenumber/geocoder) dependency |

### Known Limitations

1. **Within-state timezone splits** (e.g., Indiana counties, Arizona Navajo Nation, eastern Oregon) are not resolved — the dominant timezone per state is used.
2. **State codes use standard two-letter abbreviations** for both US ([USPS](https://pe.usps.com/text/pub28/28apb.htm): `CA`, `NY`) and Canada ([ISO 3166-2:CA](https://en.wikipedia.org/wiki/ISO_3166-2:CA): `ON`, `BC`). If you need to support other multi-timezone countries in the future, add entries using whatever code convention your data uses.
3. **Overseas territories** may have their own country code (e.g., `GF` for French Guiana instead of `FR`), which is handled correctly since they appear as separate entries in the country map.
4. **Non-US/CA multi-timezone countries** (22 countries including RU, AU, BR, MX, ID) fall back to the country's primary timezone when a state code is provided. This can be expanded by adding entries to the state map.
5. **Phone number resolution** is area-code-level for US/CA but country-level for most other countries. A Brazilian phone number will resolve to `America/Sao_Paulo` regardless of whether it's from Manaus or São Paulo.

## References

| Resource | URL |
|---|---|
| IANA Time Zone Database | https://www.iana.org/time-zones |
| IANA tzdata releases | https://data.iana.org/time-zones/releases/ |
| Oracle JDK tzdata versions | https://www.oracle.com/java/technologies/javase/tzdata-versions.html |
| GeoNames data dump | https://download.geonames.org/export/dump/ |
| GeoNames `cities15000.txt` | https://download.geonames.org/export/dump/cities15000.zip |
| GeoNames `admin1CodesASCII.txt` | https://download.geonames.org/export/dump/admin1CodesASCII.txt |
| GeoNames file format docs | https://download.geonames.org/export/dump/readme.txt |
| GeoNames Web API docs | https://www.geonames.org/export/web-services.html |
| GeoNames `searchJSON` endpoint | https://www.geonames.org/export/geonames-search.html |
| ISO 3166-1 alpha-2 (country codes) | https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2 |
| ISO 3166-2:CA (Canada provinces) | https://en.wikipedia.org/wiki/ISO_3166-2:CA |
| USPS state abbreviations | https://pe.usps.com/text/pub28/28apb.htm |
| E.164 phone number format | https://en.wikipedia.org/wiki/E.164 |
| Google libphonenumber (GitHub) | https://github.com/google/libphonenumber |
| libphonenumber geocoder module | https://github.com/google/libphonenumber/tree/master/java/geocoder |
| `PhoneNumberToTimeZonesMapper` Javadoc | https://www.javadoc.io/doc/com.googlecode.libphonenumber/geocoder/latest/com/google/i18n/phonenumbers/PhoneNumberToTimeZonesMapper.html |
| geocoder on Maven Central | https://mvnrepository.com/artifact/com.googlecode.libphonenumber/geocoder |
| Google Geocoding API | https://developers.google.com/maps/documentation/geocoding/overview |
| Google Timezone API | https://developers.google.com/maps/documentation/timezone/overview |
| Google Maps pricing | https://developers.google.com/maps/billing-and-pricing/pricing |
| TimeZoneDB API | https://timezonedb.com/api |
| Abstract API Timezone | https://www.abstractapi.com/api/timezone-api |
