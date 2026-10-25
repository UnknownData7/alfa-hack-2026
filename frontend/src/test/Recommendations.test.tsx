import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Recommendation } from '../api/types';
import { Recommendations } from '../pages/client/NeedPage';

const rec = (code: string, available: boolean, fits: string[], limits: string[]): Recommendation => ({
  code,
  name: code,
  available,
  relevance: 1,
  fits,
  limits,
  indicativeRatePct: 18,
});

describe('Recommendations', () => {
  it('показывает только подходящие продукты и выделяет лучший', () => {
    render(
      <Recommendations
        items={[
          rec('Экспресс', true, ['подходит для цели «x»'], []),
          rec('Овердрафт', true, ['подходит для цели «x»'], []),
          rec('Оборудование', false, [], ['продукт не предназначен для цели «x»']),
        ]}
        amount="1200000"
        onApply={() => {}}
      />,
    );
    expect(screen.getByText('Экспресс')).toBeInTheDocument();
    expect(screen.queryByText('Оборудование')).not.toBeInTheDocument();
    expect(screen.queryByText(/Не подходит/)).not.toBeInTheDocument();
    expect(screen.getByText('Лучше всего подходит')).toBeInTheDocument();
  });

  it('если ничего не подходит, подсказывает, что изменить', () => {
    render(
      <Recommendations
        items={[rec('Овердрафт', false, ['подходит для цели «x»'], ['лимит не больше 50% среднего месячного оборота, для вас до 300 000 ₽'])]}
        amount="500000"
        onApply={() => {}}
      />,
    );
    expect(screen.getByText('Под такие условия продуктов нет')).toBeInTheDocument();
    expect(screen.getByText(/для вас до 300 000/)).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
