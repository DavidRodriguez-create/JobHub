package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of an annual salary from free-text job descriptions,
 * returned as a rough EUR estimate. Job boards (Greenhouse, Lever) don't expose
 * structured pay, so we scan the text for a currency-prefixed amount such as
 * {@code £115,000 - £150,000}, {@code €80k} or {@code $120,000}.
 *
 * <p>Amounts below {@code crawler.salary.min-annual} are ignored to avoid
 * picking up things like "£1,000 learning budget". The original figure is left
 * untouched in the description; only the EUR estimate is stored.
 */
@ApplicationScoped
public class SalaryParser {

    private static final String CCY = "(£|€|\\$|GBP|EUR|USD|CHF|SEK|NOK|DKK|PLN|CAD)";
    private static final String NUM = "([0-9][0-9.,]*)";
    private static final String K = "\\s?([kK])?";

    private static final Pattern RANGE = Pattern.compile(
            CCY + "\\s?" + NUM + K + "\\s*(?:-|–|—|to)\\s*" + CCY + "?\\s?" + NUM + K);
    private static final Pattern SINGLE = Pattern.compile(CCY + "\\s?" + NUM + K);

    private final CurrencyConverter converter;
    private final long minAnnual;

    SalaryParser(CurrencyConverter converter,
                 @ConfigProperty(name = "crawler.salary.min-annual", defaultValue = "10000") long minAnnual) {
        this.converter = converter;
        this.minAnnual = minAnnual;
    }

    public record EurSalary(Integer min, Integer max) {}

    public Optional<EurSalary> parseToEur(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            String currency = code(range.group(1));
            long low = amount(range.group(2), range.group(3));
            long high = amount(range.group(5), range.group(6));
            if (low >= minAnnual && high >= minAnnual && high >= low) {
                Integer minEur = converter.toEur(currency, low);
                Integer maxEur = converter.toEur(currency, high);
                if (minEur != null) {
                    return Optional.of(new EurSalary(minEur, maxEur));
                }
            }
        }

        Matcher single = SINGLE.matcher(text);
        while (single.find()) {
            String currency = code(single.group(1));
            long value = amount(single.group(2), single.group(3));
            if (value >= minAnnual) {
                Integer eur = converter.toEur(currency, value);
                if (eur != null) {
                    return Optional.of(new EurSalary(eur, eur));
                }
            }
        }

        return Optional.empty();
    }

    private static long amount(String digits, String kSuffix) {
        long base = Long.parseLong(digits.replaceAll("[.,]", ""));
        return kSuffix != null && !kSuffix.isBlank() ? base * 1000 : base;
    }

    private static String code(String symbol) {
        return switch (symbol) {
            case "£" -> "GBP";
            case "€" -> "EUR";
            case "$" -> "USD";
            default -> symbol.toUpperCase();
        };
    }
}
