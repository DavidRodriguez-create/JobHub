package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.support;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.FxRateConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.SalaryParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SalaryParser Unit Tests")
class SalaryParserTest {

    SalaryParser parser;

    /** Fixed rates so assertions are deterministic regardless of config defaults. */
    private static final FxRateConfig RATES = new FxRateConfig() {
        public BigDecimal eur() { return BigDecimal.ONE; }
        public BigDecimal gbp() { return new BigDecimal("1.17"); }
        public BigDecimal usd() { return new BigDecimal("0.92"); }
        public BigDecimal chf() { return new BigDecimal("1.04"); }
        public BigDecimal sek() { return new BigDecimal("0.087"); }
        public BigDecimal nok() { return new BigDecimal("0.086"); }
        public BigDecimal dkk() { return new BigDecimal("0.134"); }
        public BigDecimal pln() { return new BigDecimal("0.23"); }
        public BigDecimal cad() { return new BigDecimal("0.68"); }
    };

    @BeforeEach
    void setUp() throws Exception {
        CurrencyConverter converter = newConverter(RATES);
        parser = newParser(converter, 10_000L);
    }

    @Test
    @DisplayName("parses a GBP range and converts both ends to EUR")
    void parsesGbpRange() {
        var salary = parser.parseToEur("💰 £115,000 - £150,000 + benefits");

        assertThat(salary).isPresent();
        assertThat(salary.get().min()).isEqualTo(134_550); // 115000 * 1.17
        assertThat(salary.get().max()).isEqualTo(175_500); // 150000 * 1.17
    }

    @Test
    @DisplayName("parses a 'to' range with a symbol on each amount")
    void parsesToRange() {
        var salary = parser.parseToEur("£44,600 to £55,000 + Incentive Awards");

        assertThat(salary).isPresent();
        assertThat(salary.get().min()).isEqualTo(52_182);
        assertThat(salary.get().max()).isEqualTo(64_350);
    }

    @Test
    @DisplayName("parses a single USD amount as min == max")
    void parsesSingleUsd() {
        var salary = parser.parseToEur("Base salary of $120,000 per year");

        assertThat(salary).isPresent();
        assertThat(salary.get().min()).isEqualTo(110_400);
        assertThat(salary.get().max()).isEqualTo(110_400);
    }

    @Test
    @DisplayName("expands the 'k' shorthand")
    void expandsKShorthand() {
        var salary = parser.parseToEur("around €80k depending on experience");

        assertThat(salary).isPresent();
        assertThat(salary.get().min()).isEqualTo(80_000);
    }

    @Test
    @DisplayName("ignores small amounts below the annual threshold")
    void ignoresSmallAmounts() {
        assertThat(parser.parseToEur("£1,000 a year learning budget")).isEmpty();
    }

    @Test
    @DisplayName("prefers the real salary range over a small budget figure")
    void prefersSalaryOverBudget() {
        String text = "💰 £115,000 - £150,000 ... 📚 £1,000 learning budget each year";

        var salary = parser.parseToEur(text);

        assertThat(salary).isPresent();
        assertThat(salary.get().min()).isEqualTo(134_550);
    }

    @Test
    @DisplayName("returns empty for unknown currency or no salary")
    void emptyWhenNoMatch() {
        assertThat(parser.parseToEur("We have 2,500 people in total")).isEmpty();
        assertThat(parser.parseToEur("competitive salary")).isEmpty();
        assertThat(parser.parseToEur(null)).isEmpty();
        assertThat(parser.parseToEur("  ")).isEmpty();
    }

    // The package-private constructors keep the production API tight; reflection
    // lets the unit test build the collaborators without a CDI container.
    private static CurrencyConverter newConverter(FxRateConfig rates) throws Exception {
        Constructor<CurrencyConverter> c = CurrencyConverter.class.getDeclaredConstructor(FxRateConfig.class);
        c.setAccessible(true);
        return c.newInstance(rates);
    }

    private static SalaryParser newParser(CurrencyConverter converter, long minAnnual) throws Exception {
        Constructor<SalaryParser> c = SalaryParser.class.getDeclaredConstructor(CurrencyConverter.class, long.class);
        c.setAccessible(true);
        return c.newInstance(converter, minAnnual);
    }
}
