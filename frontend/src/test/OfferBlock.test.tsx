import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { ApplicationDetail } from '../api/types';
import { OfferBlock, SigningBlock } from '../pages/client/ApplicationPage';
import { fireEvent } from '@testing-library/react';
import { vi } from 'vitest';

function application(status: ApplicationDetail['status']): ApplicationDetail {
  return {
    id: 1,
    number: 'A-2026-001001',
    status,
    statusLabel: 'Предложение условий',
    finalStatus: false,
    productCode: 'WORKING_EXPRESS',
    productName: 'Оборотный экспресс',
    requiresCollateral: false,
    purpose: 'WORKING_CAPITAL',
    purposeLabel: 'Пополнение оборотных средств',
    requestedAmount: 1400000,
    requestedTermMonths: 6,
    consentDataProcessing: true,
    consentCreditBureau: true,
    createdAt: '2026-10-23T10:00:00Z',
    updatedAt: '2026-10-23T10:01:00Z',
    nextStep: { title: 'Посмотрите предложение', text: '', action: 'REVIEW_OFFER' },
    offer: {
      id: 5,
      kind: 'COUNTER',
      amount: 850000,
      termMonths: 6,
      ratePct: 20.5,
      monthlyPayment: 148489,
      totalPayment: 890934,
      overpayment: 50934,
      status: 'ACTIVE',
      summary: 'Банк готов выдать кредит на других условиях',
      changes: ['Сумма: 850 000 ₽ вместо 1 400 000 ₽'],
      createdAt: '2026-10-23T10:01:00Z',
    },
    history: [],
    infoRequests: [],
    documents: [],
  };
}

describe('OfferBlock', () => {
  it('показывает встречное предложение с изменениями и кнопками', () => {
    render(<OfferBlock application={application('OFFER_MADE')} onDone={() => {}} />);
    expect(screen.getByText('Встречное предложение банка')).toBeInTheDocument();
    expect(screen.getByText('Сумма: 850 000 ₽ вместо 1 400 000 ₽')).toBeInTheDocument();
    // Клиент не видит внутреннюю оценку банка
    expect(screen.queryByText(/грейд|балл|скоринг/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Принять и перейти к подписанию' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Отклонить' })).toBeEnabled();
  });

  it('подписание по кнопке доступно только после согласия с условиями', () => {
    const onDone = vi.fn();
    render(<SigningBlock application={application('SIGNING')} onDone={onDone} />);
    const button = screen.getByRole('button', { name: 'Подписать договор и получить деньги' });
    expect(button).toBeDisabled();
    expect(screen.queryByText(/код|SMS/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox'));
    expect(button).toBeEnabled();
  });

  it('не даёт действий после принятия предложения', () => {
    render(<OfferBlock application={application('SIGNING')} onDone={() => {}} />);
    expect(screen.queryByRole('button', { name: 'Отклонить' })).not.toBeInTheDocument();
  });
});
