package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts an amount in a source currency to a rounded EUR estimate using the
 * approximate rates in {@link FxRateConfig}. Returns {@code null} for currencies
 * we don't have a rate for, so callers simply leave the column unset.
 */
@ApplicationScoped
public class CurrencyConverter {

    private final FxRateConfig rates;

    CurrencyConverter(FxRateConfig rates) {
        this.rates = rates;
    }

    public Integer toEur(String currency, long amount) {
        BigDecimal rate = rateFor(currency);
        if (rate == null) {
            return null;
        }
        return rate.multiply(BigDecimal.valueOf(amount))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal rateFor(String currency) {
        return switch (currency) {
            case "EUR" -> rates.eur();
            case "GBP" -> rates.gbp();
            case "USD" -> rates.usd();
            case "CHF" -> rates.chf();
            case "SEK" -> rates.sek();
            case "NOK" -> rates.nok();
            case "DKK" -> rates.dkk();
            case "PLN" -> rates.pln();
            case "CAD" -> rates.cad();
            default -> null;
        };
    }
}
