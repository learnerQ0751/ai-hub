import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';

// 仅开发环境：把控制台请求代理到本地 console，避免跨域与 cookie 路径问题。
// CONSOLE_PROXY_MASTER_KEY 只是本机调试用（Board 截图、接口实测），不进前端代码、不进生产构建。
const consoleProxyTarget = process.env.CONSOLE_PROXY_TARGET ?? 'http://127.0.0.1:18081';
// 仅开发环境：数据面（gateway）走 /__gateway 前缀代理，避开浏览器跨域；
// 生产由反向代理或 VITE_GATEWAY_BASE_URL 提供，不进构建产物。
const gatewayProxyTarget = process.env.GATEWAY_PROXY_TARGET ?? 'http://127.0.0.1:18080';
const consoleProxyMasterKey = process.env.CONSOLE_PROXY_MASTER_KEY;
const proxyHeaders = consoleProxyMasterKey
  ? { Authorization: `Bearer ${consoleProxyMasterKey}` }
  : undefined;

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/v1': { target: consoleProxyTarget, changeOrigin: false, headers: proxyHeaders },
      '/key': { target: consoleProxyTarget, changeOrigin: false, headers: proxyHeaders },
      '/actuator': { target: consoleProxyTarget, changeOrigin: false },
      '/__gateway': {
        target: gatewayProxyTarget,
        changeOrigin: false,
        rewrite: (path) => path.replace(/^\/__gateway/, ''),
      },
    },
  },
});
