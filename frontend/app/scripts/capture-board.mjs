#!/usr/bin/env node
/**
 * 用本机 Chrome 的 CDP 按精确视口截取 Design Board 预览页。
 * 用法：node scripts/capture-board.mjs [baseUrl] [outDir] [page1,page2]
 * 默认：http://127.0.0.1:5173、../board/screens、keys,models
 */
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const baseUrl = process.argv[2] ?? 'http://127.0.0.1:5173';
const outDir = resolve(process.argv[3] ?? join(scriptDir, '../../board/screens'));
const pages = (process.argv[4] ?? 'keys,models').split(',').filter(Boolean);
const modes = ['ok', 'loading', 'empty', 'error'];
const viewports = [
  { name: '1440', width: 1440, height: 900 },
  { name: '768', width: 768, height: 1280 },
  { name: '375', width: 375, height: 1280 },
];

const chromeCandidates = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean);
const chromePath = chromeCandidates.find((path) => existsSync(path));
if (!chromePath) {
  console.error('找不到 Chrome，可用 CHROME_PATH 指定。');
  process.exit(1);
}

const sleep = (ms) => new Promise((done) => setTimeout(done, ms));

const profileDir = await mkdtemp(join(tmpdir(), 'board-capture-'));
const port = 9333;
const chrome = spawn(
  chromePath,
  [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--hide-scrollbars',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profileDir}`,
    'about:blank',
  ],
  { stdio: 'ignore' },
);

async function waitForDebugger() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/list`);
      const targets = await response.json();
      const page = targets.find((target) => target.type === 'page');
      if (page?.webSocketDebuggerUrl) {
        return page.webSocketDebuggerUrl;
      }
    } catch {
      // Chrome 尚未就绪
    }
    await sleep(250);
  }
  throw new Error('Chrome CDP 未在 15 秒内就绪');
}

function createClient(socket) {
  let nextId = 0;
  const pending = new Map();
  socket.addEventListener('message', (event) => {
    const payload = JSON.parse(event.data);
    if (payload.id && pending.has(payload.id)) {
      const { resolve: resolveCall, reject } = pending.get(payload.id);
      pending.delete(payload.id);
      if (payload.error) {
        reject(new Error(payload.error.message));
      } else {
        resolveCall(payload.result);
      }
    }
  });
  return (method, params = {}) =>
    new Promise((resolveCall, reject) => {
      nextId += 1;
      pending.set(nextId, { resolve: resolveCall, reject });
      socket.send(JSON.stringify({ id: nextId, method, params }));
    });
}

let socket;
try {
  const wsUrl = await waitForDebugger();
  socket = new WebSocket(wsUrl);
  await new Promise((resolveOpen, reject) => {
    socket.addEventListener('open', resolveOpen, { once: true });
    socket.addEventListener('error', () => reject(new Error('CDP 连接失败')), { once: true });
  });

  const send = createClient(socket);
  await send('Page.enable');
  await mkdir(outDir, { recursive: true });

  for (const page of pages) {
    // page=board：截 Board 总览（默认页面），只出 1440、文件名 board-overview.png。
    const isBoardOverview = page === 'board';
    const pageViewports = isBoardOverview
      ? [{ name: 'overview', width: 1440, height: 900 }]
      : viewports;
    const pageModes = isBoardOverview ? ['ok'] : modes;
    for (const viewport of pageViewports) {
      for (const mode of pageModes) {
        await send('Emulation.setDeviceMetricsOverride', {
          width: viewport.width,
          height: viewport.height,
          deviceScaleFactor: 1,
          mobile: false,
        });
        const url = isBoardOverview
          ? `${baseUrl}/board?t=${Date.now()}`
          : `${baseUrl}/board/preview/${page}?mode=${mode}&t=${Date.now()}`;
        await send('Page.navigate', { url });
        let mounted = false;
        for (let attempt = 0; attempt < 100; attempt += 1) {
          const state = await send('Runtime.evaluate', {
            expression: isBoardOverview
              ? "Boolean(document.querySelector('.board-page')) && document.readyState === 'complete'"
              : "Boolean(document.querySelector('.app-content')) && document.readyState === 'complete'",
            returnByValue: true,
          });
          if (state.result?.value === true) {
            mounted = true;
            break;
          }
          await sleep(200);
        }
        if (!mounted) {
          const debug = await send('Runtime.evaluate', {
            expression:
              "JSON.stringify({href: location.href, ready: document.readyState, root: document.querySelector('#root')?.innerHTML.length ?? -1, body: document.body?.innerText.slice(0, 200)})",
            returnByValue: true,
          });
          console.warn(`页面未挂载：${url}\n${debug.result?.value}`);
        }
        await sleep(isBoardOverview ? 5000 : mode === 'loading' ? 900 : 1400);
        const shot = await send('Page.captureScreenshot', {
          format: 'png',
          captureBeyondViewport: false,
        });
        const file = join(
          outDir,
          isBoardOverview ? 'board-overview.png' : `${page}-${viewport.name}-${mode}.png`,
        );
        await writeFile(file, Buffer.from(shot.data, 'base64'));
        console.log(`saved ${file}`);
      }
    }
  }
} finally {
  if (socket) {
    socket.close();
  }
  chrome.kill();
  try {
    await rm(profileDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
  } catch {
    // Chrome 退出时偶尔仍占用 profile，忽略清理失败
  }
}
