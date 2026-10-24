import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { FeedCard } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ApplicationsList } from '../../components/ApplicationsList';
import { FeedList } from '../../components/FeedList';
import { ErrorBox, Loader } from '../../components/ui';

function greeting(date = new Date()) {
  const h = date.getHours();
  if (h < 5) return 'Доброй ночи';
  if (h < 12) return 'Доброе утро';
  if (h < 18) return 'Добрый день';
  return 'Добрый вечер';
}

export function HomePage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const feed = useQuery({ queryKey: ['feed'], queryFn: api.feed, refetchInterval: 5000 });
  const dismiss = useMutation({
    mutationFn: (id: string) => api.dismissFeedCard(id),
    // Карточка исчезает сразу, не дожидаясь ответа сервера
    onMutate: (id) => {
      queryClient.setQueryData<FeedCard[]>(['feed'], (cards) => cards?.filter((c) => c.id !== id));
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['feed'] }),
  });
  const firstName = user?.fullName.split(/\s+/)[1];

  return (
    <div className="home">
      <h1 className="greeting">
        {greeting()}
        {firstName ? `, ${firstName}` : ''}
      </h1>

      <div className="banners">
        <Link to="/need" className="banner banner-credit">
          <div className="banner-copy">
            <h2>Деньги на бизнес без визита в офис</h2>
            <p>Подберём кредит под задачу. По многим заявкам решение приходит сразу, деньги зачисляются на счёт.</p>
            <span className="banner-cta">Подобрать кредит</span>
          </div>
          <span className="art art-coins" aria-hidden>
            <i />
            <i />
            <i />
          </span>
        </Link>
        <Link to="/benefits" className="banner banner-referral">
          <div className="banner-copy">
            <h2>Деньги за рекомендации</h2>
            <p>5 000 ₽ за каждого знакомого предпринимателя, который откроет счёт по вашей ссылке</p>
            <span className="banner-cta">Пригласить</span>
          </div>
          <span className="art art-gift" aria-hidden>
            <i />
            <i />
          </span>
        </Link>
      </div>

      <div className="home-grid">
        <ApplicationsList title="Мои заявки" limit={4} />
        <section className="card feed" aria-labelledby="feed-title">
          <div className="section-head">
            <h2 id="feed-title">Для вас</h2>
            {feed.data && feed.data.length > 0 && <span className="counter">{feed.data.length}</span>}
          </div>
          {feed.isLoading && <Loader />}
          {feed.error && <ErrorBox error={feed.error} onRetry={() => feed.refetch()} />}
          {feed.data && <FeedList cards={feed.data} onDismiss={(id) => dismiss.mutate(id)} />}
        </section>
      </div>
    </div>
  );
}
