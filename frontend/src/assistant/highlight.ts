import type { PageKind } from './types';

/** Тип страницы по адресу. Подробности (шаг мастера, статус заявки) страницы сообщают сами. */
export function pageKindFor(path: string): PageKind {
  if (path === '/') return 'HOME';
  if (path.startsWith('/need')) return 'NEED';
  if (path === '/applications/new' || /^\/applications\/\d+\/edit$/.test(path)) return 'WIZARD';
  if (/^\/applications\/\d+$/.test(path)) return 'APPLICATION';
  if (path.startsWith('/history')) return 'HISTORY';
  if (path.startsWith('/benefits')) return 'BENEFITS';
  return 'OTHER';
}

/**
 * Подсветка блока страницы с атрибутом data-assist и прокрутка к нему. Блок может появиться не сразу (после
 * перехода или загрузки данных), поэтому поиск повторяется несколько раз.
 */
export function highlight(target: string, attempts = 15) {
  const el = document.querySelector<HTMLElement>(`[data-assist="${target}"]`);
  if (!el) {
    if (attempts > 0) window.setTimeout(() => highlight(target, attempts - 1), 200);
    return;
  }
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  el.scrollIntoView?.({ behavior: reduced ? 'auto' : 'smooth', block: 'center' });
  el.classList.remove('assist-highlight');
  // Перезапуск анимации, если блок уже подсвечивался
  void el.offsetWidth;
  el.classList.add('assist-highlight');
  window.setTimeout(() => el.classList.remove('assist-highlight'), 2600);
}

export function isNarrowScreen() {
  return window.matchMedia?.('(max-width: 640px)').matches ?? false;
}
