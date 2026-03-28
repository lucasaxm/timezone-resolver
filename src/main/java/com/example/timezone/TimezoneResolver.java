package com.example.timezone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberToTimeZonesMapper;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Resolves timezone from country and state using a best-effort fallback:
 * state+country → country.
 * <p>
 * Data sourced from GeoNames cities15000.txt. For each region, the timezone
 * of the most populous city is used as the representative timezone.
 * <p>
 * The country map covers 244 entries (193 sovereign nations + ~50 territories
 * and dependencies using ISO 3166-1 alpha-2 codes). State-level entries are
 * included for US (51 entries) and Canada (13 entries), both using standard
 * two-letter abbreviations (e.g. CA, NY, ON, BC).
 * All other countries fall back to country-level.
 * <p>
 * For US and Canada, the resolution order is: state → phone number → country.
 * For all other countries: country → phone number.
 * Phone number resolution uses Google's libphonenumber (geocoder module),
 * which maps area codes to IANA timezones.
 * <p>
 * Timezone IDs are IANA identifiers (e.g. "America/New_York"). The actual
 * offset and DST rules are resolved at runtime by the JVM's built-in tzdata.
 * Keep your JDK updated to get rule changes (e.g. a country abolishing DST).
 *
 * @see <a href="https://download.geonames.org/export/dump/admin1CodesASCII.txt">GeoNames admin1 codes</a>
 */
public final class TimezoneResolver {

    private static final Map<String, TimeZone> COUNTRY_MAP;
    private static final Map<String, TimeZone> STATE_MAP;

