import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useSettings } from '../settings/SettingsContext';
import { highlight, isNarrowScreen, pageKindFor } from './highlight';
import { decideOffer, isTyping, OFFERS, type OfferLog, type Signal } from './proactive';
import type {
  AssistantButton,
  AssistantConfig,
  AssistantReply,
  ChatMessage,
  DialogState,
  PageContext,
  PageKind,
  Slots,
  UiAction,
} from './types';

export interface AssistantApi {
  enabled: boolean;
  config: AssistantConfig | undefined;
  open: boolean;
  messages: ChatMessage[];
  pending: boolean;
  error: string | null;
  /** Открыть панель. С темой помощник сразу отвечает на нужный вопрос; entry нужен для метрик. */
  openPanel: (topic?: string, entry?: string) => void;
  close: () => void;
  restart: () => void;
  send: (text: string) => void;
  press: (button: AssistantButton) => void;
  setPageDetails: (details: Omit<PageContext, 'page'> | null) => void;
  /** Предложение помощи, показанное пузырём над кнопкой. */
  bubble: { signal: Signal; text: string } | null;
  /** Страница сообщает о признаке затруднения; показывать ли пузырь, решают правила показа. */
  offer: (signal: Signal) => void;
  acceptOffer: () => void;
  dismissOffer: () => void;
}

const STORAGE_KEY = 'credit.assistant';

interface Stored {
  userId: number;
  messages: ChatMessage[];
  state?: DialogState;
  slots: Slots;
  lastKind?: PageKind;
  offers?: OfferLog;
}

function readStored(userId: number): Stored {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as Stored) : null;
    if (parsed && parsed.userId === userId) return parsed;
  } catch {
    // хранилище недоступно или повреждено: начинаем разговор заново
  }
  return { userId, messages: [], slots: {} };
}

function writeStored(value: Stored) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...value, messages: value.messages.slice(-60) }));
  } catch {
    // разговор живёт до перезагрузки страницы
  }
}

const AssistantContext = createContext<AssistantApi | null>(null);

