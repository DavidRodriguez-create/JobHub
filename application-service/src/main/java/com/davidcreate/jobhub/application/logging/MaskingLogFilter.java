package com.davidcreate.jobhub.application.logging;

import io.quarkus.logging.LoggingFilter;
import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Global safety net that redacts personal data / secrets from every log line —
 * our own logs, the HTTP access log, the per-endpoint payload log, the outbound
 * rest-client request/response logs, and any framework-emitted message — before
 * it reaches a handler.
 *
 * <p>Registered by {@link LoggingFilter}, but that only registers it: the
 * {@code quarkus.log.console.filter=masking-log-filter} key in
 * application.properties is what actually binds it to the console handler.
 */
@LoggingFilter(name = "masking-log-filter")
public final class MaskingLogFilter implements Filter {

    static final String MASK = "**********";

    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]+");
    private static final Pattern KEYED = Pattern.compile(
            "(?i)\\b(password|passwordHash|password_hash|pwd|token|code|secret|authorization|api[_-]?key"
                    + "|x[-_]?service[-_]?key"
                    + "|firstName|first_name|lastName|last_name)\\b"
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
