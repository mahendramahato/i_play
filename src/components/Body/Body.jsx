import { useCallback, useEffect, useRef, useState } from 'react';
import Login from '../Login/Login';
import './Body.css';

// Set VITE_API_URL at build time for the server (e.g. /api/songs behind a reverse proxy).
const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/songs';
// Login, logout and session live one level up: /api/login rather than /api/songs/login.
const API_BASE = API.replace(/\/songs$/, '');

const BAR_COUNT = 64;
const BARS = Array.from({ length: BAR_COUNT }, (_, i) => {
    const wobble = Math.abs(
        Math.sin(i * 0.9) * 0.5 + Math.sin(i * 0.31) * 0.3 + Math.sin(i * 2.3) * 0.2
    );
    const envelope = Math.sin((Math.PI * i) / (BAR_COUNT - 1)) * 0.65 + 0.35;
    return 14 + wobble * envelope * 86;
});

function formatTime(sec) {
    if (!Number.isFinite(sec)) return '0:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
}

function Body() {
    const audioRef = useRef(null);
    // True while the user wants music playing. Unlike isPlaying it is not reset by the
    // browser's own pause event at the end of a song, so the next song can start itself.
    const wantPlayingRef = useRef(false);
    const [songs, setSongs] = useState([]);
    const [index, setIndex] = useState(0);
    const [isPlaying, setIsPlaying] = useState(false);
    const [currentTime, setCurrentTime] = useState(0);
    const [duration, setDuration] = useState(0);
    const [error, setError] = useState('');

    // checked: has the session check returned. enabled: is the server's lock on.
    // ok: may we load music (lock off, or a valid session cookie).
    const [auth, setAuth] = useState({ checked: false, enabled: false, ok: false });

    useEffect(() => {
        fetch(`${API_BASE}/session`)
            .then((res) => {
                if (!res.ok) throw new Error();
                return res.json();
            })
            .then((s) => setAuth({ checked: true, enabled: s.authEnabled, ok: s.authenticated }))
            .catch(() => setError('Could not reach the server.'));
    }, []);

    const loadSongs = useCallback(() => {
        fetch(API)
            .then((res) => {
                // Session expired or was cleared: back to the sign-in screen.
                if (res.status === 401) {
                    setAuth((a) => ({ ...a, ok: false }));
                    return null;
                }
                if (!res.ok) throw new Error();
                return res.json();
            })
            .then((data) => data && setSongs(data))
            .catch(() => setError('Could not load songs. Is the backend running?'));
    }, []);

    useEffect(() => {
        if (auth.ok) loadSongs();
    }, [auth.ok, loadSongs]);

    const signOut = async () => {
        wantPlayingRef.current = false;
        audioRef.current?.pause();
        await fetch(`${API_BASE}/logout`, { method: 'POST' }).catch(() => {});
        setSongs([]);
        setIndex(0);
        setAuth((a) => ({ ...a, ok: false }));
    };

    const song = songs[index];

    // Keep playing when the song changes (Next, Prev, or the previous song ended).
    useEffect(() => {
        if (wantPlayingRef.current) {
            audioRef.current?.play().catch(() => setIsPlaying(false));
        }
    }, [index]);

    const togglePlay = () => {
        const audio = audioRef.current;
        if (isPlaying) {
            wantPlayingRef.current = false;
            audio.pause();
        } else {
            wantPlayingRef.current = true;
            audio.play().catch(() => setIsPlaying(false));
        }
    };

    const handleEnded = () => {
        if (songs.length === 1) {
            // Only one song: the index won't change, so restart it directly.
            audioRef.current.currentTime = 0;
            audioRef.current.play().catch(() => setIsPlaying(false));
        } else {
            next();
        }
    };

    const next = () => setIndex((i) => (i + 1) % songs.length);
    const prev = () => setIndex((i) => (i - 1 + songs.length) % songs.length);

    const seek = (e) => {
        const time = Number(e.target.value);
        audioRef.current.currentTime = time;
        setCurrentTime(time);
    };

    if (error) return <div className="body">{error}</div>;
    if (!auth.checked) return <div className="body" />;
    if (auth.enabled && !auth.ok) {
        return <Login apiBase={API_BASE} onSuccess={() => setAuth((a) => ({ ...a, ok: true }))} />;
    }
    if (!song) return <div className="body">No songs yet</div>;

    return (
        <div className="body">
            {auth.enabled && (
                <button className="sign-out" onClick={signOut}>
                    Sign out
                </button>
            )}
            <audio
                ref={audioRef}
                src={`${API}/${song.id}/stream`}
                onPlay={() => setIsPlaying(true)}
                onPause={() => setIsPlaying(false)}
                onTimeUpdate={(e) => setCurrentTime(e.target.currentTime)}
                onLoadedMetadata={(e) => setDuration(e.target.duration)}
                onEnded={handleEnded}
            />
            {song.hasCover && (
                <img className="cover" src={`${API}/${song.id}/cover`} alt="" />
            )}
            <div className="song-info">
                <div className="song-title">{song.title}</div>
                {(song.artist || song.album) && (
                    <div className="song-meta">
                        {[song.artist, song.album].filter(Boolean).join(' \u00B7 ')}
                    </div>
                )}
            </div>
            <div className="waveform">
                <div className="waveform-bars" aria-hidden="true">
                    {BARS.map((height, i) => {
                        const played = duration > 0 && i / BAR_COUNT <= currentTime / duration;
                        return (
                            <span
                                key={i}
                                style={{
                                    height: `${height}%`,
                                    // Hue ramps red -> pink -> violet across the clip.
                                    background: played
                                        ? `hsl(${344 + (i / BAR_COUNT) * 60}, 90%, 62%)`
                                        : 'rgba(255, 255, 255, 0.16)',
                                }}
                            />
                        );
                    })}
                </div>
                {/* A transparent range input sits on top: the bars are only a
                    picture, while this keeps dragging, keyboard arrows and
                    screen-reader support working for free. */}
                <input
                    className="waveform-input"
                    type="range"
                    min="0"
                    max={duration || 0}
                    step="0.1"
                    value={currentTime}
                    onChange={seek}
                    aria-label="Seek"
                />
            </div>
            <div className="controls">
                <span className="time">{formatTime(currentTime)}</span>
                <button className="btn" onClick={prev} aria-label="Previous">&#9198;&#xFE0E;</button>
                <button className="btn btn-play" onClick={togglePlay} aria-label={isPlaying ? 'Pause' : 'Play'}>
                    {isPlaying ? '\u23F8\uFE0E' : '\u25B6\uFE0E'}
                </button>
                <button className="btn" onClick={next} aria-label="Next">&#9197;&#xFE0E;</button>
                <span className="time">{formatTime(duration)}</span>
            </div>
        </div>
    );
}

export default Body;
