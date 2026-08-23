package com.davidcreate.jobhub.crawler.adapter.out.client.support;

/**
 * The shared extraction instructions sent to every enrichment provider. Keeping
 * the system prompt, schema and user-prompt builder in one place guarantees the
 * hosted model (Gemini) and the local fallback (Ollama) receive identical
 * instructions, so their output flows through the same {@link EnrichmentParser}.
 */
public final class EnrichmentPrompt {

    private EnrichmentPrompt() {}

    // Cap the description so the prompt stays small/fast for a local model.
    public static final int MAX_DESCRIPTION = 6000;

    public static final String SYSTEM_PROMPT = """
            You extract structured data from a single job posting.
            Ground every value in the provided text — do not invent facts, and when the
            text gives no basis for a field, use null. You MAY apply limited, well-grounded
            inference, but ONLY where the per-field rules below allow it. Compensation is
            never inferred or estimated. Respond with a single JSON object and nothing else.""";

    public static final String SCHEMA_INSTRUCTIONS = """
            Return JSON with exactly these keys:
            {
              "employmentType": one of ["full-time","part-time","contract","freelance","internship"] or null.
                    Use the stated type, or infer it when the text clearly implies one
                    (e.g. "internship" -> internship, "6-month contract" -> contract).
                    No clear signal -> null.
              "careerLevel": one of ["internship","junior","mid","senior","lead","principal","manager","director"] or null.
                    Use the stated level, or infer from clear seniority signals: required
                    years of experience, "graduate"/"entry-level" -> junior, "5+ years"/
                    "senior" -> senior, "leads/owns a team" -> lead or manager.
                    No real signal -> null.
              "languages": array of working/spoken languages required for the role.
                    Use ONLY the five supported canonical names: English, Spanish, French,
                    Chinese, German. Include any language the posting explicitly requires AND
                    the human language the posting itself is written in.
                    Do NOT include programming languages (Python, Java, SQL, etc.) — those
                    belong in "requirements" only.
                    Use [] only if you genuinely cannot tell.
              "requirements": array of up to 15 concise key skills/requirements; [] if not stated,
              "city": the city of the role, or null. Prefer the description; if it does not
                    state a city, you may use the PARSED LOCATION hint below when it names
                    a real city.
              "country": the country of the role, or null. If a city is known but the country
                    is not stated, deduce the country from the city (e.g. "London" ->
                    "United Kingdom"). Never do the reverse — do not invent a city from
                    only a country.
              "remote": true if the role can be done remotely, else false,
              "compensationMin": the lower salary number ONLY if a salary figure is explicitly stated, else null,
              "compensationMax": the upper salary number if explicitly stated, else null,
              "currency": ISO code of the stated salary (e.g. "GBP","EUR","USD") or null
            }
            Compensation is the one hard exception: fill it only when an actual figure
            appears in the text. Never estimate or guess a salary; report the figure and
            currency exactly as written.""";

    public static String buildUserPrompt(String title, String description, String city, String country) {
        String desc = description == null ? "" : description;
        if (desc.length() > MAX_DESCRIPTION) {
            desc = desc.substring(0, MAX_DESCRIPTION);
        }
        StringBuilder sb = new StringBuilder(SCHEMA_INSTRUCTIONS)
                .append("\n\nJOB TITLE:\n").append(title == null ? "" : title);

        String parsedLocation = joinLocation(city, country);
        if (!parsedLocation.isBlank()) {
            sb.append("\n\nPARSED LOCATION (best-effort, may be inaccurate — trust the description first):\n")
                    .append(parsedLocation);
        }

        return sb.append("\n\nJOB DESCRIPTION:\n").append(desc).toString();
    }

    private static String joinLocation(String city, String country) {
        String c = city == null ? "" : city.trim();
        String co = country == null ? "" : country.trim();
        if (c.isEmpty()) return co;
        if (co.isEmpty()) return c;
        return c + ", " + co;
    }
}
