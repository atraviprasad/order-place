import Keycloak from 'keycloak-js';

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'orderplace',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'orderplace-web',
});

// React StrictMode runs effects twice in development; Keycloak must be initialised exactly once.
let initPromise: Promise<boolean> | null = null;

export function initKeycloak(): Promise<boolean> {
  if (initPromise === null) {
    initPromise = keycloak.init({
      onLoad: 'login-required', // every page of this app needs a signed-in user
      pkceMethod: 'S256', // authorization code flow with PKCE, no client secret in the browser
      checkLoginIframe: false,
    });
  }
  return initPromise;
}

keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => keycloak.login());
};

/** A valid access token, refreshed when it has less than 30 seconds left. Sends the user to log in if that fails. */
export async function getToken(): Promise<string> {
  try {
    await keycloak.updateToken(30);
  } catch {
    await keycloak.login();
  }
  if (!keycloak.token) throw new Error('Not signed in');
  return keycloak.token;
}
