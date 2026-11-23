/** Jest setup for the Vite + React + TypeScript front end (this file is .cjs because package.json has "type": "module"). */
module.exports = {
  testEnvironment: 'jest-environment-jsdom', // a fake browser (document, window) so React components can render
  roots: ['<rootDir>/src/jest.config.cjs'],
  testMatch: ['**/*.test.ts', '**/*.test.tsx'],
  transform: {
    // ts-jest turns TypeScript and JSX into CommonJS for Jest; type errors are caught by `npm run build`, not here
    '^.+\\.tsx?$': ['ts-jest', { tsconfig: 'tsconfig.jest.json', isolatedModules: true }],
  },
  moduleNameMapper: {
    // keycloak.ts uses import.meta.env (Vite only) and talks to a real server: tests get a fake instead
    '.*/auth/keycloak$': '<rootDir>/src/test/keycloakMock.ts',
  },
  setupFilesAfterEnv: ['<rootDir>/src/test/setupTests.ts'],
  clearMocks: true, // reset call counts between tests
};
