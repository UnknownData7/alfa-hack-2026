import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { decideOffer, useVisibleTimer, type OfferContext, type OfferLog } from '../assistant/proactive';

const ctx: OfferContext = { enabled: true, panelOpen: false, bubbleShown: false, tabVisible: true, maxPerSession: 3 };
const empty: OfferLog = { total: 0, pages: {} };

describe('Правила показа предложения помощи', () => {
  it('показывает на новой странице, пока не исчерпан лимит сессии', () => {
    expect(decideOffer(empty, '/need', ctx, false)).toBe('show');
    expect(decideOffer({ total: 3, pages: {} }, '/need', ctx, false)).toBe('never');
  });

  it('не больше одного раза на страницу и не после «Не сейчас»', () => {
    expect(decideOffer({ total: 1, pages: { '/need': 'shown' } }, '/need', ctx, false)).toBe('never');
    expect(decideOffer({ total: 1, pages: { '/need': 'dismissed' } }, '/need', ctx, false)).toBe('never');
    expect(decideOffer({ total: 1, pages: { '/need': 'dismissed' } }, '/applications/new', ctx, false)).toBe('show');
  });

  it('откладывает, пока клиент печатает или вкладка неактивна', () => {
    expect(decideOffer(empty, '/need', ctx, true)).toBe('later');
    expect(decideOffer(empty, '/need', { ...ctx, tabVisible: false }, false)).toBe('later');
  });

  it('молчит, если помощь выключена в настройках или панель уже открыта', () => {
    expect(decideOffer(empty, '/need', { ...ctx, enabled: false }, false)).toBe('never');
    expect(decideOffer(empty, '/need', { ...ctx, panelOpen: true }, false)).toBe('never');
  });
});

function Timer({ onFire, reset }: { onFire: () => void; reset?: boolean }) {
  useVisibleTimer(3, true, onFire, { resetOnActivity: reset });
  return null;
}

describe('Таймер бездействия', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('срабатывает один раз после порога', () => {
    const onFire = vi.fn();
    render(<Timer onFire={onFire} />);
    act(() => vi.advanceTimersByTime(2000));
    expect(onFire).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(5000));
    expect(onFire).toHaveBeenCalledTimes(1);
  });

  it('сбрасывается от ввода', () => {
    const onFire = vi.fn();
    render(<Timer onFire={onFire} reset />);
    act(() => vi.advanceTimersByTime(2000));
    act(() => {
      window.dispatchEvent(new KeyboardEvent('keydown'));
    });
    act(() => vi.advanceTimersByTime(2000));
    expect(onFire).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1000));
    expect(onFire).toHaveBeenCalledTimes(1);
  });

  it('стоит, пока вкладка неактивна', () => {
    const onFire = vi.fn();
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    render(<Timer onFire={onFire} />);
    act(() => vi.advanceTimersByTime(10_000));
    expect(onFire).not.toHaveBeenCalled();
    visibility.mockReturnValue('visible');
    act(() => vi.advanceTimersByTime(3000));
    expect(onFire).toHaveBeenCalledTimes(1);
    visibility.mockRestore();
  });
});
