const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short',
  timeStyle: 'medium',
});

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return dateTimeFormatter.format(date);
}

export function formatNumber(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—';
  }
  return new Intl.NumberFormat('zh-CN').format(value);
}

export function formatAmount(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—';
  }
  return value.toFixed(2);
}
