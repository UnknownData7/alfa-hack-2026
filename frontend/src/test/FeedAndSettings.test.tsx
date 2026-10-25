import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { Activity, FeedCard } from '../api/types';
import { FeedList } from '../components/FeedList';
import { Money } from '../components/ui';
import { groupByDay } from '../pages/client/HistoryPage';
import { SettingsProvider, useSettings } from '../settings/SettingsContext';

const card = (id: string, type: FeedCard['type']): FeedCard => ({
  id,
  type,
  title: `Карточка ${id}`,
  body: 'Текст',
  reason: 'Почему показано',
  createdAt: '2026-10-23T09:05:00Z',
  read: false,
});

describe('FeedList', () => {
  it('показывает дату и время у каждой карточки и убирает по «Прочитано»', () => {
    const onDismiss = vi.fn();
    render(
      <MemoryRouter>
        <FeedList cards={[card('event-1', 'STATUS'), card('news-2', 'NEWS')]} onDismiss={onDismiss} />
      </MemoryRouter>,
    );
    expect(screen.getAllByText(/23 окт\. 2026/)).toHaveLength(2);
    const buttons = screen.getAllByRole('button', { name: 'Прочитано' });
    expect(buttons).toHaveLength(2);
    fireEvent.click(buttons[1]);
    expect(onDismiss).toHaveBeenCalledWith('news-2');
  });

  it('пустая лента объясняет, что здесь появится', () => {
    render(<FeedList cards={[]} onDismiss={() => {}} />);
    expect(screen.getByText(/Новых сообщений нет/)).toBeInTheDocument();
  });
});

function HideToggle() {
  const { setHideBalance } = useSettings();
  return (
    <button type="button" onClick={() => setHideBalance(true)}>
      скрыть
    </button>
  );
}

describe('Скрытие остатков', () => {
  it('прячет только суммы с пометкой sensitive', () => {
    render(
      <SettingsProvider>
        <Money value={3842150} sensitive />
        <Money value={1200000} />
        <HideToggle />
      </SettingsProvider>,
    );
    expect(screen.getByText(/3\s842\s150/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'скрыть' }));
    expect(screen.queryByText(/3\s842\s150/)).not.toBeInTheDocument();
    expect(screen.getByText('••• ₽')).toBeInTheDocument();
    expect(screen.getByText(/1\s200\s000/)).toBeInTheDocument();
  });

  it('тёмная тема ставится атрибутом на документ', () => {
    function Dark() {
      const { setTheme } = useSettings();
      return (
        <button type="button" onClick={() => setTheme('dark')}>
          тёмная
        </button>
      );
    }
    render(
      <SettingsProvider>
        <Dark />
      </SettingsProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'тёмная' }));
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});

describe('История операций', () => {
  it('группирует операции по дням, сохраняя порядок', () => {
    const op = (createdAt: string): Activity => ({
      type: 'STATUS',
      title: 't',
      description: 'd',
      applicationId: 1,
      applicationNumber: 'A-1',
      createdAt,
    });
    const groups = groupByDay([op('2026-10-20T15:00:00'), op('2026-10-20T09:00:00'), op('2026-10-18T12:00:00')]);
    expect(groups).toHaveLength(2);
    expect(groups[0].items).toHaveLength(2);
    expect(groups[1].day).toBe('18 октября 2026 г.');
  });
});
