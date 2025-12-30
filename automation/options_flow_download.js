#!/usr/bin/env node
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import puppeteer from 'puppeteer';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const config = {
  url: process.env.CHEDDARFLOW_URL || 'https://app.cheddarflow.com/',
  downloadDir: path.resolve(process.env.DOWNLOAD_DIR || path.join(__dirname, 'downloads')),
  headless: process.env.HEADLESS !== 'false',
  userDataDir: process.env.USER_DATA_DIR || path.join(__dirname, 'user-data')
};

async function ensureDirectory(dirPath) {
  await fs.promises.mkdir(dirPath, { recursive: true });
}

async function run() {
  await ensureDirectory(config.downloadDir);
  await ensureDirectory(config.userDataDir);

  const browser = await puppeteer.launch({
    headless: config.headless,
    userDataDir: config.userDataDir,
    defaultViewport: { width: 1280, height: 800 }
  });

  const page = await browser.newPage();
  await page.setDefaultNavigationTimeout(90_000);

  await page._client().send('Page.setDownloadBehavior', {
    behavior: 'allow',
    downloadPath: config.downloadDir
  });

  console.log(`Navigating to ${config.url}...`);
  await page.goto(config.url, { waitUntil: 'networkidle2' });

  await page.waitForSelector('.ag-center-cols-container div.ag-row', { timeout: 90_000 });

  const optionsScriptPath = path.resolve(__dirname, '..', 'optionsMutator.js');
  if (!fs.existsSync(optionsScriptPath)) {
    throw new Error('optionsMutator.js was not found next to the repo root.');
  }

  console.log('Injecting optionsMutator script and waiting for CSV download...');
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 90_000 }),
    page.addScriptTag({ path: optionsScriptPath })
  ]);

  const downloadPath = await download.path();
  const fileName = download.suggestedFilename();
  console.log(`Download completed: ${fileName}`);
  console.log(`Saved to: ${downloadPath}`);

  await browser.close();
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
