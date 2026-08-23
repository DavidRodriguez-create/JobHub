package com.davidcreate.jobhub.auth.logging;

import io.quarkus.logging.LoggingFilter;
import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Global safety net that redacts personal data / secrets from every log line —
 * our own logs, the HTTP access log, and any framework-emitted message — before
 * it reaches a handler. Registered automatically on the log handlers by
 * {@link LoggingFilter}; no configuration needed.
 *
 * <p>This is a catch-all. Prefer not to log secrets in the first place; this
 * exists so an incidental email/token/hash never leaks to stdout.
 *
 * <p>The dev-only {@code LoggingVerificationNotifier} is exempt on purpose: with
 * no real email provider wired in, its log line is how a developer reads the
 * verification token/code locally. A production email adapter replaces it.
 */
@LoggingFilter(name = "masking-log-filter")
public final class MaskingLogFilter implements Filter {

    static final String MASK = "**********";

    private static final String NOTIFIER_LOGGER =
            "com.davidcreate.jobhub.auth.adapter.out.notification.LoggingVerificationNotifier";

    // Order matters: keyed forms (token=…, Bearer …) run before the broad token
    // shapes so the value is masked but the key/prefix stays readable.
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]+");
    // Matches key=value, key: value, and JSON "key":"value" (quotes around the key
    // and/or value are tolerated and preserved; only the value is replaced).
    // Names are PII but have no distinctive shape, so they can only be masked when
    // labelled with a known key — a bare "John" is indistinguishable from any word.
    // Apply-profile answer-bank fields (salary, location, work authorization, links, and
    // more) are personal data, and RequestLoggingFilter logs full JSON bodies, so mask
    // them by key too. Token-based like the rest of KEYED: a multi-word free-text value is
    // masked up to the first space, a known limitation shared by every keyed field here.
    private static final Pattern KEYED = Pattern.compile(
            "(?i)\\b(password|passwordHash|password_hash|pwd|token|code|secret|authorization|api[_-]?key"
                    + "|x[-_]?service[-_]?key"
                    + "|firstName|first_name|lastName|last_name"
                    + "|workAuthorization|work_authorization|noticePeriod|notice_period"
                    + "|salaryExpectation|salary_expectation|currentLocation|current_location"
                    + "|linkedinUrl|linkedin_url|githubUrl|github_url|portfolioUrl|portfolio_url"
                    + "|languages|roomToGrow|room_to_grow)\\b"
                    + "([\"']?\\s*[=:]\\s*[\"']?)([^\\s\",}]+)");
    private static final Pattern JWT = Pattern.compile(
            "eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    // getFormattedMessage() is deprecated in favour of letting the Formatter render
    // the message — but a masking filter must inspect and rewrite the fully-resolved
    // text before it reaches any handler, which is exactly that method's job.
    @Override
    @SuppressWarnings("deprecation")
    public boolean isLoggable(LogRecord record) {
        if (NOTIFIER_LOGGER.equals(record.getLoggerName())) {
            return true;
        }
        if (record instanceof ExtLogRecord ext) {
            String formatted = ext.getFormattedMessage();
            String masked = mask(formatted);
            if (formatted != null && !formatted.equals(masked)) {
                ext.setMessage(masked, ExtLogRecord.FormatStyle.NO_FORMAT);
                ext.setParameters(null);
            }
        } else {
            String msg = record.getMessage();
            String masked = mask(msg);
            if (msg != null && !masked.equals(msg)) {
                record.setMessage(masked);
                record.setParameters(null);
            }
        }
        return true;
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = input;
        out = BEARER.matcher(out).replaceAll("$1" + MASK);
        out = KEYED.matcher(out).replaceAll("$1$2" + MASK);
        out = JWT.matcher(out).replaceAll(MASK);
        out = BCRYPT.matcher(out).replaceAll(MASK);
        out = EMAIL.matcher(out).replaceAll(MASK);
        return out;
    }
}
