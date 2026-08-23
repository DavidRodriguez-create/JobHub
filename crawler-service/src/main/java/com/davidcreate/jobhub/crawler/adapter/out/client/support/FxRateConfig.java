package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.math.BigDecimal;

/**
 * Approximate FX rates used to convert crawled salaries into a rough EUR
 * estimate. Values are deliberately ballpark — the original figure stays in
 * the job description. Override any rate in application.properties, e.g.
 * {@code crawler.fx.gbp=1.15}.
 */
@ConfigMapping(prefix = "crawler.fx")
public interface FxRateConfig {

    @WithDefault("1.0")   BigDecimal eur();
    @WithDefault("1.17")  BigDecimal gbp();
    @WithDefault("0.92")  BigDecimal usd();
    @WithDefault("1.04")  BigDecimal chf();
    @WithDefault("0.087") BigDecimal sek();
    @WithDefault("0.086") BigDecimal nok();
    @WithDefault("0.134") BigDecimal dkk();
    @WithDefault("0.23")  BigDecimal pln();
    @WithDefault("0.68")  BigDecimal cad();
}
