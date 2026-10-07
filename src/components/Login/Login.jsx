import { useEffect, useRef, useState } from 'react';
import './Login.css';

// Shown when the server's lock is on and there is no valid session.
// On success the server sets an HttpOnly cookie, so nothing is stored here.
function Login({ apiBase, onSuccess }) {
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const inputRef = useRef(null);

    useEffect(() => {
        inputRef.current?.focus();
    }, []);

    async function submit(e) {
        e.preventDefault();
        setBusy(true);
        setError('');
        try {
            const res = await fetch(`${apiBase}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password }),
            });
            if (res.ok) {
                onSuccess();
                return;
            }
            if (res.status === 429) setError('Too many attempts. Try again in a few minutes.');
            else if (res.status === 401) setError('Wrong password.');
            else setError('Something went wrong. Try again.');
        } catch {
            setError('Could not reach the server.');
        }
        setPassword('');
        setBusy(false);
        inputRef.current?.focus();
    }

    return (
        <div className="login">
            <form className="login-card" onSubmit={submit}>
                <svg className="login-mark" viewBox="0 0 32 32" aria-hidden="true">
                    <rect x="3.6" y="12" width="3.2" height="8" rx="1.6" />
                    <rect x="9" y="8" width="3.2" height="16" rx="1.6" />
                    <rect x="14.4" y="5" width="3.2" height="22" rx="1.6" className="login-mark-peak" />
                    <rect x="19.8" y="9" width="3.2" height="14" rx="1.6" />
                    <rect x="25.2" y="11.5" width="3.2" height="9" rx="1.6" />
                </svg>
                <h1>Soundly</h1>
                <p>This library is private. Enter the password to listen.</p>
                <input
                    ref={inputRef}
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Password"
                    aria-label="Password"
                    autoComplete="current-password"
                    disabled={busy}
                />
                {error && <p className="login-error" role="alert">{error}</p>}
                <button type="submit" disabled={busy || !password}>
                    {busy ? 'Signing in…' : 'Sign in'}
                </button>
            </form>
        </div>
    );
}

export default Login;
