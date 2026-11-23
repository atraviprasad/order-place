import { jest } from '@jest/globals';

// stands in for src/auth/keycloak.ts in every test (see moduleNameMapper in jest.config.cjs)
export const keycloak = {
  token: 'test-token' as string | undefined,
  updateToken: jest.fn<(minValidity: number) => Promise<boolean>>(async () => true),
  login: jest.fn<() => Promise<void>>(async () => undefined),
};
export const getToken = jest.fn<() => Promise<string>>(async () => 'test-token');
export const initKeycloak = jest.fn<() => Promise<boolean>>(async () => true);
