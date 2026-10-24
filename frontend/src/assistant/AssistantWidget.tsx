import { useEffect, useRef, useState, type FormEvent } from 'react';
import { formatMoney, formatMonths } from '../api/format';
import { useAssistant } from './AssistantContext';
import type { ChatMessage } from './types';

const AssistantIcon = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
    <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12Z" />
    <path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .8-1 1.5v.2" />
    <path d="M12 16.5h.01" />
  </svg>
);

const MinimizeIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" aria-hidden>
    <path d="M5 12h14" />
  </svg>
);

const RestartIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
    <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
    <path d="M3 3v5h5" />
  </svg>
);

const SendIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
    <path d="M4 12 20 4l-4 16-4-7-8-1Z" />
  </svg>
);

/** Плавающая кнопка и панель помощника. Панель не модальная: страница под ней остаётся доступной. */
export function AssistantWidget() {
  const assistant = useAssistant();
  if (!assistant.enabled) return null;
  return (
    <div className="assistant-root">
      {assistant.open && <AssistantPanel />}
      {!assistant.open && assistant.bubble && (
        <div className="assistant-bubble" role="status">
          <p>{assistant.bubble.text}</p>
          <div className="assistant-bubble-actions">
            <button type="button" className="btn btn-primary btn-sm" onClick={assistant.acceptOffer}>
              Помочь
            </button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={assistant.dismissOffer}>
              Не сейчас
            </button>
          </div>
        </div>
      )}
      <button
        type="button"
        className="assistant-fab"
        title="Помощник"
        aria-expanded={assistant.open}
        onClick={() => (assistant.open ? assistant.close() : assistant.openPanel())}
      >
        <AssistantIcon />
        <span className="sr-only">Помощник</span>
      </button>
    </div>
  );
}

function AssistantPanel() {
  const { messages, pending, error, send, press, close, restart } = useAssistant();
  const [text, setText] = useState('');
  const log = useRef<HTMLDivElement>(null);
  const lastAssistant = [...messages].reverse().find((m) => m.from === 'assistant');

  // Новый ответ показываем с начала, а не с конца: таблица и кнопки идут после текста
  useEffect(() => {
    const el = log.current;
    if (!el) return;
    const last = el.querySelector<HTMLElement>('.assistant-msg:last-of-type');
    const top = last && last.classList.contains('from-assistant') ? last.offsetTop - 8 : el.scrollHeight;
    el.scrollTo?.({ top });
  }, [messages, pending]);

  function submit(e: FormEvent) {
    e.preventDefault();
    send(text);
    setText('');
  }

  return (
    <section
      className="assistant-panel"
      role="dialog"
      aria-label="Помощник"
      onKeyDown={(e) => e.key === 'Escape' && close()}
    >
      <header className="assistant-head">
        <strong>Помощник</strong>
        <div className="assistant-head-actions">
          <button type="button" className="icon-btn" onClick={restart} title="Начать заново" disabled={pending}>
            <RestartIcon />
            <span className="sr-only">Начать заново</span>
          </button>
          <button type="button" className="icon-btn" onClick={close} title="Свернуть">
            <MinimizeIcon />
            <span className="sr-only">Свернуть</span>
          </button>
        </div>
      </header>
      <p className="assistant-note">Помощник подсказывает, но не принимает решения. Условия кредита определяет банк</p>
      <div className="assistant-log" ref={log} aria-live="polite">
        {messages.map((m) => (
          <Message key={m.id} message={m} active={m === lastAssistant && !pending} onPress={press} />
        ))}
        {pending && <div className="assistant-msg from-assistant typing">Помощник печатает…</div>}
        {error && (
          <div className="assistant-error" role="alert">
            {error}
          </div>
        )}
      </div>
      <form className="assistant-input" onSubmit={submit}>
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Напишите вопрос…"
          maxLength={1000}
          aria-label="Вопрос помощнику"
        />
        <button type="submit" className="icon-btn" disabled={pending || text.trim() === ''} title="Отправить">
          <SendIcon />
          <span className="sr-only">Отправить</span>
        </button>
      </form>
    </section>
  );
}

export function Message({
  message,
  active,
  onPress,
}: {
  message: ChatMessage;
  active: boolean;
  onPress: (b: NonNullable<ChatMessage['buttons']>[number]) => void;
}) {
  return (
    <div className={`assistant-msg from-${message.from}`}>
      <div className="assistant-text">{message.text}</div>
      {message.terms && message.terms.length > 0 && (
        <>
          <table className="assistant-terms">
            <thead>
              <tr>
                <th>Срок</th>
                <th>Платёж в месяц</th>
                <th>Переплата</th>
              </tr>
            </thead>
            <tbody>
              {message.terms.map((t) => (
                <tr key={t.termMonths} className={t.recommended ? 'recommended' : ''}>
                  <td>{formatMonths(t.termMonths)}</td>
                  <td>{formatMoney(t.monthlyPayment)}</td>
                  <td>{formatMoney(t.overpayment)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="assistant-caption">Ориентировочно по ставке «от». Точные условия банк покажет в предложении</p>
        </>
      )}
      {active && message.buttons && message.buttons.length > 0 && (
        <div className="assistant-replies">
          {message.buttons.map((b) => (
            <button key={b.label} type="button" className="chip" onClick={() => onPress(b)}>
              {b.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
