import { useEffect, useRef } from 'react';

/** Признаки затруднения клиента. Собираются только в браузере, на сервер уходит лишь название сигнала. */
export type Signal =
  | 'IDLE_NEED'
  | 'PURPOSE_CHANGES'
  | 'TERM_DOUBT'
  | 'COMPARE_DOUBT'
  | 'WIZARD_IDLE'
  | 'VALIDATION_REPEAT'
  | 'LEAVE_DRAFT';

/** Текст пузыря зависит от сигнала, тема открывает помощника сразу на нужном вопросе. */
export const OFFERS: Record<Signal, { text: string; topic: string }> = {
  IDLE_NEED: { text: 'Не знаете, с чего начать? Помогу подобрать кредит под вашу задачу', topic: 'purpose' },
  PURPOSE_CHANGES: { text: 'Не уверены, какую цель выбрать? Подскажу по вашей задаче', topic: 'purpose' },
  TERM_DOUBT: { text: 'Не знаете, какой срок выбрать? Покажу платёж при разных сроках', topic: 'term' },
  COMPARE_DOUBT: { text: 'Подсказать, чем отличаются продукты?', topic: 'compare' },
  WIZARD_IDLE: { text: 'Что-то непонятно на этом шаге? Подскажу', topic: 'wizard' },
  VALIDATION_REPEAT: { text: 'Не получается заполнить условия? Подскажу, что подходит под продукт', topic: 'wizard' },
  LEAVE_DRAFT: { text: 'Черновик сохранится сам. Подсказать, что осталось заполнить?', topic: 'wizard' },
};

/** Сколько предложений показано за сессию и на каких страницах. После показа или «Не сейчас» страница закрыта. */
export interface OfferLog {
  total: number;
  pages: Record<string, 'shown' | 'dismissed'>;
}

export interface OfferContext {
  enabled: boolean;
  panelOpen: boolean;
  bubbleShown: boolean;
  tabVisible: boolean;
  maxPerSession: number;
}

export type OfferDecision = 'show' | 'later' | 'never';

/**
 * Правила показа: не больше одного предложения на страницу и maxPerSession за сессию, после «Не сейчас» на странице
 * больше не предлагать, не показывать при открытой панели и неактивной вкладке. Пока клиент печатает, показ
 * откладывается.
 */
export function decideOffer(log: OfferLog, path: string, ctx: OfferContext, typing: boolean): OfferDecision {
  if (!ctx.enabled || ctx.panelOpen || ctx.bubbleShown) return 'never';
  if (log.pages[path] || log.total >= ctx.maxPerSession) return 'never';
  if (!ctx.tabVisible || typing) return 'later';
  return 'show';
}

/** Фокус в поле ввода: клиент печатает, отвлекать нельзя. */
export function isTyping(): boolean {
  const el = document.activeElement as HTMLElement | null;
  if (!el) return false;
  return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable;
}

const ACTIVITY = ['keydown', 'pointerdown', 'wheel', 'touchstart', 'input', 'scroll'] as const;

/**
 * Таймер, который идёт только при активной вкладке. С resetOnActivity сбрасывается от ввода и прокрутки (бездействие),
 * без него считает время с момента включения. resetKey сбрасывает таймер при смене шага или данных формы.
 */
export function useVisibleTimer(
  seconds: number | undefined,
  active: boolean,
  onFire: () => void,
  options: { resetOnActivity?: boolean; resetKey?: unknown } = {},
) {
  const elapsed = useRef(0);
  const fired = useRef(false);
  const callback = useRef(onFire);
  callback.current = onFire;
  const { resetOnActivity = false, resetKey } = options;

  useEffect(() => {
    elapsed.current = 0;
    fired.current = false;
  }, [resetKey, active]);

  useEffect(() => {
    if (!active || !seconds) return;
    const reset = () => {
      elapsed.current = 0;
    };
    if (resetOnActivity) ACTIVITY.forEach((e) => window.addEventListener(e, reset, { passive: true, capture: true }));
    const timer = window.setInterval(() => {
      if (document.visibilityState !== 'visible' || fired.current) return;
      elapsed.current += 1;
      if (elapsed.current >= seconds) {
        fired.current = true;
        callback.current();
      }
    }, 1000);
    return () => {
      window.clearInterval(timer);
      if (resetOnActivity) ACTIVITY.forEach((e) => window.removeEventListener(e, reset, { capture: true }));
    };
  }, [active, seconds, resetOnActivity]);
}
