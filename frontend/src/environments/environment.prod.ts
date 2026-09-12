// In production this should be replaced at build time (or via a generated
// environment file in CI/CD) with the real API URL - never commit a real
// production URL or secret into source control.
export const environment = {
  production: true,
  apiBaseUrl: '/api', // assumes frontend and backend share a domain behind a reverse proxy; override per deployment
};