/** Помощник клиента. Разговор хранится в браузере на время сессии и переживает переходы между страницами. */
export function AssistantProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const isClient = user?.role === 'CLIENT';
  const config = useQuery({ queryKey: ['assistant-config'], queryFn: api.assistantConfig, enabled: isClient, staleTime: Infinity });
  const enabled = isClient && config.data?.enabled === true;
  const navigate = useNavigate();
  const location = useLocation();
  const { proactiveHelp } = useSettings();
  const proactive = enabled && config.data?.proactive === true && proactiveHelp;

  const [conversation, setConversation] = useState<Stored>(() => ({ userId: user?.id ?? 0, messages: [], slots: {} }));
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nextId = useRef(1);
  const details = useRef<{ path: string; value: Omit<PageContext, 'page'> } | null>(null);
  const conversationRef = useRef(conversation);
  conversationRef.current = conversation;
  const [bubble, setBubble] = useState<{ signal: Signal; text: string; path: string } | null>(null);
  const bubbleRef = useRef(bubble);
  bubbleRef.current = bubble;
  const offerTimer = useRef<number | null>(null);
  const openRef = useRef(open);
  openRef.current = open;

  useEffect(() => {
    if (!user) return;
    const stored = readStored(user.id);
    nextId.current = stored.messages.reduce((m, x) => Math.max(m, x.id), 0) + 1;
    setConversation(stored);
    setOpen(false);
  }, [user]);

  useEffect(() => {
    if (user) writeStored(conversation);
  }, [conversation, user]);

  const pageContext = useCallback((): PageContext => {
    const page = pageKindFor(location.pathname);
    const extra = details.current && details.current.path === location.pathname ? details.current.value : {};
    return { ...extra, page };
  }, [location.pathname]);

  const setPageDetails = useCallback(
    (value: Omit<PageContext, 'page'> | null) => {
      details.current = value ? { path: location.pathname, value } : null;
    },
    [location.pathname],
  );

  const runUi = useCallback(
    (actions: UiAction[]) => {
      let leftPage = false;
      for (const a of actions) {
        if (a.type === 'FILL_NEED_FORM') {
          const q = new URLSearchParams({ purpose: a.params.purpose, amount: a.params.amount, term: a.params.term, auto: '1' });
          navigate(`/need?${q.toString()}`);
          leftPage = true;
        } else if (a.type === 'OPEN_WIZARD') {
          navigate(`/applications/new?${new URLSearchParams(a.params).toString()}`);
          leftPage = true;
        } else if (a.type === 'NAVIGATE' && a.params.to?.startsWith('/')) {
          navigate(a.params.to);
          leftPage = true;
        } else if (a.type === 'HIGHLIGHT') {
          highlight(a.params.target);
        }
      }
      // На телефоне панель закрывает страницу целиком: после действия показываем страницу
      if (isNarrowScreen() && (leftPage || actions.some((a) => a.type === 'HIGHLIGHT'))) setOpen(false);
    },
    [navigate],
  );

  const append = useCallback((message: Omit<ChatMessage, 'id'>) => {
    const id = nextId.current++;
    setConversation((c) => ({ ...c, messages: [...c.messages, { ...message, id }] }));
  }, []);

  const handle = useCallback(
    async (call: Promise<AssistantReply>, kind: PageKind) => {
      setPending(true);
      setError(null);
      try {
        const reply = await call;
        const id = nextId.current++;
        // Подсказка по мастеру или заявке относится к той странице, куда помощник ведёт клиента
        const lastKind: PageKind =
          reply.state === 'WIZARD_HELP' ? 'WIZARD' : reply.state === 'APPLICATION_HELP' ? 'APPLICATION' : kind;
        setConversation((c) => ({
          ...c,
          state: reply.state,
          slots: reply.slots ?? {},
          lastKind,
          messages: reply.text
            ? [...c.messages, { id, from: 'assistant', text: reply.text, terms: reply.terms, buttons: reply.buttons }]
            : c.messages,
        }));
        runUi(reply.ui ?? []);
      } catch (e) {
        setError(e instanceof ApiError ? e.message : 'Помощник сейчас недоступен. Повторите попытку');
      } finally {
        setPending(false);
      }
    },
    [runUi],
  );

  const action = useCallback(
    (name: string, params: Record<string, string> = {}) => {
      const c = conversationRef.current;
      const page = pageContext();
      handle(api.assistantAction({ state: c.state, slots: c.slots, page, action: name, params }), page.page);
    },
    [handle, pageContext],
  );

  const openPanel = useCallback(
    (topic?: string, entry = 'FAB') => {
      if (!enabled) return;
      setOpen(true);
      const page = pageContext();
      api.assistantEvent({ type: 'OPENED', page: page.page, entry }).catch(() => {});
      const c = conversationRef.current;
      if (topic) {
        action('START', { topic });
      } else if (c.messages.length === 0 || (c.lastKind !== page.page && (page.page === 'WIZARD' || page.page === 'APPLICATION'))) {
        // На новой странице мастера или заявки сразу подсказываем по ней, иначе продолжаем разговор
        action('START');
      }
    },
    [enabled, pageContext, action],
  );

  const send = useCallback(
    (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || pending) return;
      append({ from: 'user', text: trimmed });
      const c = conversationRef.current;
      const page = pageContext();
      handle(api.assistantMessage({ state: c.state, slots: c.slots, page, text: trimmed }), page.page);
    },
    [append, handle, pageContext, pending],
  );

  const press = useCallback(
    (b: AssistantButton) => {
      if (pending) return;
      if (b.action === 'HIGHLIGHT') {
        runUi([{ type: 'HIGHLIGHT', params: b.params }]);
        return;
      }
      append({ from: 'user', text: b.label });
      action(b.action, b.params);
    },
    [action, append, pending, runUi],
  );

  const restart = useCallback(() => {
    const fresh = { userId: conversationRef.current.userId, messages: [], slots: {}, offers: conversationRef.current.offers };
    setConversation(fresh);
    conversationRef.current = fresh;
    action('START');
  }, [action]);

  // --- Предложения помощи

  const markOffer = useCallback((path: string, mark: 'shown' | 'dismissed') => {
    setConversation((c) => {
      const log = c.offers ?? { total: 0, pages: {} };
      return {
        ...c,
        offers: { total: mark === 'shown' ? log.total + 1 : log.total, pages: { ...log.pages, [path]: mark } },
      };
    });
  }, []);

  const offer = useCallback(
    (signal: Signal) => {
      const path = location.pathname;
      const page = pageKindFor(path);
      if (offerTimer.current) window.clearTimeout(offerTimer.current);
      let attempts = 10;
      const tryShow = () => {
        const decision = decideOffer(
          conversationRef.current.offers ?? { total: 0, pages: {} },
          path,
          {
            enabled: proactive,
            panelOpen: openRef.current,
            bubbleShown: false,
            tabVisible: document.visibilityState === 'visible',
            maxPerSession: config.data?.maxOffersPerSession ?? 3,
          },
          isTyping(),
        );
        if (decision === 'show') {
          setBubble({ signal, text: OFFERS[signal].text, path });
          markOffer(path, 'shown');
          api.assistantEvent({ type: 'OFFER_SHOWN', signal, page }).catch(() => {});
        } else if (decision === 'later' && attempts-- > 0) {
          // Клиент печатает или вкладка неактивна: ждём, пока освободится
          offerTimer.current = window.setTimeout(tryShow, 1000);
        }
      };
      if (!bubbleRef.current) tryShow();
    },
    [location.pathname, proactive, config.data?.maxOffersPerSession, markOffer],
  );

  const acceptOffer = useCallback(() => {
    if (!bubble) return;
    api.assistantEvent({ type: 'OFFER_ACCEPTED', signal: bubble.signal, page: pageKindFor(bubble.path) }).catch(() => {});
    setBubble(null);
    openPanel(OFFERS[bubble.signal].topic, 'OFFER');
  }, [bubble, openPanel]);

  const dismissOffer = useCallback(() => {
    if (!bubble) return;
    api.assistantEvent({ type: 'OFFER_DISMISSED', signal: bubble.signal, page: pageKindFor(bubble.path) }).catch(() => {});
    markOffer(bubble.path, 'dismissed');
    setBubble(null);
  }, [bubble, markOffer]);

  // Пузырь относится к странице, где возник сигнал
  useEffect(() => {
    setBubble(null);
    if (offerTimer.current) window.clearTimeout(offerTimer.current);
  }, [location.pathname]);

  useEffect(() => {
    if (open) setBubble(null);
  }, [open]);

  const close = useCallback(() => setOpen(false), []);

  // Клиент перешёл в мастер или на страницу заявки при открытой панели: сразу подсказываем по новой странице
  const kind = pageKindFor(location.pathname);
  const autoStarted = useRef<string | null>(null);
  useEffect(() => {
    if (!open || !enabled || pending) return;
    const key = location.pathname;
    if ((kind === 'WIZARD' || kind === 'APPLICATION') && conversationRef.current.lastKind !== kind && autoStarted.current !== key) {
      // Страница сообщает подробности в своём эффекте, поэтому ждём немного
      const timer = window.setTimeout(() => {
        autoStarted.current = key;
        action('START');
      }, 300);
      return () => window.clearTimeout(timer);
    }
  }, [kind, location.pathname, open, enabled, pending, action]);

  const value = useMemo<AssistantApi>(
    () => ({
      enabled,
      config: config.data,
      open: open && enabled,
      messages: conversation.messages,
      pending,
      error,
      openPanel,
      close,
      restart,
      send,
      press,
      setPageDetails,
      bubble: bubble && enabled ? { signal: bubble.signal, text: bubble.text } : null,
      offer,
      acceptOffer,
      dismissOffer,
    }),
    [
      enabled,
      config.data,
      open,
      conversation.messages,
      pending,
      error,
      openPanel,
      close,
      restart,
      send,
      press,
      setPageDetails,
      bubble,
      offer,
      acceptOffer,
      dismissOffer,
    ],
  );
  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
}

const disabled: AssistantApi = {
  enabled: false,
  config: undefined,
  open: false,
  messages: [],
  pending: false,
  error: null,
  openPanel: () => {},
  close: () => {},
  restart: () => {},
  send: () => {},
  press: () => {},
  setPageDetails: () => {},
  bubble: null,
  offer: () => {},
  acceptOffer: () => {},
  dismissOffer: () => {},
};

/** Вне провайдера (например в тестах отдельных компонентов) помощник просто выключен. */
export function useAssistant(): AssistantApi {
  return useContext(AssistantContext) ?? disabled;
}

/** Страница сообщает помощнику подробности: шаг мастера, статус заявки, значения формы. */
export function useAssistantPage(value: Omit<PageContext, 'page'>) {
  const { setPageDetails } = useAssistant();
  const key = JSON.stringify(value);
  useEffect(() => {
    setPageDetails(JSON.parse(key));
  }, [key, setPageDetails]);
  useEffect(() => () => setPageDetails(null), [setPageDetails]);
}
