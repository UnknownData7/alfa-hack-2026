import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { HistoryEntry } from '../api/types';
import { StatusTracker } from '../components/StatusTracker';

const entry = (toStatus: HistoryEntry['toStatus']): HistoryEntry => ({
  toStatus,
  toStatusLabel: toStatus,
  actorType: 'SYSTEM',
  actorLabel: 'Система',
  createdAt: '2026-10-23T10:00:00Z',
});

describe('StatusTracker', () => {
  it('подсвечивает текущий шаг и скрывает ручную проверку, если её не было', () => {
    render(<StatusTracker status="OFFER_MADE" history={[entry('DRAFT'), entry('SUBMITTED'), entry('SCORING'), entry('OFFER_MADE')]} />);
    expect(screen.getByText('Предложение').closest('li')).toHaveAttribute('aria-current', 'step');
    expect(screen.queryByText('Ручная проверка')).not.toBeInTheDocument();
    expect(screen.getByText('Скоринг').closest('li')).toHaveClass('done');
  });

  it('показывает ручную проверку, если заявка через неё проходила', () => {
    render(<StatusTracker status="MANUAL_REVIEW" history={[entry('DRAFT'), entry('SUBMITTED'), entry('SCORING')]} />);
    expect(screen.getByText('Ручная проверка').closest('li')).toHaveAttribute('aria-current', 'step');
  });

  it('заменяет финал на отказ', () => {
    render(<StatusTracker status="DECLINED" history={[entry('DRAFT'), entry('SUBMITTED'), entry('SCORING')]} />);
    expect(screen.getByText('Отказ').closest('li')).toHaveAttribute('aria-current', 'step');
    expect(screen.queryByText('Выдан')).not.toBeInTheDocument();
  });

  it('отмечает, что нужны данные', () => {
    render(<StatusTracker status="NEED_INFO" history={[entry('DRAFT'), entry('SUBMITTED')]} />);
    expect(screen.getByText('нужны данные')).toBeInTheDocument();
  });
});
