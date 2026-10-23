// Типы ответов API. Повторяют DTO backend (пакеты application, task, product, company, feed).

export type Role = 'CLIENT' | 'UNDERWRITER';

export type ApplicationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'NEED_INFO'
  | 'SCORING'
  | 'MANUAL_REVIEW'
  | 'OFFER_MADE'
  | 'SIGNING'
  | 'ISSUED'
  | 'DECLINED'
  | 'CLOSED';

export interface User {
  id: number;
  login: string;
  fullName: string;
  role: Role;
  companyId?: number;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface CompanyProfile {
  id: number;
  inn: string;
  name: string;
  legalForm: 'IP' | 'OOO';
  legalFormLabel: string;
  ogrn: string;
  okvedCode: string;
  okvedName: string;
  industryRisk: 'LOW' | 'MEDIUM' | 'HIGH';
  registrationDate: string;
  businessAgeMonths: number;
  employeesCount: number;
  taxSystem: string;
  accountMasked: string;
  monthsWithBank: number;
  annualTurnover: number;
  avgMonthlyTurnover: number;
  turnoverGrowthPct: number;
  existingMonthlyObligations: number;
  financialsAsOf: string;
}

export interface PurposeOption {
  code: string;
  label: string;
}

export interface Product {
  code: string;
  name: string;
  description: string;
  purposes: PurposeOption[];
  minAmount: number;
  maxAmount: number;
  minTermMonths: number;
  maxTermMonths: number;
  requiresCollateral: boolean;
  alwaysManual: boolean;
  minAvgMonthlyTurnover?: number;
  indicativeRateFromPct: number;
}

export interface Recommendation {
  code: string;
  name: string;
  available: boolean;
  relevance: number;
  fits: string[];
  limits: string[];
  indicativeRatePct: number;
}

export interface NextStep {
  title: string;
  text: string;
  action: 'CONTINUE_DRAFT' | 'WAIT' | 'ANSWER_REQUEST' | 'REVIEW_OFFER' | 'SIGN' | 'NONE' | 'NEW_APPLICATION';
}

export interface ApplicationSummary {
  id: number;
  number: string;
  status: ApplicationStatus;
  statusLabel: string;
  productCode: string;
  productName: string;
  requestedAmount: number;
  requestedTermMonths: number;
  createdAt: string;
  updatedAt: string;
  nextStep: NextStep;
}

export interface HistoryEntry {
  fromStatus?: ApplicationStatus;
  toStatus: ApplicationStatus;
  toStatusLabel: string;
  actorType: 'CLIENT' | 'BANK' | 'SYSTEM';
  actorLabel: string;
  comment?: string;
  createdAt: string;
}

export interface InfoRequest {
  id: number;
  source: 'SYSTEM' | 'BANK';
  text: string;
  status: 'OPEN' | 'ANSWERED';
  clientAnswer?: string;
  createdAt: string;
  answeredAt?: string;
}

export interface DocumentItem {
  id: number;
  docType: string;
  fileName: string;
  sizeBytes: number;
  uploadedByRole: string;
  uploadedAt: string;
  newForBank: boolean;
}

export interface Offer {
  id: number;
  kind: 'INITIAL' | 'COUNTER';
  amount: number;
  termMonths: number;
  ratePct: number;
  monthlyPayment: number;
  totalPayment: number;
  overpayment: number;
  status: string;
  summary: string;
  reasons: string[];
  changes: string[];
  createdAt: string;
}

export interface Factor {
  code: string;
  title: string;
  value: string;
  points: number;
  comment: string;
}

export interface Decision {
  summary: string;
  reasonCode?: string;
  grade?: string;
  score?: number;
  hardStopReason?: string;
  factors: Factor[];
}

export interface Signing {
  codeRequested: boolean;
  attemptsLeft: number;
  blocked: boolean;
  confirmed: boolean;
}

export interface Disbursement {
  amount: number;
  accountMasked: string;
  executedAt: string;
  simulated: boolean;
}

export interface ApplicationDetail {
  id: number;
  number: string;
  status: ApplicationStatus;
  statusLabel: string;
  finalStatus: boolean;
  productCode: string;
  productName: string;
  requiresCollateral: boolean;
  purpose: string;
  purposeLabel: string;
  requestedAmount: number;
  requestedTermMonths: number;
  monthlyObligationsDeclared?: number;
  collateralDescription?: string;
  consentDataProcessing: boolean;
  consentCreditBureau: boolean;
  parentApplicationId?: number;
  createdAt: string;
  updatedAt: string;
  submittedAt?: string;
  nextStep: NextStep;
  decision?: Decision;
  offer?: Offer;
  history: HistoryEntry[];
  infoRequests: InfoRequest[];
  documents: DocumentItem[];
  signing?: Signing;
  disbursement?: Disbursement;
}

export interface DraftRequest {
  productCode: string;
  purpose: string;
  amount: number;
  termMonths: number;
  monthlyObligationsDeclared?: number | null;
  collateralDescription?: string | null;
  consentDataProcessing?: boolean;
  consentCreditBureau?: boolean;
  parentApplicationId?: number | null;
}

export interface FeedCard {
  id: string;
  type: 'NEWS' | 'TIP' | 'STATUS';
  title: string;
  body: string;
  reason: string;
  relatedApplicationId?: number;
  createdAt: string;
  read: boolean;
}

export interface SendCodeResponse {
  attemptsLeft: number;
  demoCode?: string;
  hint: string;
}

// ---------- Банк ----------

export type TaskStatus = 'NEW' | 'IN_PROGRESS' | 'POSTPONED' | 'DONE';

export interface TaskListItem {
  id: number;
  applicationId: number;
  applicationNumber: string;
  companyName: string;
  legalForm: string;
  productCode: string;
  productName: string;
  amount: number;
  termMonths: number;
  priority: number;
  status: TaskStatus;
  statusLabel: string;
  assigneeId?: number;
  assigneeName?: string;
  mine: boolean;
  slaDueAt: string;
  overdue: boolean;
  createdAt: string;
  repeat: boolean;
  reasonCodes: string;
  grade?: string;
  applicationStatus: ApplicationStatus;
}

export type CheckStatus = 'PENDING' | 'OK' | 'NEGATIVE' | 'NEEDS_INFO';

export interface CheckView {
  code: string;
  title: string;
  goal: string;
  status: CheckStatus;
  comment?: string;
  updatedAt: string;
}

export interface ScoringRun {
  runNo: number;
  totalScore: number;
  grade: string;
  hardStop: boolean;
  hardStopReason?: string;
  paymentLoad: number;
  trigger: 'INITIAL' | 'RECALC';
  createdBy: string;
  createdAt: string;
  factors: Factor[];
  inputs: Record<string, unknown>;
}

export interface BankDecisionView {
  result: string;
  resultLabel: string;
  comment?: string;
  decidedByName?: string;
  createdAt: string;
  counterOffer?: { amount: number; termMonths: number };
}

export interface ExternalCheck {
  source: string;
  sourceLabel: string;
  status: 'OK' | 'UNAVAILABLE';
  payload: Record<string, unknown>;
  receivedAt: string;
}

export interface BankApplicationInfo {
  id: number;
  number: string;
  status: ApplicationStatus;
  statusLabel: string;
  productCode: string;
  productName: string;
  requiresCollateral: boolean;
  productMinAmount: number;
  productMaxAmount: number;
  productMinTerm: number;
  productMaxTerm: number;
  purposeLabel: string;
  requestedAmount: number;
  requestedTermMonths: number;
  monthlyObligationsDeclared?: number;
  collateralDescription?: string;
  submittedAt?: string;
  decisionSummary?: string;
}

export type DecisionResult = 'APPROVE' | 'APPROVE_WITH_CHANGES' | 'REQUEST_INFO' | 'DECLINE' | 'POSTPONE';

export interface TaskCard {
  task: TaskListItem;
  application: BankApplicationInfo;
  company: CompanyProfile;
  documents: DocumentItem[];
  scoringRuns: ScoringRun[];
  checks: CheckView[];
  decisions: BankDecisionView[];
  history: HistoryEntry[];
  infoRequests: InfoRequest[];
  externalChecks: ExternalCheck[];
  currentOffer?: Offer;
  canAct: boolean;
  allowedResults: DecisionResult[];
}

export interface IndicativeTerms {
  outcome: string;
  outcomeLabel: string;
  summary: string;
  amount?: number;
  termMonths?: number;
  ratePct?: number;
  monthlyPayment?: number;
}

export interface RecalculateResponse {
  run: ScoringRun;
  indicative: IndicativeTerms;
}
