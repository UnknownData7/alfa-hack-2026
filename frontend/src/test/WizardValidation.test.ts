import { describe, expect, it } from 'vitest';
import type { Product } from '../api/types';
import { validateConditions } from '../pages/client/WizardPage';

const product: Product = {
  code: 'WORKING_EXPRESS',
  name: 'Оборотный экспресс',
  description: '',
  purposes: [{ code: 'WORKING_CAPITAL', label: 'Пополнение оборотных средств' }],
  minAmount: 100000,
  maxAmount: 3000000,
  minTermMonths: 3,
  maxTermMonths: 12,
  requiresCollateral: false,
  alwaysManual: false,
  indicativeRateFromPct: 19,
};

const form = (amount: string, termMonths: string, purpose = 'WORKING_CAPITAL') => ({
  productCode: 'WORKING_EXPRESS',
  purpose,
  amount,
  termMonths,
  monthlyObligationsDeclared: '',
  collateralDescription: '',
  consentDataProcessing: false,
  consentCreditBureau: false,
});

describe('проверка условий заявки', () => {
  it('пропускает условия в границах продукта', () => {
    expect(validateConditions(form('1200000', '9'), product)).toBeNull();
  });

  it('сообщает о сумме вне границ', () => {
    expect(validateConditions(form('5000000', '9'), product)).toContain('Сумма');
  });

  it('сообщает о сроке вне границ', () => {
    expect(validateConditions(form('1200000', '24'), product)).toContain('Срок');
  });

  it('сообщает о неподходящей цели', () => {
    expect(validateConditions(form('1200000', '9', 'EQUIPMENT'), product)).toContain('цели');
  });
});
