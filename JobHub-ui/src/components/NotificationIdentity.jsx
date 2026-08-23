// JobHub: Notification card identity row (company icon + job title).
// Story #207 / Ticket #216: card identity (company + job title). Used by the
// all-notifications page (screens/Notifications.jsx); the sidebar's redundant bell,
// which used to share this component too, was removed in story #206. Presentation
// logic (what counts as "resolved") lives in notificationPresentation.js; this
// component is the shared markup only.
//
// Story #244 / Ticket #260: logo image rendering (BR-244-2) + gate fix (BR-244-1).
// - companyLogoUrl is rendered as a real <img> when present and non-empty.
// - On image load error, the component falls back to the text-initial chip / generic
//   fallback icon; never shows a broken-image glyph (BR-244-2, AC-244-8).
// - The logo image is scoped to this component via React state, so CoLogo call sites
//   on other screens (Applications, Kanban, JobSearch) are completely unaffected.
//
// Story #429 / sub-issue #448: CoLogo (ui.jsx) itself now owns the img+onError
// degrade lifecycle (it used to live only here). The "company known" path below
// is consolidated onto CoLogo - this component no longer tracks its own
// per-row `imgError` state for that case. The "company unknown" path (EC-244-1:
// a logoUrl with no company name) is deliberately kept as its own small
// <img>/onError branch: CoLogo's own fallback is always the initials chip, but
// this screen's contract for "no company at all" is the GENERIC fallback icon,
// never a bare "?" chip - a fallback target CoLogo has no way to express for a
// caller-specific edge case, so unifying it would mean growing CoLogo's public
// API for a single consumer's corner case rather than removing real duplication.
import React from "react";
const { useState } = React;
import Icon from "./Icon.jsx";
import { CoLogo } from "./ui.jsx";
import { resolveCardIdentity, FALLBACK_LABEL } from "./notificationPresentation.js";

function NotificationIdentity({ notification }) {
  const { company, jobTitle, resolved, companyLogoUrl } = resolveCardIdentity(notification);

  const title = (
    <span className="notification-row-job-title" data-testid="notification-row-job-title">
      {resolved ? jobTitle : FALLBACK_LABEL}
    </span>
  );

  if (company) {
    // Company known: CoLogo owns the <img>/onError/chip-degrade lifecycle entirely.
    return (
      <div className="notification-row-identity">
        <CoLogo
          co={company}
          logoUrl={companyLogoUrl}
          size="sm"
          data-testid="notification-row-co-logo"
          imgTestId="notification-row-co-logo-image"
        />
        {title}
      </div>
    );
  }

  return <NotificationIdentityNoCompany companyLogoUrl={companyLogoUrl} title={title} />;
}

// EC-244-1: no company name at all. A present logoUrl is still attempted
// independently (BR-244-2), but on failure the fallback is the generic
// building icon, not a company-initials chip (there is no company to
// initial). Each row is an independent instance - no shared state.
function NotificationIdentityNoCompany({ companyLogoUrl, title }) {
  const [imgError, setImgError] = useState(false);
  const showImage = Boolean(companyLogoUrl) && !imgError;

  return (
    <div className="notification-row-identity">
      {showImage ? (
        <img
          src={companyLogoUrl}
          alt=""
          className="cologo sm"
          data-testid="notification-row-co-logo-image"
          onError={() => setImgError(true)}
        />
      ) : (
        <span className="notification-row-fallback-icon" data-testid="notification-row-fallback-icon">
          <Icon name="building" size={16} />
        </span>
      )}
      {title}
    </div>
  );
}

export { NotificationIdentity };
export default NotificationIdentity;
