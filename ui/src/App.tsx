import { useState } from 'react';
import { requestPasswordReset, resetPassword, verifyEmail } from './api/endpoints';
import { clearEmailLink, parseEmailLink, type EmailLink } from './auth/emailLink';
import { ForgotPasswordPage } from './auth/ForgotPasswordPage';
import { useAuth } from './auth/useAuth';
import { LoginPage } from './auth/LoginPage';
import { RegisterPage } from './auth/RegisterPage';
import { ResetPasswordPage } from './auth/ResetPasswordPage';
import { VerifyEmailPage } from './auth/VerifyEmailPage';
import { DashboardPage } from './dashboard/DashboardPage';
import { ProfilePage } from './profile/ProfilePage';
import { WorkoutsPage } from './workouts/WorkoutsPage';

type View = 'activity' | 'workouts' | 'profile';
type AuthView = 'login' | 'register' | 'forgot';

export function App() {
  const { api, isAuthenticated, signIn, signUp, signOut, clearSession } = useAuth();
  const [view, setView] = useState<View>('activity');
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [authView, setAuthView] = useState<AuthView>('login');
  // Read once, on load: a link from an account email (see auth/emailLink.ts).
  const [emailLink, setEmailLink] = useState<EmailLink | null>(() =>
    parseEmailLink(window.location.hash),
  );

  function dismissEmailLink() {
    clearEmailLink();
    setEmailLink(null);
  }

  // An emailed link takes precedence over whatever session this browser holds: the person
  // who opened it wants that action, signed in or not.
  if (emailLink?.kind === 'reset-password') {
    return (
      <ResetPasswordPage
        token={emailLink.token}
        onSubmit={(token, newPassword) => resetPassword(token, newPassword)}
        onDone={() => {
          // A reset revokes every refresh token, so a session stored here (possibly a
          // different account's) is dropped rather than left to fail on its next refresh.
          clearSession();
          setAuthView('login');
          dismissEmailLink();
        }}
      />
    );
  }

  if (emailLink?.kind === 'verify-email') {
    return (
      <VerifyEmailPage
        token={emailLink.token}
        onSubmit={(token) => verifyEmail(token)}
        onDone={dismissEmailLink}
      />
    );
  }

  if (!isAuthenticated) {
    if (authView === 'register') {
      return <RegisterPage onSubmit={signUp} onSwitchToLogin={() => setAuthView('login')} />;
    }
    if (authView === 'forgot') {
      return (
        <ForgotPasswordPage
          onSubmit={(email) => requestPasswordReset(email)}
          onBackToLogin={() => setAuthView('login')}
        />
      );
    }
    return (
      <LoginPage
        onSubmit={signIn}
        onSwitchToRegister={() => setAuthView('register')}
        onForgotPassword={() => setAuthView('forgot')}
      />
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
