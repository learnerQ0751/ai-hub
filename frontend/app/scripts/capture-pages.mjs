#!/usr/bin/env node
/**
 * 第五期控制台页面验证与截图：本机 Chrome（CDP，headless）+ 登录 + 逐页断言。
 * 用法：node scripts/capture-pages.mjs [baseUrl] [outDir]
 * 默认：http://127.0.0.1:5174、../docs/screens
 *
 * 先决条件：dev server 已启动；开发环境默认 mock 会话（admin/admin123），
 * 接口请求由 vite 代理注入 master key（CONSOLE_PROXY_MASTER_KEY）。
 */
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const baseUrl = process.argv[2] ?? 'http://127.0.0.1:5174';
const outDir = resolve(process.argv[3] ?? join(scriptDir, '../docs/screens'));
const username = process.env.CONSOLE_USER ?? 'admin';
const password = process.env.CONSOLE_PASSWORD ?? 'admin123';
const demoProject = process.env.DEMO_PROJECT ?? 'demo-console';

const pages = [
  { menu: '概览', title: '概览', file: 'overview' },
  { menu: '虚拟 Key', title: '虚拟 Key', file: 'keys', detail: '详情' },
  { menu: '模型与部署', title: '模型与部署', file: 'models', detail: '详情' },
  { menu: 'MCP Servers', title: 'MCP Servers', file: 'mcp-servers', detail: '详情' },
  { menu: 'Prompt 模板', title: 'Prompt 模板', file: 'prompts' },
  { menu: 'RAG 知识库', title: 'RAG 知识库', file: 'rag' },
  { menu: '调用日志', title: '调用日志', file: 'logs', trace: true },
  { menu: '用量与成本', title: '用量与成本', file: 'usage' },
  { menu: 'Playground', title: 'Playground', file: 'playground' },
  { menu: '系统状态', title: '系统状态', file: 'system' },
  // 审计分页在后端修复前会挂住并占满连接池，放最后跑，避免污染其他页面。
  { menu: '审计日志', title: '审计日志', file: 'audit' },
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
const profileDir = await mkdtemp(join(tmpdir(), 'console-capture-'));
const port = 9334;
const chrome = spawn(
  chromePath,
  [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--hide-scrollbars',
    // 本机环境有系统代理；localhost 必须直连，否则 CDP 导航被代理吃掉。
    '--no-proxy-server',
    '--proxy-bypass-list=*',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profileDir}`,
    'about:blank',
  ],
  { stdio: 'ignore' },
);

async function waitForDebugger() {
  for (let attempt = 0; attempt < 80; attempt += 1) {
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
  throw new Error('Chrome CDP 未在 20 秒内就绪');
}

function createClient(socket) {
  let nextId = 0;
  const pending = new Map();
  const events = [];
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
      return;
    }
    if (payload.method === 'Log.entryAdded' && payload.params?.entry?.level === 'error') {
      events.push(payload.params.entry.text);
    }
    if (payload.method === 'Runtime.exceptionThrown') {
      const details = payload.params?.exceptionDetails;
      const error = details?.exception;
      events.push(
        [
          details?.text ?? 'runtime exception',
          error?.description ?? error?.value ?? details?.url ?? '',
          details?.lineNumber !== undefined
            ? `line ${details.lineNumber}:${details.columnNumber}`
            : '',
        ]
          .filter(Boolean)
          .join(' '),
      );
    }
  });
  const send = (method, params = {}) =>
    new Promise((resolveCall, reject) => {
      nextId += 1;
      pending.set(nextId, { resolve: resolveCall, reject });
      socket.send(JSON.stringify({ id: nextId, method, params }));
    });
  return { send, events };
}

let socket;
const report = [];
try {
  const wsUrl = await waitForDebugger();
  socket = new WebSocket(wsUrl);
  await new Promise((resolveOpen, reject) => {
    socket.addEventListener('open', resolveOpen, { once: true });
    socket.addEventListener('error', () => reject(new Error('CDP 连接失败')), { once: true });
  });

  const { send, events } = createClient(socket);
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Log.enable');
  await mkdir(outDir, { recursive: true });

  const evaluate = async (expression) => {
    const result = await send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
    });
    return result.result?.value;
  };

  const waitFor = async (expression, label, timeoutMs = 20000) => {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      if (await evaluate(expression)) {
        return true;
      }
      await sleep(200);
    }
    console.warn(`等待超时：${label}`);
    return false;
  };

  const screenshot = async (name) => {
    const shot = await send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: true,
    });
    const file = join(outDir, `${name}.png`);
    await writeFile(file, Buffer.from(shot.data, 'base64'));
    return file;
  };

  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  });

  // 登录页
  const navResult = await send('Page.navigate', { url: `${baseUrl}/login` });
  console.log('navigate ->', JSON.stringify(navResult));
  const loginReady = await waitFor("Boolean(document.querySelector('#username'))", '登录表单');
  if (!loginReady) {
    const debug = await evaluate(
      'JSON.stringify({href: location.href, ready: document.readyState, html: document.body?.innerHTML.slice(0, 300), text: document.body?.innerText.slice(0, 200)})',
    );
    throw new Error(`登录页未挂载：${debug}`);
  }
  await sleep(600);
  await screenshot('login');

  const fill = (selector, value) => {
    const script = `(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) return false;
      const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
      setter.call(el, ${JSON.stringify(value)});
      el.dispatchEvent(new Event('input', { bubbles: true }));
      return true;
    })()`;
    return evaluate(script);
  };

  await fill('#username', username);
  await fill('#password', password);
  await evaluate("document.querySelector('button[type=submit]')?.click()");
  const loggedIn = await waitFor("location.pathname === '/overview'", '登录跳转', 25000);
  if (!loggedIn) {
    const body = await evaluate('document.body.innerText.slice(0, 300)');
    throw new Error(`登录失败：${body}`);
  }
  // 懒加载外壳与侧栏：等 11 个菜单项都挂载后再逐页点。
  await waitFor("document.querySelectorAll('.ant-menu-item').length >= 11", '侧栏菜单', 25000);
  await sleep(1500);

  const clickMenu = async (label) => {
    for (let attempt = 0; attempt < 12; attempt += 1) {
      const clicked = await evaluate(`(() => {
        const items = [...document.querySelectorAll('.ant-menu-item')];
        const target = items.find((item) => item.textContent.trim() === ${JSON.stringify(label)});
        if (!target) return false;
        target.click();
        return true;
      })()`);
      if (clicked) {
        return true;
      }
      await sleep(400);
    }
    return false;
  };

  const collect = () =>
    evaluate(`(() => {
      const text = (selector) => document.querySelector(selector)?.innerText?.trim() ?? '';
      return {
        path: location.pathname,
        title: text('.page-title'),
        rows: document.querySelectorAll('.ant-table-tbody tr.ant-table-row').length,
        cards: document.querySelectorAll('.console-card, .mcp-card').length,
        errors: [...document.querySelectorAll('.console-error-panel, .mcp-error-panel')].map((el) => el.innerText.replace(/\\s+/g, ' ').trim()),
        alerts: [...document.querySelectorAll('.ant-alert')].map((el) => el.innerText.replace(/\\s+/g, ' ').trim()).slice(0, 3),
        body: document.body.innerText.replace(/\\s+/g, ' ').slice(0, 400),
      };
    })()`);

  console.log(
    'after login ->',
    await evaluate(
      "JSON.stringify({href: location.href, menuItems: [...document.querySelectorAll('.ant-menu-item')].map((el) => el.textContent.trim())})",
    ),
  );

  for (const page of pages) {
    const clicked = await clickMenu(page.menu);
    if (!clicked) {
      const body = await evaluate(
        "JSON.stringify({href: location.href, menu: [...document.querySelectorAll('.ant-menu-item')].map((el) => el.textContent.trim()), text: document.body.innerText.slice(0, 300)})",
      );
      report.push({ file: page.file, ok: false, reason: `找不到菜单：${page.menu} :: ${body}` });
      continue;
    }
    await waitFor(
      `document.querySelector('.page-title')?.innerText.trim() === ${JSON.stringify(page.title)}`,
      `页面标题 ${page.title}`,
    );
    // 等数据加载骨架消失，避免截到 loading 态。
    await waitFor(
      "!document.querySelector('.console-skeleton, .mcp-skeleton')",
      `数据加载 ${page.title}`,
      15000,
    );
    await sleep(800);

    if (page.file === 'rag') {
      // 不整页刷新（mock 会话在内存里），用 history + popstate 触发路由内跳转。
      await evaluate(`(() => {
        history.pushState({}, '', '/rag?project=${encodeURIComponent(demoProject)}');
        window.dispatchEvent(new PopStateEvent('popstate'));
      })()`);
      await sleep(800);
      await waitFor(
        "!document.querySelector('.console-skeleton') && document.querySelectorAll('.ant-table-tbody tr.ant-table-row').length > 0",
        'RAG 文档加载',
        15000,
      );
      await sleep(600);
      const openedChunks = await evaluate(`(() => {
        const buttons = [...document.querySelectorAll('button')];
        const target = buttons.find((button) => button.textContent.trim() === '切片');
        if (!target) return false;
        target.click();
        return true;
      })()`);
      if (openedChunks) {
        await sleep(1800);
        await screenshot('rag-chunks-1440');
        await send('Input.dispatchKeyEvent', {
          type: 'keyDown',
          key: 'Escape',
          code: 'Escape',
          windowsVirtualKeyCode: 27,
        });
        await send('Input.dispatchKeyEvent', {
          type: 'keyUp',
          key: 'Escape',
          code: 'Escape',
          windowsVirtualKeyCode: 27,
        });
        await sleep(600);
      }
    }

    if (page.file === 'playground' && process.env.CONSOLE_PLAYGROUND_KEY) {
      await fill('input[placeholder^="临时虚拟 Key"]', process.env.CONSOLE_PLAYGROUND_KEY);
      await fill('textarea[placeholder="输入要发送的消息"]', '用一句话说明 ai-hub 是什么。');
      await evaluate(`(() => {
        const buttons = [...document.querySelectorAll('button')];
        const target = buttons.find((button) => button.textContent.trim() === '发送');
        target?.click();
      })()`);
      const answered = await waitFor(
        "document.querySelectorAll('.ant-card').length > 2 && document.body.innerText.includes('助手')",
        'Playground 对话结果',
        45000,
      );
      if (answered) {
        await sleep(500);
        await screenshot('playground-chat-1440');
      }
    }

    const state = await collect();
    const file = await screenshot(`${page.file}-1440`);
    const pageErrors = events.splice(0);
    report.push({
      file: page.file,
      ok: state.title === page.title && state.errors.length === 0,
      ...state,
      screenshot: file,
      consoleErrors: pageErrors,
    });

    if (page.detail) {
      const opened = await evaluate(`(() => {
        const buttons = [...document.querySelectorAll('button')];
        const target = buttons.find((button) => button.textContent.trim() === ${JSON.stringify(page.detail)});
        if (!target) return false;
        target.click();
        return true;
      })()`);
      if (opened) {
        await sleep(1200);
        await screenshot(`${page.file}-detail-1440`);
        await send('Input.dispatchKeyEvent', {
          type: 'keyDown',
          key: 'Escape',
          code: 'Escape',
          windowsVirtualKeyCode: 27,
        });
        await send('Input.dispatchKeyEvent', {
          type: 'keyUp',
          key: 'Escape',
          code: 'Escape',
          windowsVirtualKeyCode: 27,
        });
        await sleep(600);
      }
    }

    if (page.trace) {
      const opened = await evaluate(`(() => {
        const buttons = [...document.querySelectorAll('button')];
        const target = buttons.find((button) => button.textContent.trim() === '链路' && !button.disabled);
        if (!target) return false;
        target.click();
        return true;
      })()`);
      if (opened) {
        await sleep(1600);
        await screenshot(`${page.file}-trace-1440`);
        await send('Input.dispatchKeyEvent', {
          type: 'keyDown',
          key: 'Escape',
          code: 'Escape',
          windowsVirtualKeyCode: 27,
        });
        await send('Input.dispatchKeyEvent', {
          type: 'keyUp',
          key: 'Escape',
          code: 'Escape',
          windowsVirtualKeyCode: 27,
        });
        await sleep(600);
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

await writeFile(join(outDir, 'verify-summary.json'), JSON.stringify(report, null, 2), 'utf8');
for (const page of report) {
  console.log(
    `${page.ok ? 'OK  ' : 'FAIL'} ${page.file} rows=${page.rows ?? '-'} cards=${page.cards ?? '-'} errors=${(page.errors ?? []).length}${page.reason ? ` reason=${page.reason}` : ''}`,
  );
  if (page.errors?.length) {
    console.log(`     DOM: ${page.errors.join(' | ').slice(0, 300)}`);
  }
  if (page.consoleErrors?.length) {
    console.log(`     JS: ${page.consoleErrors.join(' | ').slice(0, 600)}`);
  }
}
const failed = report.filter((page) => !page.ok);
process.exit(failed.length === 0 ? 0 : 1);
