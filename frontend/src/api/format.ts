const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 });
const number = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });
const dateTime = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' });
const date = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium' });

export const formatMoney = (value?: number | null) => (value == null ? '—' : money.format(value));
export const formatNumber = (value?: number | null) => (value == null ? '—' : number.format(value));
export const formatPct = (value?: number | null) => (value == null ? '—' : `${number.format(value)}%`);
export const formatDateTime = (value?: string) => (value ? dateTime.format(new Date(value)) : '—');
export const formatDate = (value?: string) => (value ? date.format(new Date(value)) : '—');

export function formatMonths(months: number) {
  const mod10 = months % 10;
  const mod100 = months % 100;
  if (mod10 === 1 && mod100 !== 11) return `${months} месяц`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${months} месяца`;
  return `${months} месяцев`;
}

export function formatAge(months: number) {
  const years = Math.floor(months / 12);
  const rest = months % 12;
  if (years === 0) return formatMonths(rest);
  const y = years % 10 === 1 && years % 100 !== 11 ? 'год' : years % 10 >= 2 && years % 10 <= 4 && (years % 100 < 12 || years % 100 > 14) ? 'года' : 'лет';
  return rest === 0 ? `${years} ${y}` : `${years} ${y} ${formatMonths(rest)}`;
}

export function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} Б`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} КБ`;
  return `${(bytes / 1024 / 1024).toFixed(1)} МБ`;
}

export const riskLabel: Record<string, string> = { LOW: 'низкий', MEDIUM: 'средний', HIGH: 'высокий' };
export const docTypeLabel: Record<string, string> = {
  BANK_STATEMENT: 'Выписка по счёту',
  FINANCIAL_REPORT: 'Финансовая отчётность',
  COLLATERAL: 'Документы по залогу',
  OTHER: 'Другой документ',
};

export const statusLabel: Record<string, string> = {
  DRAFT: 'Черновик',
  SUBMITTED: 'Подана',
  NEED_INFO: 'Нужны данные',
  SCORING: 'Скоринг',
  MANUAL_REVIEW: 'Ручная проверка',
  OFFER_MADE: 'Предложение условий',
  SIGNING: 'Подписание',
  ISSUED: 'Выдан',
  DECLINED: 'Отказ',
  CLOSED: 'Закрыта',
};
