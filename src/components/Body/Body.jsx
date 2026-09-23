import { useEffect, useRef, useState } from 'react';
import './Body.css';

// Set VITE_API_URL at build time for the server (e.g. /api/songs behind a reverse proxy).
const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/songs';

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

    useEffect(() => {
        fetch(API)
            .then((res) => {
                if (!res.ok) throw new Error();
                return res.json();
            })
            .then(setSongs)
            .catch(() => setError('Could not load songs. Is the backend running?'));
    }, []);

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
    if (!song) return <div className="body">No songs yet</div>;

    return (
        <div className="body">
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
                {song.artist && <div className="song-artist">{song.artist}</div>}
                {song.album && <div className="song-artist">{song.album}</div>}
            </div>
            <input
                className="slider"
                type="range"
                min="0"
                max={duration || 0}
                step="0.1"
                value={currentTime}
                onChange={seek}
            />
            <div className="time">
                {formatTime(currentTime)} / {formatTime(duration)}
            </div>
            <div className="controls">
                <button onClick={prev} aria-label="Previous">&#9198;&#xFE0E;</button>
                <button onClick={togglePlay} aria-label={isPlaying ? 'Pause' : 'Play'}>
                    {isPlaying ? '\u23F8\uFE0E' : '\u25B6\uFE0E'}
                </button>
                <button onClick={next} aria-label="Next">&#9197;&#xFE0E;</button>
            </div>
        </div>
    );
}

export default Body;
