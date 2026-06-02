// Feature flag: when VITE_USE_API="true" the app talks to the real backend.
// Defaults to false so the app runs standalone on mock data (src/data/mockData.js).
//
// Enable for a session with:  VITE_USE_API=true npm run dev
// or set it in a .env.local file at the JobHub-ui root.
export const USE_API = import.meta.env.VITE_USE_API === "true";
