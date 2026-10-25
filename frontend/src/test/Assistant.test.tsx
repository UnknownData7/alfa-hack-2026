import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { Recommendation } from '../api/types';
import { pageKindFor } from '../assistant/highlight';
import { Message } from '../assistant/AssistantWidget';
import type { ChatMessage } from '../assistant/types';
import { Recommendations } from '../pages/client/NeedPage';

const reply: ChatMessage = {
  id: 1,
  from: 'assistant',
  text: 'Для такой задачи подходит «Оборотный экспресс», ставка от 18% годовых',
  terms: [
    { termMonths: 3, monthlyPayment: 137_340, overpayment: 12_020, recommended: false },
    { termMonths: 6, monthlyPayment: 70_210, overpayment: 21_260, recommended: true },
  ],
  buttons: [{ label: 'Подставить 400 000 ₽ на 6 месяцев', action: 'FILL_FORM', params: { term: '6' } }],
};

describe('Сообщение помощника', () => {
  it('показывает платёж и переплату по срокам и кнопки последнего ответа', () => {
    const onPress = vi.fn();
    render(<Message message={reply} active onPress={onPress} />);
    expect(screen.getByText('6 месяцев').closest('tr')).toHaveClass('recommended');
    expect(screen.getByText(/Ориентировочно по ставке «от»/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Подставить 400 000 ₽ на 6 месяцев' }));
    expect(onPress).toHaveBeenCalledWith(reply.buttons![0]);
  });

  it('у прошлых ответов кнопок нет, чтобы не нажать устаревший вариант', () => {
    render(<Message message={reply} active={false} onPress={() => {}} />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('Тип страницы для помощника', () => {
  it('определяется по адресу', () => {
    expect(pageKindFor('/')).toBe('HOME');
    expect(pageKindFor('/need')).toBe('NEED');
    expect(pageKindFor('/applications/new')).toBe('WIZARD');
    expect(pageKindFor('/applications/12/edit')).toBe('WIZARD');
    expect(pageKindFor('/applications/12')).toBe('APPLICATION');
    expect(pageKindFor('/history')).toBe('HISTORY');
  });
});

describe('Встроенный вход «Чем они отличаются?»', () => {
  const rec = (code: string): Recommendation => ({
    code,
    name: code,
    available: true,
    relevance: 6,
    fits: ['подходит для цели «x»'],
    limits: [],
    indicativeRatePct: 18,
  });

  it('появляется над карточками, когда подходит больше одного продукта', () => {
    const onCompare = vi.fn();
    render(<Recommendations items={[rec('Экспресс'), rec('Овердрафт')]} amount="400000" onApply={() => {}} onCompare={onCompare} />);
    fireEvent.click(screen.getByRole('button', { name: 'Чем они отличаются?' }));
    expect(onCompare).toHaveBeenCalled();
  });

  it('не показывается для одного продукта', () => {
    render(<Recommendations items={[rec('Экспресс')]} amount="400000" onApply={() => {}} onCompare={() => {}} />);
    expect(screen.queryByRole('button', { name: 'Чем они отличаются?' })).not.toBeInTheDocument();
  });
});
