// JobHub — in-memory data store.
//
// There is no mock/sample data: the store starts empty and is hydrated from the
// backend APIs at runtime (jobs from job-service at boot; the signed-in user's
// applications and saved jobs after login — see App.jsx `loadUserData`). With
// VITE_USE_API off the store simply stays empty.
//
// The shape (companies map, jobs[], applications[], saved[] + helpers) is kept so
// the screens can keep reading this singleton synchronously.

const DATA = (function () {
  const companies = {}; // key -> { name, industry, size, hq, url, logoUrl? }
  const jobs = [];        // UI job view-model (see mappers.jobFromApi)
  const applications = []; // UI application view-model (see mappers.appFromApi)
  const saved = [];        // array of saved job ids

  const byId = (id) => jobs.find((j) => j.id === id);
  const coOf = (co) => companies[co] || { name: co, industry: "—", size: "—", hq: "—", url: "" };
  const appForJob = (jobId) => applications.find((a) => a.jobId === jobId);
  const nextAppId = () => "APP-" + String(applications.length + 1).padStart(3, "0");

  return { companies, jobs, applications, saved, byId, coOf, appForJob, nextAppId };
})();

export default DATA;
