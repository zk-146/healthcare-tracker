import { useState } from 'react';
import { useAuth } from './auth/useAuth';
import { LoginPage } from './auth/LoginPage';
import { DashboardPage } from './dashboard/DashboardPage';
import { WorkoutsPage } from './workouts/WorkoutsPage';

type View = 'activity' | 'workouts';

export function App() {
  const { api, isAuthenticated, signIn, signOut } = useAuth();
  const [view, setView] = useState<View>('activity');
  const [createOpen, setCreateOpen] = useState(false);

  if (!isAuthenticated) {
    return <LoginPage onSubmit={signIn} />;
  }

  return (
    <main className="page">
      <header className="app-header">
        <h1 className="app-title">Activity Tracker</h1>
        <div className="app-header-actions">
          {view === 'workouts' && (
            <button type="button" className="link-button" onClick={() => setCreateOpen(true)}>
              ＋ Log workout
            </button>
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
      </nav>

      {/*
        Switching tabs unmounts the other page. That remount is the whole cache story:
        returning to Activity refetches the dashboard, so an edit made on the Workouts
        tab is reflected without any invalidation logic. Cost: a brief skeleton.
      */}
      {view === 'activity' ? (
        <DashboardPage api={api} />
      ) : (
        <WorkoutsPage
          api={api}
          createOpen={createOpen}
          onCreateClose={() => setCreateOpen(false)}
        />
      )}
    </main>
  );
}
