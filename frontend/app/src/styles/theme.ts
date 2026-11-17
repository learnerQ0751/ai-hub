import type { ThemeConfig } from 'antd';

/**
 * D1「冷静工程台 · 纯净版」设计 token（2026-11-17 主人确认）。
 * 视觉稿在 L2 再细化；这里只固定已确认的基调：
 * 3px 圆角、无阴影、细灰分割线、36px 量级行高、等宽 ID、克制的单一强调色。
 * 强调色暂用电蓝，L2 可整体替换此一处。
 */
export const D1_COLORS = {
  ink: '#17181c',
  textSecondary: '#6b7280',
  textMuted: '#9ca3af',
  border: '#e5e7eb',
  borderSoft: '#eef0f2',
  pageBg: '#f7f8fa',
  surface: '#ffffff',
  accent: '#2457e6',
  success: '#16a34a',
  warning: '#d97706',
  danger: '#dc2626',
} as const;

export const d1Theme: ThemeConfig = {
  token: {
    colorPrimary: D1_COLORS.accent,
    colorInfo: D1_COLORS.accent,
    colorSuccess: D1_COLORS.success,
    colorWarning: D1_COLORS.warning,
    colorError: D1_COLORS.danger,
    colorText: D1_COLORS.ink,
    colorTextSecondary: D1_COLORS.textSecondary,
    colorTextTertiary: D1_COLORS.textMuted,
    colorBorder: D1_COLORS.border,
    colorBorderSecondary: D1_COLORS.borderSoft,
    colorBgLayout: D1_COLORS.pageBg,
    colorBgContainer: D1_COLORS.surface,
    borderRadius: 3,
    boxShadow: 'none',
    boxShadowSecondary: 'none',
    boxShadowTertiary: 'none',
    fontFamily:
      "'Inter','Noto Sans SC','Source Han Sans SC','PingFang SC','Microsoft YaHei',sans-serif",
    fontSize: 14,
    controlHeight: 32,
  },
  components: {
    Layout: {
      headerBg: D1_COLORS.surface,
      siderBg: D1_COLORS.surface,
      bodyBg: D1_COLORS.pageBg,
      headerHeight: 48,
      headerPadding: '0 16px',
    },
    Menu: {
      itemBorderRadius: 3,
      itemHeight: 34,
      itemMarginInline: 6,
      itemMarginBlock: 2,
    },
    Table: {
      headerBg: '#fafbfc',
      headerSplitColor: 'transparent',
      borderColor: D1_COLORS.borderSoft,
      cellPaddingBlock: 8,
      cellPaddingInline: 12,
      rowHoverBg: D1_COLORS.pageBg,
    },
    Card: {
      borderRadiusLG: 3,
    },
    Button: {
      borderRadius: 3,
      primaryShadow: 'none',
      defaultShadow: 'none',
      dangerShadow: 'none',
    },
    Input: { borderRadius: 3 },
    Select: { borderRadius: 3 },
    Drawer: { paddingLG: 20 },
    Tag: { borderRadiusSM: 3 },
  },
};
