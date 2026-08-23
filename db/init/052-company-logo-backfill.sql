-- 052-company-logo-backfill.sql
-- Story #429 (revised ADR 0024, tracked by #451; owned by job-service): one-time backfill
-- of crawler.company.logo_url from each company's OWN-SITE icon.
--
-- No ATS the crawler calls exposes a logo or a company web domain (verified live for
-- Greenhouse, Lever, SmartRecruiters), and the original Clearbit-derivation approach is dead
-- (the Clearbit Logo API shut down 2025-12-01, so every derived URL now 404s). So logos are
-- NOT derived or fetched at runtime: they are curated here, each pointing at the company's
-- own favicon / apple-touch-icon, and each verified to return a real image (HTTP 200) before
-- inclusion. Uncurated companies (and any brand-new one) keep a NULL logo_url and render the
-- UI initials chip; they are filled by an admin under #430 (the build-next dependency).
--
-- Keyed by company_name through a byte-for-byte copy of 051's slug mirror
-- (crawler.tmp_company_slugify), so every key resolves to the exact slug 051 stored - 051
-- drops that function at its own COMMIT, so this migration recreates its own copy and drops
-- it again. The URL points at a third party (the company's own host) and may still fail to
-- load; the UI degrades to its initials-chip fallback, so a stale entry can never render a
-- broken image.
--
-- Guard (revised ADR 0024 D3, standing rule): WHERE logo_url IS NULL AND manually_edited =
-- false. This excludes, on manually_edited ALONE, every row an admin ever touched (including
-- one whose logo was intentionally cleared back to NULL), and on logo_url, every row that
-- already has any logo. It only ever fills a genuinely empty slot. That same guard makes the
-- migration idempotent: a second run matches nothing because the first run filled every
-- eligible row.
--
-- No new grant needed: job_user already has UPDATE ON crawler.company (051).

CREATE EXTENSION IF NOT EXISTS unaccent;

-- Byte-for-byte copy of 051's crawler.tmp_company_slugify (051 drops it at its own COMMIT).
CREATE OR REPLACE FUNCTION crawler.tmp_company_logo_slugify(raw_name TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT NULLIF(
        regexp_replace(
            regexp_replace(
                regexp_replace(
                    lower(unaccent(regexp_replace(raw_name, $regex$[.,'’`´"]$regex$, '', 'g'))),
                    '[^a-z0-9]+', '-', 'g'
                ),
                '(^-+|-+$)', '', 'g'
            ),
            '-(sa|inc|ltd|gmbh|kg|co|corp|plc|nv|bv|se|ag|srl|sl|spa)$', ''
        ),
        ''
    )
$$;

UPDATE crawler.company c
SET logo_url   = v.logo_url,
    updated_at = NOW()
FROM (VALUES
    ('Abnormal Security', 'https://abnormalsecurity.com/favicon.ico'),
    ('Adyen', 'https://adyen.com/favicon.ico'),
    ('AfterShip', 'https://aftership.com/apple-touch-icon.png'),
    ('Airbnb', 'https://airbnb.com/favicon.ico'),
    ('Airtable', 'https://airtable.com/favicon.ico'),
    ('Algolia', 'https://algolia.com/favicon.ico'),
    ('Amazon', 'https://amazon.com/favicon.ico'),
    ('Amplitude', 'https://amplitude.com/favicon.ico'),
    ('Arize AI', 'https://arize.com/apple-touch-icon.png'),
    ('AssemblyAI', 'https://assemblyai.com/favicon.ico'),
    ('AWS', 'https://aws.amazon.com/apple-touch-icon.png'),
    ('Betterment', 'https://betterment.com/favicon.ico'),
    ('BeyondTrust', 'https://beyondtrust.com/favicon.ico'),
    ('Bird (MessageBird)', 'https://bird.com/favicon.ico'),
    ('Bitpanda', 'https://bitpanda.com/favicon.ico'),
    ('Bitwarden', 'https://bitwarden.com/favicon.ico'),
    ('BlaBlaCar', 'https://blablacar.com/apple-touch-icon.png'),
    ('Block (Square)', 'https://block.xyz/apple-touch-icon.png'),
    ('Blockchain.com', 'https://blockchain.com/favicon.ico'),
    ('Buildkite', 'https://buildkite.com/apple-touch-icon.png'),
    ('Cabify', 'https://cabify.com/favicon.ico'),
    ('Calendly', 'https://calendly.com/favicon.ico'),
    ('Calm', 'https://calm.com/favicon.ico'),
    ('Capgemini', 'https://capgemini.com/apple-touch-icon.png'),
    ('Carta', 'https://carta.com/apple-touch-icon.png'),
    ('Catawiki', 'https://catawiki.com/favicon.ico'),
    ('Cato Networks', 'https://catonetworks.com/apple-touch-icon.png'),
    ('Celonis', 'https://celonis.com/favicon.ico'),
    ('CircleCI', 'https://circleci.com/favicon.ico'),
    ('ClickHouse', 'https://clickhouse.com/favicon.ico'),
    ('Cloudflare', 'https://cloudflare.com/favicon.ico'),
    ('CockroachDB', 'https://cockroachlabs.com/favicon.ico'),
    ('Collective Health', 'https://collectivehealth.com/apple-touch-icon.png'),
    ('Collibra', 'https://collibra.com/apple-touch-icon.png'),
    ('Commvault', 'https://commvault.com/favicon.ico'),
    ('Contentful', 'https://contentful.com/apple-touch-icon.png'),
    ('Contentstack', 'https://contentstack.com/favicon.ico'),
    ('Criteo', 'https://criteo.com/favicon.ico'),
    ('Datadog', 'https://datadoghq.com/favicon.ico'),
    ('Doctolib', 'https://doctolib.com/apple-touch-icon.png'),
    ('Doximity', 'https://doximity.com/favicon.ico'),
    ('Dremio', 'https://dremio.com/apple-touch-icon.png'),
    ('Elastic', 'https://elastic.co/apple-touch-icon.png'),
    ('Faire', 'https://faire.com/apple-touch-icon.png'),
    ('Fireblocks', 'https://fireblocks.com/apple-touch-icon.png'),
    ('Fireworks AI', 'https://fireworks.ai/favicon.ico'),
    ('Form3', 'https://form3.com/favicon.ico'),
    ('FourKites', 'https://fourkites.com/apple-touch-icon.png'),
    ('Gemini', 'https://gemini.com/apple-touch-icon.png'),
    ('Grafana Labs', 'https://grafanalabs.com/favicon.ico'),
    ('Gsk', 'https://gsk.com/favicon.ico'),
    ('Honor', 'https://honor.com/favicon.ico'),
    ('Huntress', 'https://huntress.com/favicon.ico'),
    ('Iberdrola', 'https://iberdrola.com/favicon.ico'),
    ('Imbue', 'https://imbue.com/favicon.ico'),
    ('JetBrains', 'https://jetbrains.com/apple-touch-icon.png'),
    ('Jobteaser', 'https://jobteaser.com/favicon.ico'),
    ('JumpCloud', 'https://jumpcloud.com/favicon.ico'),
    ('Keeper Security', 'https://keepersecurity.com/favicon.ico'),
    ('Klaxoon', 'https://klaxoon.com/favicon.ico'),
    ('KnowBe4', 'https://knowbe4.com/favicon.ico'),
    ('Komodo Health', 'https://komodohealth.com/apple-touch-icon.png'),
    ('KPN', 'https://kpn.com/favicon.ico'),
    ('Kyriba', 'https://kyriba.com/favicon.ico'),
    ('LaunchDarkly', 'https://launchdarkly.com/favicon.ico'),
    ('Make (Integromat)', 'https://make.com/apple-touch-icon.png'),
    ('Malt', 'https://malt.com/apple-touch-icon.png'),
    ('Mercari', 'https://mercari.com/favicon.ico'),
    ('Mercury', 'https://mercury.com/favicon.ico'),
    ('Misfits Market', 'https://misfitsmarket.com/favicon.ico'),
    ('Mixpanel', 'https://mixpanel.com/favicon.ico'),
    ('MongoDB', 'https://mongodb.com/favicon.ico'),
    ('N26', 'https://n26.com/apple-touch-icon.png'),
    ('Neo4j', 'https://neo4j.com/apple-touch-icon.png'),
    ('Netskope', 'https://netskope.com/apple-touch-icon.png'),
    ('New Relic', 'https://newrelic.com/favicon.ico'),
    ('Nium', 'https://nium.com/favicon.ico'),
    ('Okta', 'https://okta.com/apple-touch-icon.png'),
    ('PagerDuty', 'https://pagerduty.com/favicon.ico'),
    ('Papa', 'https://papa.com/apple-touch-icon.png'),
    ('PayJoy', 'https://payjoy.com/favicon.ico'),
    ('Paypal', 'https://paypal.com/favicon.ico'),
    ('Pennylane', 'https://pennylane.com/favicon.ico'),
    ('Pie Insurance', 'https://pieinsurance.com/favicon.ico'),
    ('Ping Identity', 'https://pingidentity.com/favicon.ico'),
    ('Pinterest', 'https://pinterest.com/favicon.ico'),
    ('PlanetScale', 'https://planetscale.com/apple-touch-icon.png'),
    ('project44', 'https://project44.com/apple-touch-icon.png'),
    ('Public.com', 'https://public.com/apple-touch-icon.png'),
    ('Qonto', 'https://qonto.com/favicon.ico'),
    ('Raisin', 'https://raisin.com/favicon.ico'),
    ('Remote.com', 'https://remote.com/favicon.ico'),
    ('Ripple', 'https://ripple.com/favicon.ico'),
    ('Roblox', 'https://roblox.com/favicon.ico'),
    ('Rockstar Games', 'https://rockstargames.com/apple-touch-icon.png'),
    ('Salesloft', 'https://salesloft.com/favicon.ico'),
    ('Santander', 'https://santander.com/apple-touch-icon.png'),
    ('Scandit', 'https://scandit.com/apple-touch-icon.png'),
    ('Scopely', 'https://scopely.com/favicon.ico'),
    ('Sezzle', 'https://sezzle.com/favicon.ico'),
    ('SGS', 'https://sgs.com/apple-touch-icon.png'),
    ('Shell', 'https://shell.com/favicon.ico'),
    ('Showpad', 'https://showpad.com/favicon.ico'),
    ('Sigma Computing', 'https://sigmacomputing.com/favicon.ico'),
    ('SingleStore', 'https://singlestore.com/favicon.ico'),
    ('Skyscanner', 'https://skyscanner.com/favicon.ico'),
    ('Smallpdf', 'https://smallpdf.com/favicon.ico'),
    ('Smartsheet', 'https://smartsheet.com/apple-touch-icon.png'),
    ('Snorkel AI', 'https://snorkel.ai/favicon.ico'),
    ('SoFi', 'https://sofi.com/apple-touch-icon.png'),
    ('Speechmatics', 'https://speechmatics.com/apple-touch-icon.png'),
    ('Spendesk', 'https://spendesk.com/apple-touch-icon.png'),
    ('Spotify', 'https://spotify.com/apple-touch-icon.png'),
    ('Squarespace', 'https://squarespace.com/favicon.ico'),
    ('Starling Bank', 'https://starlingbank.com/apple-touch-icon.png'),
    ('StockX', 'https://stockx.com/apple-touch-icon.png'),
    ('Storyblok', 'https://storyblok.com/favicon.ico'),
    ('Stripe', 'https://stripe.com/favicon.ico'),
    ('Sumo Logic', 'https://sumologic.com/favicon.ico'),
    ('Swile', 'https://swile.co/favicon.ico'),
    ('Tailscale', 'https://tailscale.com/favicon.ico'),
    ('Telefonica', 'https://telefonica.com/apple-touch-icon.png'),
    ('Thales', 'https://thales.com/favicon.ico'),
    ('Thrive Market', 'https://thrivemarket.com/apple-touch-icon.png'),
    ('Twilio', 'https://twilio.com/apple-touch-icon.png'),
    ('Ubisoft', 'https://ubisoft.com/favicon.ico'),
    ('Vercel', 'https://vercel.com/favicon.ico'),
    ('Verkada', 'https://verkada.com/apple-touch-icon.png'),
    ('Visa', 'https://visa.com/favicon.ico'),
    ('VTEX', 'https://vtex.com/favicon.ico'),
    ('Wallapop', 'https://wallapop.com/apple-touch-icon.png'),
    ('Wayve', 'https://wayve.ai/favicon.ico'),
    ('Webflow', 'https://webflow.com/apple-touch-icon.png'),
    ('Weee!', 'https://sayweee.com/apple-touch-icon.png'),
    ('Yotpo', 'https://yotpo.com/apple-touch-icon.png'),
    ('YugabyteDB', 'https://yugabyte.com/apple-touch-icon.png'),
    ('Zendesk', 'https://zendesk.com/favicon.ico'),
    ('Zscaler', 'https://zscaler.com/favicon.ico')
) AS v(company_name, logo_url)
WHERE c.slug = crawler.tmp_company_logo_slugify(v.company_name)
  AND c.logo_url IS NULL
  AND c.manually_edited = false;

DROP FUNCTION crawler.tmp_company_logo_slugify(TEXT);


-- ── Verification (mechanical check, #437-style pattern) ─────────────────────────────
SELECT
    (SELECT COUNT(*) FROM crawler.company)                            AS total,
    (SELECT COUNT(*) FROM crawler.company WHERE logo_url IS NOT NULL) AS with_logo;
