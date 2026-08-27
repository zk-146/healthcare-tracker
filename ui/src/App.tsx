import { useAuth } from './auth/useAuth';
import { LoginPage } from './auth/LoginPage';
import { DashboardPage } from './dashboard/DashboardPage';

export function App() {
  const { api, isAuthenticated, signIn, signOut } = useAuth();

  if (!isAuthenticated) {
    return <LoginPage onSubmit={signIn} />;
  }
  return <DashboardPage api={api} onSignOut={() => void signOut()} />;
}
