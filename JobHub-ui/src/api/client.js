// JobHub — low-level HTTP client shared by all service modules.
//
// All requests are same-origin: the Vite dev server (dev) and nginx (prod)
// reverse-proxy each path prefix to the right backend service:
//   /auth/*           -> auth-service        (:8082)
//   /jobs/*           -> job-service         (:8081)
//   /applications/*   -> application-service (:8083)

const TOKEN_KEY = "jobhub_token";

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}
export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {}
}
export function clearToken() { setToken(null); }

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message || `HTTP ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

/**
 * @returns {Promise<{ data: any, total: number|null, status: number }>}
 */
export async function request(path, { method = "GET", body, auth = false, headers = {} } = {}) {
  const finalHeaders = { Accept: "application/json", ...headers };
  if (body !== undefined) finalHeaders["Content-Type"] = "application/json";
  if (auth) {
    const token = getToken();
    if (token) finalHeaders["Authorization"] = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(path, {
      method,
      headers: finalHeaders,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (networkErr) {
    throw new ApiError(0, "Network error — is the backend reachable?", networkErr);
  }

  const totalHeader = res.headers.get("X-Total-Count");
  const total = totalHeader != null ? Number(totalHeader) : null;

  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }

  if (!res.ok) {
    const msg = data && data.message ? data.message : data && data.error ? data.error : res.statusText;
    throw new ApiError(res.status, msg, data);
  }

  return { data, total, status: res.status };
}
