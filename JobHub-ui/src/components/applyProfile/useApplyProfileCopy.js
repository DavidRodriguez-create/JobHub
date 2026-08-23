import React from "react";

/* ── Apply profile: shared copy-to-clipboard behaviour ──
   Extracted from ApplyProfileSection's inline handleCopy/copiedField logic
   (screens/SavedSettings.jsx) so both the editable Settings section and the
   read-only drawer (story #460) share the exact same copy semantics.

   Read-only and local (BR-8): never calls saveApplyProfile. Acts on the exact
   on-screen value it is given, shows a "Copied to clipboard." toast, and flips
   on a per-field transient "Copied" confirmation that clears after 1500ms or
   when a different field is copied (never two fields' confirmations at once). */
export function useApplyProfileCopy(pushToast) {
  const [copiedField, setCopiedField] = React.useState(null);
  const copyTimeoutRef = React.useRef(null);

  React.useEffect(() => () => {
    if (copyTimeoutRef.current) clearTimeout(copyTimeoutRef.current);
  }, []);

  function handleCopy(key, value) {
    const hasValue = value !== null && value !== undefined && value !== "";
    if (!hasValue) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value);
    }
    setCopiedField(key);
    if (pushToast) pushToast("Copied to clipboard.", "copy");
    if (copyTimeoutRef.current) clearTimeout(copyTimeoutRef.current);
    copyTimeoutRef.current = setTimeout(() => setCopiedField((c) => (c === key ? null : c)), 1500);
  }

  return { copiedField, handleCopy };
}
