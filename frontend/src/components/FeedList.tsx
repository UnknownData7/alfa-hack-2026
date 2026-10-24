import { Link } from 'react-router-dom';
import { formatDateTime } from '../api/format';
import type { FeedCard } from '../api/types';

const TYPE_LABEL: Record<FeedCard['type'], string> = {
  STATUS: 'По вашей заявке',
  TIP: 'Подсказка',
  NEWS: 'Новость',
};

/** Лента «Для вас». Кнопка «Прочитано» убирает карточку из ленты насовсем. */
export function FeedList({ cards, onDismiss }: { cards: FeedCard[]; onDismiss: (id: string) => void }) {
  if (cards.length === 0) {
    return <p className="muted feed-empty">Новых сообщений нет. Здесь появятся события по заявкам и советы банка.</p>;
  }
  return (
    <ul className="feed-list">
      {cards.map((card) => (
        <li key={card.id} className={`feed-card feed-${card.type.toLowerCase()}`}>
          <div className="feed-meta">
            <span className="feed-type">{TYPE_LABEL[card.type]}</span>
            <time dateTime={card.createdAt}>{formatDateTime(card.createdAt)}</time>
          </div>
          <h3>{card.title}</h3>
          <p>{card.body}</p>
          <p className="feed-reason">{card.reason}</p>
          <div className="feed-actions">
            {card.relatedApplicationId && (
              <Link to={`/applications/${card.relatedApplicationId}`} className="link small">
                Открыть заявку
              </Link>
            )}
            <button type="button" className="link small" onClick={() => onDismiss(card.id)}>
              Прочитано
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
