import { useEffect, useState } from 'react';
import Investigations from './Investigations';

type CurrentUser = { id: string; email: string | null; displayName: string | null; avatarUrl: string | null };
type AuthState = { kind: 'loading' | 'unauthenticated' | 'error' } | { kind: 'authenticated'; user: CurrentUser };
type Csrf = { token: string; headerName: string };

async function request(path: string, init: RequestInit = {}) {
  return fetch(path, { credentials: 'same-origin', cache: 'no-store',
    signal: AbortSignal.timeout(10000), ...init });
}

async function csrf(): Promise<Csrf> {
  const response = await request('/api/csrf');
  if (!response.ok) throw new Error('Unable to obtain CSRF state');
  const value: unknown = await response.json();
  if (typeof value !== 'object' || value === null || !('token' in value)
    || !('headerName' in value) || typeof value.token !== 'string'
    || typeof value.headerName !== 'string') throw new Error('Invalid CSRF response');
  return { token: value.token, headerName: value.headerName };
}

function isUser(value: unknown): value is CurrentUser {
  if (typeof value !== 'object' || value === null) return false;
  return 'id' in value && typeof value.id === 'string'
    && 'email' in value && (value.email === null || typeof value.email === 'string')
    && 'displayName' in value && (value.displayName === null || typeof value.displayName === 'string')
    && 'avatarUrl' in value && (value.avatarUrl === null || typeof value.avatarUrl === 'string');
}

function safeAvatar(url: string | null): string | undefined {
  if (!url) return undefined;
  try { return new URL(url).protocol === 'https:' ? url : undefined; } catch { return undefined; }
}

export default function App() {
  const [auth, setAuth] = useState<AuthState>({ kind: 'loading' });
  const [health, setHealth] = useState('CHECKING');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [attempt, setAttempt] = useState(0);
  const loginFailed = new URLSearchParams(window.location.search).get('login') === 'failed';

  useEffect(() => {
    let active = true;
    setAuth({ kind: 'loading' });
    async function bootstrap() {
      try {
        const response = await request('/api/me');
        if (response.status === 401) {
          if (active) setAuth({ kind: 'unauthenticated' });
          return;
        }
        if (!response.ok) throw new Error('Unable to load current user');
        const user: unknown = await response.json();
        if (!isUser(user)) throw new Error('Invalid current user response');
        await csrf(); // Login clears the previous token: obtain fresh session CSRF state.
        if (active) setAuth({ kind: 'authenticated', user });
      } catch {
        if (active) setAuth({ kind: 'error' });
      }
    }
    void bootstrap();
    void request('/api/health').then(async response => {
      const body: unknown = response.ok ? await response.json() : null;
      if (active) setHealth(typeof body === 'object' && body !== null
        && 'status' in body && body.status === 'UP' ? 'UP' : 'DOWN');
    }).catch(() => { if (active) setHealth('DOWN'); });
    return () => { active = false; };
  }, [attempt]);

  async function logout() {
    setBusy(true);
    setMessage('');
    try {
      const token = await csrf();
      const response = await request('/logout', { method: 'POST', headers: { [token.headerName]: token.token } });
      if (response.status !== 204) throw new Error('Logout failed');
      setAuth({ kind: 'unauthenticated' });
      try { await csrf(); } catch { setMessage('Signed out. CSRF refresh failed; retry before another action.'); }
    } catch {
      setMessage('Unable to confirm logout. Retry or refresh to check your session.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <h1>AI Incident Orchestrator</h1>
      <p role="status">Backend status: <strong>{health}</strong></p>
      {loginFailed && <p role="alert">Sign in failed. Please try again.</p>}
      {auth.kind === 'loading' && <p role="status">Checking your session…</p>}
      {auth.kind === 'error' && <div role="alert">
        <p>Unable to check your session. Check the backend and your connection.</p>
        <button onClick={() => setAttempt(value => value + 1)}>Retry</button>
      </div>}
      {auth.kind === 'unauthenticated' && <button onClick={() => window.location.assign('/oauth2/authorization/google')}>
        Sign in with Google
      </button>}
      {auth.kind === 'authenticated' && <section>
        {safeAvatar(auth.user.avatarUrl) && <img src={safeAvatar(auth.user.avatarUrl)}
          alt="Profile avatar" width="64" height="64" referrerPolicy="no-referrer" />}
        <h2>{auth.user.displayName ?? 'Signed in'}</h2>
        <p>{auth.user.email ?? 'Email not provided'}</p>
        <p>Application user ID: {auth.user.id}</p>
        <button disabled={busy} onClick={() => void logout()}>{busy ? 'Signing out…' : 'Logout'}</button>
        <Investigations getCsrf={csrf} />
      </section>}
      {message && <p role="alert">{message}</p>}
    </main>
  );
}
