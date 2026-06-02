import React from "react";
// JobHub — Add Application modal form
import { Button, Input, Field, Modal } from "./ui.jsx";

const NOTES_MAX = 2000;

function AddApplicationModal({ onClose, onSubmit }) {
  const [title, setTitle] = React.useState("");
  const [company, setCompany] = React.useState("");
  const [location, setLocation] = React.useState("");
  const [comp, setComp] = React.useState("");
  const [postUrl, setPostUrl] = React.useState("");
  const [portalUrl, setPortalUrl] = React.useState("");
  const [appliedOn, setAppliedOn] = React.useState(new Date().toISOString().slice(0, 10));
  const [status, setStatus] = React.useState("applied");
  const [notes, setNotes] = React.useState("");
  const [errors, setErrors] = React.useState({});

  const validate = () => {
    const e = {};
    if (!title.trim()) e.title = "Job title is required";
    if (!company.trim()) e.company = "Company name is required";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = (ev) => {
    ev.preventDefault();
    if (!validate()) return;
    onSubmit({
      title: title.trim(),
      company: company.trim(),
      location: location.trim() || "—",
      comp: comp.trim() || "—",
      postUrl: postUrl.trim(),
      portalUrl: portalUrl.trim(),
      appliedOn: appliedOn,
      status: status,
      notes: notes.trim(),
    });
  };

  return (
    <Modal title="Add application" onClose={onClose} wide
      footer={
        <div style={{ display: "flex", width: "100%", justifyContent: "space-between", alignItems: "center" }}>
          <span style={{ fontSize: 12, color: "var(--color-ink-3)" }}>
            For jobs you found outside JobHub
          </span>
          <div style={{ display: "flex", gap: 8 }}>
            <Button variant="ghost" onClick={onClose}>Cancel</Button>
            <Button variant="primary" icon="plus" onClick={handleSubmit}>Add application</Button>
          </div>
        </div>
      }>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <Field label="Job title" error={errors.title}>
            <Input placeholder="e.g. Senior Product Designer" value={title}
              onChange={(e) => setTitle(e.target.value)} autoFocus />
          </Field>
          <Field label="Company" error={errors.company}>
            <Input placeholder="e.g. Spotify" value={company}
              onChange={(e) => setCompany(e.target.value)} />
          </Field>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <Field label="Location" hint="Optional">
            <Input placeholder="e.g. Remote · US" value={location}
              onChange={(e) => setLocation(e.target.value)} />
          </Field>
          <Field label="Compensation" hint="Optional">
            <Input placeholder="e.g. €150k–€200k" value={comp}
              onChange={(e) => setComp(e.target.value)} />
          </Field>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <Field label="Date applied">
            <Input type="date" value={appliedOn}
              onChange={(e) => setAppliedOn(e.target.value)} />
          </Field>
          <Field label="Status">
            <select className="input" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="applied">Applied</option>
              <option value="screening">Screening</option>
              <option value="interview">Interview</option>
              <option value="offer">Offer</option>
              <option value="accepted">Accepted</option>
              <option value="rejected">Rejected</option>
              <option value="ghosted">Ghosted</option>
              <option value="withdrawn">Withdrawn</option>
            </select>
          </Field>
        </div>

        <Field label="Job post URL" hint="Link to the original posting">
          <Input type="url" placeholder="https://…" leading="link" value={postUrl}
            onChange={(e) => setPostUrl(e.target.value)} />
        </Field>

        <Field label="Career portal URL" hint="Company's application tracking page">
          <Input type="url" placeholder="https://…" leading="building" value={portalUrl}
            onChange={(e) => setPortalUrl(e.target.value)} />
        </Field>

        <Field label="Notes" hint="Anything you want to remember about this application">
          <textarea className="input" placeholder="Cover letter details, referral contact, interview notes…"
            value={notes} onChange={(e) => setNotes(e.target.value.slice(0, NOTES_MAX))}
            maxLength={NOTES_MAX} rows={3}
            style={{ width: "100%", overflowWrap: "anywhere", wordBreak: "break-word" }} />
          <div className="mono" style={{ marginTop: 6, textAlign: "right", fontSize: 11,
            color: notes.length >= NOTES_MAX ? "var(--color-danger)" : "var(--color-ink-4)" }}>
            {notes.length} / {NOTES_MAX}
          </div>
        </Field>
      </form>
    </Modal>
  );
}

export { AddApplicationModal };
