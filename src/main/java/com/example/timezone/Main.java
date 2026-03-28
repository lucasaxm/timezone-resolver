package com.example.timezone;

import java.util.Optional;
import java.util.TimeZone;

public class Main {

    public static void main(String[] args) {
        System.out.println("=== US/CA: state resolves it ===");
        print("US", "CA", "+12125551234");   // State wins, phone ignored
        print("US", "NY", null);             // State only, no phone
        print("CA", "BC", "+14165551234");   // State wins, phone ignored
        print("CA", "SK", null);             // Saskatchewan, no DST

        System.out.println("\n=== US/CA: invalid state → phone fallback ===");
        print("US", "NOPE", "+12125551234"); // Bad state → phone (NYC area code) → Eastern
        print("US", null, "+13125551234");   // No state → phone (Chicago area code) → Central
        print("US", null, "+12135551234");   // No state → phone (LA area code) → Pacific
        print("CA", null, "+16045551234");   // No state → phone (Vancouver area code) → Pacific
        print("CA", "XX", "+14165551234");   // Bad state → phone (Toronto area code) → Eastern

        System.out.println("\n=== US/CA: invalid state + invalid phone → country fallback ===");
        print("US", "NOPE", null);           // Bad state, no phone → US country (NYC)
        print("US", null, "not-a-number");   // No state, bad phone → US country (NYC)
        print("CA", null, null);             // Nothing → CA country (Toronto)

        System.out.println("\n=== Non-US/CA: country resolves it ===");
        print("JP", null, null);             // Japan → Asia/Tokyo
        print("BR", null, null);             // Brazil → America/Sao_Paulo
        print("DE", null, "+4930123456");    // Country wins, phone ignored

        System.out.println("\n=== Non-US/CA: invalid country → phone fallback ===");
        print("XX", null, "+5511999999999"); // Bad country → phone (São Paulo) → Sao_Paulo
        print(null, null, "+81312345678");   // No country → phone (Tokyo) → Asia/Tokyo
        print("", null, "+442071234567");    // Empty country → phone (London) → Europe/London
        print("XX", null, "+61291234567");   // Bad country → phone (Sydney) → Australia/Sydney

        System.out.println("\n=== Nothing resolves → empty ===");
        print(null, null, null);             // All null
        print("XX", "YY", "not-a-number");  // All invalid
        print("", "", "");                   // All empty
    }

    private static void print(String country, String state, String phone) {
        Optional<TimeZone> result = TimezoneResolver.resolve(country, state, phone);
        String location = String.format("country=%-2s, state=%-4s, phone=%-16s",
            country != null ? country : "-",
            state != null ? state : "-",
            phone != null ? phone : "-");
        String timezone = result.map(TimeZone::getID).orElse("NOT FOUND");
        System.out.printf("  %-55s → %s%n", location, timezone);
    }
}
