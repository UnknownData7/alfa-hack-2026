import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import type { Role } from './api/types';
import { useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { Loader } from './components/ui';
import { LoginPage } from './pages/LoginPage';
import { QueuePage } from './pages/bank/QueuePage';
import { TaskPage } from './pages/bank/TaskPage';
import { ApplicationPage } from './pages/client/ApplicationPage';
import { BenefitsPage } from './pages/client/BenefitsPage';
import { HistoryPage } from './pages/client/HistoryPage';
import { HomePage } from './pages/client/HomePage';
import { NeedPage } from './pages/client/NeedPage';
import { WizardPage } from './pages/client/WizardPage';

function Protected({ role, children }: { role: Role; children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <Loader />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to={user.role === 'UNDERWRITER' ? '/bank' : '/'} replace />;
  return <Layout>{children}</Layout>;
}

export default function App() {
  const { user, loading } = useAuth();
  return (
    <Routes>
      <Route
        path="/login"
        element={
          loading ? <Loader /> : user ? <Navigate to={user.role === 'UNDERWRITER' ? '/bank' : '/'} replace /> : <Layout><LoginPage /></Layout>
        }
      />
      <Route path="/" element={<Protected role="CLIENT"><HomePage /></Protected>} />
      <Route path="/history" element={<Protected role="CLIENT"><HistoryPage /></Protected>} />
      <Route path="/benefits" element={<Protected role="CLIENT"><BenefitsPage /></Protected>} />
      <Route path="/need" element={<Protected role="CLIENT"><NeedPage /></Protected>} />
      <Route path="/applications/new" element={<Protected role="CLIENT"><WizardPage /></Protected>} />
      <Route path="/applications/:id/edit" element={<Protected role="CLIENT"><WizardPage /></Protected>} />
      <Route path="/applications/:id" element={<Protected role="CLIENT"><ApplicationPage /></Protected>} />
      <Route path="/bank" element={<Protected role="UNDERWRITER"><QueuePage /></Protected>} />
      <Route path="/bank/tasks/:id" element={<Protected role="UNDERWRITER"><TaskPage /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
