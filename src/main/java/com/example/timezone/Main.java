package com.example.timezone;

import java.util.Optional;
import java.util.TimeZone;

public class Main {

    public static void main(String[] args) {
        System.out.println("=== Multi-timezone countries (state-level) ===");
        print("US", "CA");    // California → Pacific
        print("US", "NY");    // New York → Eastern
        print("US", "IL");    // Illinois → Central
        print("US", "HI");    // Hawaii
        print("CA", "02");    // British Columbia → Pacific
        print("CA", "08");    // Ontario → Eastern
        print("CA", "11");    // Saskatchewan → no DST
        print("CA", "05");    // Newfoundland → UTC-3:30
        print("RU", "48");    // Moscow
        print("RU", "75");    // Novosibirsk
        print("AU", "02");    // New South Wales → Sydney
        print("AU", "05");    // South Australia → Adelaide
        print("BR", "27");    // São Paulo
        print("BR", "04");    // Amazonas → Manaus
        print("MX", "09");    // Mexico City
        print("MX", "02");    // Baja California → Tijuana
        print("ID", "04");    // Jakarta → WIB
        print("ID", "29");    // Jayapura → WIT

        System.out.println("\n=== Country-level fallback (state unknown) ===");
        print("US", null);    // US → most populous city (NYC)
        print("BR", null);    // Brazil → São Paulo
        print("RU", null);    // Russia → Moscow

        System.out.println("\n=== Single-timezone countries ===");
        print("JP", null);    // Japan
        print("DE", null);    // Germany
        print("KR", null);    // South Korea
        print("GB", null);    // United Kingdom
        print("FR", null);    // France
        print("IN", null);    // India → UTC+5:30

        System.out.println("\n=== Unknown inputs ===");
        print("US", "NOPE");  // Unknown state → country fallback
        print("XX", null);    // Unknown country → NOT FOUND
    }

    private static void print(String country, String state) {
        Optional<TimeZone> result = TimezoneResolver.resolve(country, state);
        String location = String.format("country=%s, state=%-4s",
                country,
                state != null ? state : "-");
        String timezone = result.map(TimeZone::getID).orElse("NOT FOUND");
        System.out.printf("  %-25s → %s%n", location, timezone);
    }
}
