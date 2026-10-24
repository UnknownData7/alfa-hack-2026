// Контракт API помощника, см. backend AssistantDtos

export type DialogState =
  | 'GREETING'
  | 'PURPOSE'
  | 'PURPOSE_DETAIL'
  | 'AMOUNT'
  | 'TERM'
  | 'SUMMARY'
  | 'PRODUCTS'
  | 'COMPARE'
  | 'WIZARD_HELP'
  | 'APPLICATION_HELP';

export type PageKind = 'HOME' | 'NEED' | 'WIZARD' | 'APPLICATION' | 'HISTORY' | 'BENEFITS' | 'OTHER';

export interface Slots {
  purpose?: string | null;
  amount?: number | null;
  termMonths?: number | null;
  termHint?: number | null;
  productCode?: string | null;
}

/** Где клиент и что введено в форму на странице. */
export interface PageContext {
  page: PageKind;
  wizardStep?: number;
  applicationStatus?: string;
  offerKind?: string;
  purpose?: string;
  amount?: number;
  termMonths?: number;
  productCode?: string;
}

export interface AssistantButton {
  label: string;
  action: string;
  params: Record<string, string>;
}

export interface UiAction {
  type: 'FILL_NEED_FORM' | 'OPEN_WIZARD' | 'NAVIGATE' | 'HIGHLIGHT';
  params: Record<string, string>;
}

export interface TermOption {
  termMonths: number;
  monthlyPayment: number;
  overpayment: number;
  recommended: boolean;
}

export interface AssistantReply {
  state: DialogState;
  slots: Slots;
  text: string;
  terms: TermOption[];
  buttons: AssistantButton[];
  ui: UiAction[];
  sources: string[];
}

export interface AssistantConfig {
  enabled: boolean;
  proactive: boolean;
  idleSeconds: number;
  purposeChanges: number;
  valueChanges: number;
  recommendationsSeconds: number;
  wizardIdleSeconds: number;
  validationRepeats: number;
  maxOffersPerSession: number;
}

export type AssistantEventType = 'OPENED' | 'OFFER_SHOWN' | 'OFFER_ACCEPTED' | 'OFFER_DISMISSED';

/** Сообщение в ленте разговора. У ответа помощника есть кнопки и таблица сроков. */
export interface ChatMessage {
  id: number;
  from: 'user' | 'assistant';
  text: string;
  terms?: TermOption[];
  buttons?: AssistantButton[];
}