    static {
        Map<String, TimeZone> countries = new HashMap<>(244);
        countries.put("AD", TimeZone.getTimeZone("Europe/Andorra"));
        countries.put("AE", TimeZone.getTimeZone("Asia/Dubai"));
        countries.put("AF", TimeZone.getTimeZone("Asia/Kabul"));
        countries.put("AG", TimeZone.getTimeZone("America/Antigua"));
        countries.put("AI", TimeZone.getTimeZone("America/Anguilla"));
        countries.put("AL", TimeZone.getTimeZone("Europe/Tirane"));
        countries.put("AM", TimeZone.getTimeZone("Asia/Yerevan"));
        countries.put("AO", TimeZone.getTimeZone("Africa/Luanda"));
        countries.put("AR", TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        countries.put("AS", TimeZone.getTimeZone("Pacific/Pago_Pago"));
        countries.put("AT", TimeZone.getTimeZone("Europe/Vienna"));
        countries.put("AU", TimeZone.getTimeZone("Australia/Sydney"));
        countries.put("AW", TimeZone.getTimeZone("America/Aruba"));
        countries.put("AX", TimeZone.getTimeZone("Europe/Mariehamn"));
        countries.put("AZ", TimeZone.getTimeZone("Asia/Baku"));
        countries.put("BA", TimeZone.getTimeZone("Europe/Sarajevo"));
        countries.put("BB", TimeZone.getTimeZone("America/Barbados"));
        countries.put("BD", TimeZone.getTimeZone("Asia/Dhaka"));
        countries.put("BE", TimeZone.getTimeZone("Europe/Brussels"));
        countries.put("BF", TimeZone.getTimeZone("Africa/Ouagadougou"));
        countries.put("BG", TimeZone.getTimeZone("Europe/Sofia"));
        countries.put("BH", TimeZone.getTimeZone("Asia/Bahrain"));
        countries.put("BI", TimeZone.getTimeZone("Africa/Bujumbura"));
        countries.put("BJ", TimeZone.getTimeZone("Africa/Porto-Novo"));
        countries.put("BL", TimeZone.getTimeZone("America/St_Barthelemy"));
        countries.put("BM", TimeZone.getTimeZone("Atlantic/Bermuda"));
        countries.put("BN", TimeZone.getTimeZone("Asia/Brunei"));
        countries.put("BO", TimeZone.getTimeZone("America/La_Paz"));
        countries.put("BQ", TimeZone.getTimeZone("America/Kralendijk"));
        countries.put("BR", TimeZone.getTimeZone("America/Sao_Paulo"));
        countries.put("BS", TimeZone.getTimeZone("America/Nassau"));
        countries.put("BT", TimeZone.getTimeZone("Asia/Thimphu"));
        countries.put("BW", TimeZone.getTimeZone("Africa/Gaborone"));
        countries.put("BY", TimeZone.getTimeZone("Europe/Minsk"));
        countries.put("BZ", TimeZone.getTimeZone("America/Belize"));
        countries.put("CA", TimeZone.getTimeZone("America/Toronto"));
        countries.put("CC", TimeZone.getTimeZone("Indian/Cocos"));
        countries.put("CD", TimeZone.getTimeZone("Africa/Kinshasa"));
        countries.put("CF", TimeZone.getTimeZone("Africa/Bangui"));
        countries.put("CG", TimeZone.getTimeZone("Africa/Brazzaville"));
        countries.put("CH", TimeZone.getTimeZone("Europe/Zurich"));
        countries.put("CI", TimeZone.getTimeZone("Africa/Abidjan"));
        countries.put("CK", TimeZone.getTimeZone("Pacific/Rarotonga"));
        countries.put("CL", TimeZone.getTimeZone("America/Santiago"));
        countries.put("CM", TimeZone.getTimeZone("Africa/Douala"));
        countries.put("CN", TimeZone.getTimeZone("Asia/Shanghai"));
        countries.put("CO", TimeZone.getTimeZone("America/Bogota"));
        countries.put("CR", TimeZone.getTimeZone("America/Costa_Rica"));
        countries.put("CU", TimeZone.getTimeZone("America/Havana"));
        countries.put("CV", TimeZone.getTimeZone("Atlantic/Cape_Verde"));
        countries.put("CW", TimeZone.getTimeZone("America/Curacao"));
        countries.put("CX", TimeZone.getTimeZone("Indian/Christmas"));
        countries.put("CY", TimeZone.getTimeZone("Asia/Nicosia"));
        countries.put("CZ", TimeZone.getTimeZone("Europe/Prague"));
        countries.put("DE", TimeZone.getTimeZone("Europe/Berlin"));
        countries.put("DJ", TimeZone.getTimeZone("Africa/Djibouti"));
        countries.put("DK", TimeZone.getTimeZone("Europe/Copenhagen"));
        countries.put("DM", TimeZone.getTimeZone("America/Dominica"));
        countries.put("DO", TimeZone.getTimeZone("America/Santo_Domingo"));
        countries.put("DZ", TimeZone.getTimeZone("Africa/Algiers"));
        countries.put("EC", TimeZone.getTimeZone("America/Guayaquil"));
        countries.put("EE", TimeZone.getTimeZone("Europe/Tallinn"));
        countries.put("EG", TimeZone.getTimeZone("Africa/Cairo"));
        countries.put("EH", TimeZone.getTimeZone("Africa/El_Aaiun"));
        countries.put("ER", TimeZone.getTimeZone("Africa/Asmara"));
        countries.put("ES", TimeZone.getTimeZone("Europe/Madrid"));
        countries.put("ET", TimeZone.getTimeZone("Africa/Addis_Ababa"));
        countries.put("FI", TimeZone.getTimeZone("Europe/Helsinki"));
        countries.put("FJ", TimeZone.getTimeZone("Pacific/Fiji"));
        countries.put("FK", TimeZone.getTimeZone("Atlantic/Stanley"));
        countries.put("FM", TimeZone.getTimeZone("Pacific/Pohnpei"));
        countries.put("FO", TimeZone.getTimeZone("Atlantic/Faroe"));
        countries.put("FR", TimeZone.getTimeZone("Europe/Paris"));
        countries.put("GA", TimeZone.getTimeZone("Africa/Libreville"));
        countries.put("GB", TimeZone.getTimeZone("Europe/London"));
        countries.put("GD", TimeZone.getTimeZone("America/Grenada"));
        countries.put("GE", TimeZone.getTimeZone("Asia/Tbilisi"));
        countries.put("GF", TimeZone.getTimeZone("America/Cayenne"));
        countries.put("GG", TimeZone.getTimeZone("Europe/Guernsey"));
        countries.put("GH", TimeZone.getTimeZone("Africa/Accra"));
        countries.put("GI", TimeZone.getTimeZone("Europe/Gibraltar"));
        countries.put("GL", TimeZone.getTimeZone("America/Nuuk"));
        countries.put("GM", TimeZone.getTimeZone("Africa/Banjul"));
        countries.put("GN", TimeZone.getTimeZone("Africa/Conakry"));
        countries.put("GP", TimeZone.getTimeZone("America/Guadeloupe"));
        countries.put("GQ", TimeZone.getTimeZone("Africa/Malabo"));
        countries.put("GR", TimeZone.getTimeZone("Europe/Athens"));
        countries.put("GS", TimeZone.getTimeZone("Atlantic/South_Georgia"));
        countries.put("GT", TimeZone.getTimeZone("America/Guatemala"));
        countries.put("GU", TimeZone.getTimeZone("Pacific/Guam"));
        countries.put("GW", TimeZone.getTimeZone("Africa/Bissau"));
        countries.put("GY", TimeZone.getTimeZone("America/Guyana"));
        countries.put("HK", TimeZone.getTimeZone("Asia/Hong_Kong"));
        countries.put("HN", TimeZone.getTimeZone("America/Tegucigalpa"));
        countries.put("HR", TimeZone.getTimeZone("Europe/Zagreb"));
        countries.put("HT", TimeZone.getTimeZone("America/Port-au-Prince"));
        countries.put("HU", TimeZone.getTimeZone("Europe/Budapest"));
        countries.put("ID", TimeZone.getTimeZone("Asia/Jakarta"));
        countries.put("IE", TimeZone.getTimeZone("Europe/Dublin"));
        countries.put("IL", TimeZone.getTimeZone("Asia/Jerusalem"));
        countries.put("IM", TimeZone.getTimeZone("Europe/Isle_of_Man"));
        countries.put("IN", TimeZone.getTimeZone("Asia/Kolkata"));
        countries.put("IQ", TimeZone.getTimeZone("Asia/Baghdad"));
        countries.put("IR", TimeZone.getTimeZone("Asia/Tehran"));
        countries.put("IS", TimeZone.getTimeZone("Atlantic/Reykjavik"));
        countries.put("IT", TimeZone.getTimeZone("Europe/Rome"));
        countries.put("JE", TimeZone.getTimeZone("Europe/Jersey"));
        countries.put("JM", TimeZone.getTimeZone("America/Jamaica"));
        countries.put("JO", TimeZone.getTimeZone("Asia/Amman"));
        countries.put("JP", TimeZone.getTimeZone("Asia/Tokyo"));
        countries.put("KE", TimeZone.getTimeZone("Africa/Nairobi"));
        countries.put("KG", TimeZone.getTimeZone("Asia/Bishkek"));
        countries.put("KH", TimeZone.getTimeZone("Asia/Phnom_Penh"));
        countries.put("KI", TimeZone.getTimeZone("Pacific/Tarawa"));
        countries.put("KM", TimeZone.getTimeZone("Indian/Comoro"));
        countries.put("KN", TimeZone.getTimeZone("America/St_Kitts"));
        countries.put("KP", TimeZone.getTimeZone("Asia/Pyongyang"));
        countries.put("KR", TimeZone.getTimeZone("Asia/Seoul"));
        countries.put("KW", TimeZone.getTimeZone("Asia/Kuwait"));
        countries.put("KY", TimeZone.getTimeZone("America/Cayman"));
        countries.put("KZ", TimeZone.getTimeZone("Asia/Almaty"));
        countries.put("LA", TimeZone.getTimeZone("Asia/Vientiane"));
        countries.put("LB", TimeZone.getTimeZone("Asia/Beirut"));
        countries.put("LC", TimeZone.getTimeZone("America/St_Lucia"));
        countries.put("LI", TimeZone.getTimeZone("Europe/Vaduz"));
        countries.put("LK", TimeZone.getTimeZone("Asia/Colombo"));
        countries.put("LR", TimeZone.getTimeZone("Africa/Monrovia"));
        countries.put("LS", TimeZone.getTimeZone("Africa/Maseru"));
        countries.put("LT", TimeZone.getTimeZone("Europe/Vilnius"));
        countries.put("LU", TimeZone.getTimeZone("Europe/Luxembourg"));
        countries.put("LV", TimeZone.getTimeZone("Europe/Riga"));
        countries.put("LY", TimeZone.getTimeZone("Africa/Tripoli"));
        countries.put("MA", TimeZone.getTimeZone("Africa/Casablanca"));
        countries.put("MC", TimeZone.getTimeZone("Europe/Monaco"));
        countries.put("MD", TimeZone.getTimeZone("Europe/Chisinau"));
        countries.put("ME", TimeZone.getTimeZone("Europe/Podgorica"));
        countries.put("MF", TimeZone.getTimeZone("America/Marigot"));
        countries.put("MG", TimeZone.getTimeZone("Indian/Antananarivo"));
        countries.put("MH", TimeZone.getTimeZone("Pacific/Majuro"));
        countries.put("MK", TimeZone.getTimeZone("Europe/Skopje"));
        countries.put("ML", TimeZone.getTimeZone("Africa/Bamako"));
        countries.put("MM", TimeZone.getTimeZone("Asia/Yangon"));
        countries.put("MN", TimeZone.getTimeZone("Asia/Ulaanbaatar"));
        countries.put("MO", TimeZone.getTimeZone("Asia/Macau"));
        countries.put("MP", TimeZone.getTimeZone("Pacific/Saipan"));
        countries.put("MQ", TimeZone.getTimeZone("America/Martinique"));
        countries.put("MR", TimeZone.getTimeZone("Africa/Nouakchott"));
        countries.put("MS", TimeZone.getTimeZone("America/Montserrat"));
        countries.put("MT", TimeZone.getTimeZone("Europe/Malta"));
        countries.put("MU", TimeZone.getTimeZone("Indian/Mauritius"));
        countries.put("MV", TimeZone.getTimeZone("Indian/Maldives"));
        countries.put("MW", TimeZone.getTimeZone("Africa/Blantyre"));
        countries.put("MX", TimeZone.getTimeZone("America/Mexico_City"));
        countries.put("MY", TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        countries.put("MZ", TimeZone.getTimeZone("Africa/Maputo"));
        countries.put("NA", TimeZone.getTimeZone("Africa/Windhoek"));
        countries.put("NC", TimeZone.getTimeZone("Pacific/Noumea"));
        countries.put("NE", TimeZone.getTimeZone("Africa/Niamey"));
        countries.put("NF", TimeZone.getTimeZone("Pacific/Norfolk"));
        countries.put("NG", TimeZone.getTimeZone("Africa/Lagos"));
        countries.put("NI", TimeZone.getTimeZone("America/Managua"));
        countries.put("NL", TimeZone.getTimeZone("Europe/Amsterdam"));
        countries.put("NO", TimeZone.getTimeZone("Europe/Oslo"));
        countries.put("NP", TimeZone.getTimeZone("Asia/Kathmandu"));
        countries.put("NR", TimeZone.getTimeZone("Pacific/Nauru"));
        countries.put("NU", TimeZone.getTimeZone("Pacific/Niue"));
        countries.put("NZ", TimeZone.getTimeZone("Pacific/Auckland"));
        countries.put("OM", TimeZone.getTimeZone("Asia/Muscat"));
        countries.put("PA", TimeZone.getTimeZone("America/Panama"));
        countries.put("PE", TimeZone.getTimeZone("America/Lima"));
        countries.put("PF", TimeZone.getTimeZone("Pacific/Tahiti"));
        countries.put("PG", TimeZone.getTimeZone("Pacific/Port_Moresby"));
        countries.put("PH", TimeZone.getTimeZone("Asia/Manila"));
        countries.put("PK", TimeZone.getTimeZone("Asia/Karachi"));
        countries.put("PL", TimeZone.getTimeZone("Europe/Warsaw"));
        countries.put("PM", TimeZone.getTimeZone("America/Miquelon"));
        countries.put("PN", TimeZone.getTimeZone("Pacific/Pitcairn"));
        countries.put("PR", TimeZone.getTimeZone("America/Puerto_Rico"));
        countries.put("PS", TimeZone.getTimeZone("Asia/Hebron"));
        countries.put("PT", TimeZone.getTimeZone("Europe/Lisbon"));
        countries.put("PW", TimeZone.getTimeZone("Pacific/Palau"));
        countries.put("PY", TimeZone.getTimeZone("America/Asuncion"));
        countries.put("QA", TimeZone.getTimeZone("Asia/Qatar"));
        countries.put("RE", TimeZone.getTimeZone("Indian/Reunion"));
        countries.put("RO", TimeZone.getTimeZone("Europe/Bucharest"));
        countries.put("RS", TimeZone.getTimeZone("Europe/Belgrade"));
        countries.put("RU", TimeZone.getTimeZone("Europe/Moscow"));
        countries.put("RW", TimeZone.getTimeZone("Africa/Kigali"));
        countries.put("SA", TimeZone.getTimeZone("Asia/Riyadh"));
        countries.put("SB", TimeZone.getTimeZone("Pacific/Guadalcanal"));
        countries.put("SC", TimeZone.getTimeZone("Indian/Mahe"));
        countries.put("SD", TimeZone.getTimeZone("Africa/Khartoum"));
        countries.put("SE", TimeZone.getTimeZone("Europe/Stockholm"));
        countries.put("SG", TimeZone.getTimeZone("Asia/Singapore"));
        countries.put("SH", TimeZone.getTimeZone("Atlantic/St_Helena"));
        countries.put("SI", TimeZone.getTimeZone("Europe/Ljubljana"));
        countries.put("SJ", TimeZone.getTimeZone("Arctic/Longyearbyen"));
        countries.put("SK", TimeZone.getTimeZone("Europe/Bratislava"));
        countries.put("SL", TimeZone.getTimeZone("Africa/Freetown"));
        countries.put("SM", TimeZone.getTimeZone("Europe/San_Marino"));
        countries.put("SN", TimeZone.getTimeZone("Africa/Dakar"));
        countries.put("SO", TimeZone.getTimeZone("Africa/Mogadishu"));
        countries.put("SR", TimeZone.getTimeZone("America/Paramaribo"));
        countries.put("SS", TimeZone.getTimeZone("Africa/Juba"));
        countries.put("ST", TimeZone.getTimeZone("Africa/Sao_Tome"));
        countries.put("SV", TimeZone.getTimeZone("America/El_Salvador"));
        countries.put("SX", TimeZone.getTimeZone("America/Lower_Princes"));
        countries.put("SY", TimeZone.getTimeZone("Asia/Damascus"));
        countries.put("SZ", TimeZone.getTimeZone("Africa/Mbabane"));
        countries.put("TC", TimeZone.getTimeZone("America/Grand_Turk"));
        countries.put("TD", TimeZone.getTimeZone("Africa/Ndjamena"));
        countries.put("TF", TimeZone.getTimeZone("Indian/Kerguelen"));
        countries.put("TG", TimeZone.getTimeZone("Africa/Lome"));
        countries.put("TH", TimeZone.getTimeZone("Asia/Bangkok"));
        countries.put("TJ", TimeZone.getTimeZone("Asia/Dushanbe"));
        countries.put("TL", TimeZone.getTimeZone("Asia/Dili"));
        countries.put("TM", TimeZone.getTimeZone("Asia/Ashgabat"));
        countries.put("TN", TimeZone.getTimeZone("Africa/Tunis"));
        countries.put("TO", TimeZone.getTimeZone("Pacific/Tongatapu"));
        countries.put("TR", TimeZone.getTimeZone("Europe/Istanbul"));
        countries.put("TT", TimeZone.getTimeZone("America/Port_of_Spain"));
        countries.put("TV", TimeZone.getTimeZone("Pacific/Funafuti"));
        countries.put("TW", TimeZone.getTimeZone("Asia/Taipei"));
        countries.put("TZ", TimeZone.getTimeZone("Africa/Dar_es_Salaam"));
        countries.put("UA", TimeZone.getTimeZone("Europe/Kyiv"));
        countries.put("UG", TimeZone.getTimeZone("Africa/Kampala"));
        countries.put("US", TimeZone.getTimeZone("America/New_York"));
        countries.put("UY", TimeZone.getTimeZone("America/Montevideo"));
        countries.put("UZ", TimeZone.getTimeZone("Asia/Tashkent"));
        countries.put("VA", TimeZone.getTimeZone("Europe/Vatican"));
        countries.put("VC", TimeZone.getTimeZone("America/St_Vincent"));
        countries.put("VE", TimeZone.getTimeZone("America/Caracas"));
        countries.put("VG", TimeZone.getTimeZone("America/Tortola"));
        countries.put("VI", TimeZone.getTimeZone("America/St_Thomas"));
        countries.put("VN", TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        countries.put("VU", TimeZone.getTimeZone("Pacific/Efate"));
        countries.put("WF", TimeZone.getTimeZone("Pacific/Wallis"));
        countries.put("WS", TimeZone.getTimeZone("Pacific/Apia"));
        countries.put("XK", TimeZone.getTimeZone("Europe/Belgrade"));
        countries.put("YE", TimeZone.getTimeZone("Asia/Aden"));
        countries.put("YT", TimeZone.getTimeZone("Indian/Mayotte"));
        countries.put("ZA", TimeZone.getTimeZone("Africa/Johannesburg"));
        countries.put("ZM", TimeZone.getTimeZone("Africa/Lusaka"));
        countries.put("ZW", TimeZone.getTimeZone("Africa/Harare"));
        COUNTRY_MAP = Collections.unmodifiableMap(countries);

        Map<String, TimeZone> states = new HashMap<>(64);
        // CA (Canada) — standard two-letter province/territory abbreviations
        states.put("CA:AB", TimeZone.getTimeZone("America/Edmonton"));
        states.put("CA:BC", TimeZone.getTimeZone("America/Vancouver"));
        states.put("CA:MB", TimeZone.getTimeZone("America/Winnipeg"));
        states.put("CA:NB", TimeZone.getTimeZone("America/Moncton"));
        states.put("CA:NL", TimeZone.getTimeZone("America/St_Johns"));
        states.put("CA:NS", TimeZone.getTimeZone("America/Halifax"));
        states.put("CA:NT", TimeZone.getTimeZone("America/Edmonton"));
        states.put("CA:NU", TimeZone.getTimeZone("America/Winnipeg"));
        states.put("CA:ON", TimeZone.getTimeZone("America/Toronto"));
        states.put("CA:PE", TimeZone.getTimeZone("America/Halifax"));
        states.put("CA:QC", TimeZone.getTimeZone("America/Toronto"));
        states.put("CA:SK", TimeZone.getTimeZone("America/Regina"));
        states.put("CA:YT", TimeZone.getTimeZone("America/Whitehorse"));
        // US (United States)
        states.put("US:AK", TimeZone.getTimeZone("America/Anchorage"));
        states.put("US:AL", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:AR", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:AZ", TimeZone.getTimeZone("America/Phoenix"));
        states.put("US:CA", TimeZone.getTimeZone("America/Los_Angeles"));
        states.put("US:CO", TimeZone.getTimeZone("America/Denver"));
        states.put("US:CT", TimeZone.getTimeZone("America/New_York"));
        states.put("US:DC", TimeZone.getTimeZone("America/New_York"));
        states.put("US:DE", TimeZone.getTimeZone("America/New_York"));
        states.put("US:FL", TimeZone.getTimeZone("America/New_York"));
        states.put("US:GA", TimeZone.getTimeZone("America/New_York"));
        states.put("US:HI", TimeZone.getTimeZone("Pacific/Honolulu"));
        states.put("US:IA", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:ID", TimeZone.getTimeZone("America/Boise"));
        states.put("US:IL", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:IN", TimeZone.getTimeZone("America/Indiana/Indianapolis"));
        states.put("US:KS", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:KY", TimeZone.getTimeZone("America/Kentucky/Louisville"));
        states.put("US:LA", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:MA", TimeZone.getTimeZone("America/New_York"));
        states.put("US:MD", TimeZone.getTimeZone("America/New_York"));
        states.put("US:ME", TimeZone.getTimeZone("America/New_York"));
        states.put("US:MI", TimeZone.getTimeZone("America/Detroit"));
        states.put("US:MN", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:MO", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:MS", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:MT", TimeZone.getTimeZone("America/Denver"));
        states.put("US:NC", TimeZone.getTimeZone("America/New_York"));
        states.put("US:ND", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:NE", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:NH", TimeZone.getTimeZone("America/New_York"));
        states.put("US:NJ", TimeZone.getTimeZone("America/New_York"));
        states.put("US:NM", TimeZone.getTimeZone("America/Denver"));
        states.put("US:NV", TimeZone.getTimeZone("America/Los_Angeles"));
        states.put("US:NY", TimeZone.getTimeZone("America/New_York"));
        states.put("US:OH", TimeZone.getTimeZone("America/New_York"));
        states.put("US:OK", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:OR", TimeZone.getTimeZone("America/Los_Angeles"));
        states.put("US:PA", TimeZone.getTimeZone("America/New_York"));
        states.put("US:RI", TimeZone.getTimeZone("America/New_York"));
        states.put("US:SC", TimeZone.getTimeZone("America/New_York"));
        states.put("US:SD", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:TN", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:TX", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:UT", TimeZone.getTimeZone("America/Denver"));
        states.put("US:VA", TimeZone.getTimeZone("America/New_York"));
        states.put("US:VT", TimeZone.getTimeZone("America/New_York"));
        states.put("US:WA", TimeZone.getTimeZone("America/Los_Angeles"));
        states.put("US:WI", TimeZone.getTimeZone("America/Chicago"));
        states.put("US:WV", TimeZone.getTimeZone("America/New_York"));
        states.put("US:WY", TimeZone.getTimeZone("America/Denver"));
        STATE_MAP = Collections.unmodifiableMap(states);
    }

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();
    private static final PhoneNumberToTimeZonesMapper TZ_MAPPER = PhoneNumberToTimeZonesMapper.getInstance();
    private static final String UNKNOWN_TZ = "Etc/Unknown";

    private TimezoneResolver() {
    }

    /**
     * Resolves the timezone for the given location with best-effort fallback.
     * <p>
     * For US and Canada: state → phone number → country.
     * For all other countries: country → phone number.
     * <p>
     * "Invalid" inputs (unknown country code, unknown state code) are treated
     * the same as null — the resolver simply falls through to the next level.
     *
     * @param countryCode ISO 3166-1 alpha-2 country code (nullable, e.g. "US", "BR", "JP")
     * @param state       state/province code (nullable, e.g. "CA", "ON")
     * @param phoneNumber phone number in E.164 or international format (nullable, e.g. "+12125551234")
     * @return the resolved timezone, or empty if nothing could be resolved
     */
    public static Optional<TimeZone> resolve(String countryCode, String state, String phoneNumber) {
        String normalizedCountry = normalize(countryCode);
        boolean isUsOrCa = "US".equals(normalizedCountry) || "CA".equals(normalizedCountry);

        if (isUsOrCa) {
            // US/CA: state → phone → country
            Optional<TimeZone> stateResult = resolveByState(normalizedCountry, state);
            if (stateResult.isPresent()) {
                return stateResult;
            }

            Optional<TimeZone> phoneResult = resolveByPhone(phoneNumber);
            if (phoneResult.isPresent()) {
                return phoneResult;
            }

            return resolveByCountry(normalizedCountry);
        }

        // Non-US/CA: country → phone
        Optional<TimeZone> countryResult = resolveByCountry(normalizedCountry);
        if (countryResult.isPresent()) {
            return countryResult;
        }

        return resolveByPhone(phoneNumber);
    }

    private static Optional<TimeZone> resolveByState(String normalizedCountry, String state) {
        if (normalizedCountry == null || state == null || state.trim().isEmpty()) {
            return Optional.empty();
        }
        String stateKey = normalizedCountry + ":" + state.trim().toUpperCase();
        return Optional.ofNullable(STATE_MAP.get(stateKey));
    }

    private static Optional<TimeZone> resolveByCountry(String normalizedCountry) {
        if (normalizedCountry == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(COUNTRY_MAP.get(normalizedCountry));
    }

    private static Optional<TimeZone> resolveByPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            PhoneNumber parsed = PHONE_UTIL.parse(phoneNumber.trim(), null);
            List<String> timezones = TZ_MAPPER.getTimeZonesForNumber(parsed);
            if (timezones.isEmpty() || UNKNOWN_TZ.equals(timezones.get(0))) {
                return Optional.empty();
            }
            return Optional.of(TimeZone.getTimeZone(timezones.get(0)));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
