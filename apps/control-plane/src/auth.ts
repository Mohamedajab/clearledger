import Keycloak from "keycloak-js";

export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === "true";
const keycloak = DEMO_MODE ? undefined : new Keycloak({
  url: import.meta.env.VITE_OIDC_URL ?? window.location.origin + "/auth",
  realm: import.meta.env.VITE_OIDC_REALM ?? "clearledger",
  clientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? "clearledger-web",
});
let initialization: Promise<boolean> | undefined;

export function initializeAuth(): Promise<boolean> {
  if (DEMO_MODE) return Promise.resolve(true);
  initialization ??= keycloak!.init({ onLoad: "check-sso", pkceMethod: "S256", checkLoginIframe: false });
  return initialization;
}

export function identity() {
  const token = keycloak?.tokenParsed;
  const roles = token?.realm_access?.roles ?? [];
  const scopes = typeof token?.scope === "string" ? token.scope.split(" ") : [];
  return {
    username: DEMO_MODE ? "Demo operator" : String(token?.preferred_username ?? "Operator"),
    tenant: DEMO_MODE ? "northstar" : String(token?.tenant_id ?? ""),
    canWrite: DEMO_MODE || roles.includes("ledger:write") || scopes.includes("ledger:write"),
  };
}

export async function accessToken() {
  if (!keycloak?.authenticated) throw new Error("Sign in to continue.");
  try {
    await keycloak.updateToken(30);
    if (!keycloak.token) throw new Error("Session expired.");
    return keycloak.token;
  } catch {
    expireSession();
    throw new Error("Your session expired. Sign in again.");
  }
}

export function expireSession() {
  keycloak?.clearToken();
  window.dispatchEvent(new Event("clearledger:session-expired"));
}

export async function signIn() {
  await keycloak?.login({ redirectUri: window.location.origin + "/overview" });
}

export async function signOut() {
  await keycloak?.logout({ redirectUri: window.location.origin });
}
