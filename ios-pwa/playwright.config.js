const { defineConfig, devices } = require('@playwright/test');
module.exports = defineConfig({
  testDir: './tests',
  timeout: 30000,
  retries: 0,
  use: {
    baseURL: 'http://127.0.0.1:4173',
    ...devices['iPhone 7'],
    browserName: 'webkit'
  },
  webServer: {
    command: 'npx http-server . -p 4173 -c-1',
    port: 4173,
    reuseExistingServer: false
  }
});