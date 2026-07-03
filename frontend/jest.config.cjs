const nextJest = require('next/jest');

const createJestConfig = nextJest({ dir: './' });

const customJestConfig = {
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/setupTests.ts'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
  testPathIgnorePatterns: ['/node_modules/', '/.next/'],
  // Coverage settings — focus on core client logic instead of pages/templates
  collectCoverage: true,
  collectCoverageFrom: [
    'src/lib/**/*.{ts,tsx}',
    'src/contexts/**/*.{ts,tsx}',
    'src/services/**/*.{ts,tsx}',
    'src/hooks/**/*.{ts,tsx}',
    'src/shared/api/**/*.{ts,tsx}',
    'src/shared/lib/**/*.{ts,tsx}',
    'src/entities/**/*.{ts,tsx}',
    '!src/**/__tests__/**',
  ],
  coverageReporters: ['text', 'lcov'],
  coverageDirectory: '<rootDir>/coverage',
  coverageThreshold: {
    global: {
      lines: 70,
      statements: 70,
      functions: 55,
      branches: 60,
    },
  },
};

module.exports = createJestConfig(customJestConfig);
