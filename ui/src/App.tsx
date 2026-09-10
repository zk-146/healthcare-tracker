import { useState } from 'react';
import { useAuth } from './auth/useAuth';
import { LoginPage } from './auth/LoginPage';
import { RegisterPage } from './auth/RegisterPage';
import { DashboardPage } from './dashboard/DashboardPage';
import { ProfilePage } from './profile/ProfilePage';
import { WorkoutsPage } from './workouts/WorkoutsPage';

type View = 'activity' | 'workouts' | 'profile';
type AuthView = 'login' | 'register';

export function App() {
  const { api, isAuthenticated, signIn, signUp, signOut, clearSession } = useAuth();
  const [view, setView] = useState<View>('activity');
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [authView, setAuthView] = useState<AuthView>('login');

  if (!isAuthenticated) {
    return authView === 'login' ? (
      <LoginPage onSubmit={signIn} onSwitchToRegister={() => setAuthView('register')} />
    ) : (
      <RegisterPage onSubmit={signUp} onSwitchToLogin={() => setAuthView('login')} />
    );
  }

  return (
    <main className="page">
      <header className="app-header">
        <h1 className="app-title">Activity Tracker</h1>
        <div className="app-header-actions">
          {view === 'workouts' && (
            <>
              <button type="button" className="link-button" onClick={() => setImportOpen(true)}>
                Import CSV
              </button>
              <button type="button" className="link-button" onClick={() => setCreateOpen(true)}>
                ＋ Log workout
              </button>
            </>
          )}
          <button type="button" className="link-button" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </header>

      <nav className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={view === 'activity'}
          className="tab"
          onClick={() => setView('activity')}
        >
          Activity
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'workouts'}
          className="tab"
          onClick={() => setView('workouts')}
        >
          Workouts
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'profile'}
          className="tab"
          onClick={() => setView('profile')}
        >
          Profile
        </button>
      </nav>

      {/*
        Switching tabs unmounts the other page. That remount is the whole cache story:
        returning to Activity refetches the dashboard, so an edit made on the Workouts
        tab is reflected without any invalidation logic. Cost: a brief skeleton.
      */}
      {view === 'activity' && <DashboardPage api={api} />}
      {view === 'workouts' && (
        <WorkoutsPage
          api={api}
          createOpen={createOpen}
          onCreateClose={() => setCreateOpen(false)}
          importOpen={importOpen}
          onImportClose={() => setImportOpen(false)}
        />
      )}
      {view === 'profile' && (
        <ProfilePage
          api={api}
          onAccountDeleted={clearSession}
          onPasswordChanged={() => void signOut()}
        />
      )}
    </main>
  );
}
