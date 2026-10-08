import { useEffect, useRef } from 'react';
import { getAnalyser } from '../../audio/analyser';
import './Visualizer.css';

const RING_BARS = 120;

// Full-screen animated background. Three soft colour fields drift slowly and a
// ring of bars sits above the player. While music plays, the fields swell with
// the bass, mids and highs, and the ring traces the live spectrum; otherwise
// everything breathes gently on its own.
function Visualizer() {
    const canvasRef = useRef(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        const g = canvas.getContext('2d');
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const bins = new Uint8Array(256);
        const ring = new Float32Array(RING_BARS);
        const level = { bass: 0, mid: 0, high: 0 };
        let w = 0;
        let h = 0;
        let raf = 0;
        let frameCount = 0;
        // Where the ring goes, measured from the page so it never collides with text.
        const place = { cx: 0, cy: 0, r0: 0 };
        const MAX_REACH = 1.68; // outer edge of the longest bar, as a multiple of r0

        const measure = () => {
            const card = document.querySelector('.login-card');
            if (card) {
                // Sign-in: a halo just outside the card's corners.
                const b = card.getBoundingClientRect();
                place.cx = b.left + b.width / 2;
                place.cy = b.top + b.height / 2;
                place.r0 = Math.hypot(b.width, b.height) / 2 + 10;
                return;
            }
            const player = document.querySelector('.cover, .song-info');
            const top = player ? player.getBoundingClientRect().top : h * 0.7;
            // Player: centred in the free space above it, small enough that even
            // the longest bar stops short of the song title.
            place.cx = w / 2;
            place.cy = top / 2;
            place.r0 = Math.max(36, Math.min(w * 0.16, (top - 32) / 2 / MAX_REACH));
        };

        const resize = () => {
            const dpr = Math.min(window.devicePixelRatio || 1, 2);
            w = window.innerWidth;
            h = window.innerHeight;
            canvas.width = Math.round(w * dpr);
            canvas.height = Math.round(h * dpr);
            g.setTransform(dpr, 0, 0, dpr, 0, 0);
            measure();
            if (reduceMotion) frame(0);
        };

        const band = (from, to) => {
            let sum = 0;
            for (let i = from; i < to; i++) sum += bins[i];
            return sum / ((to - from) * 255);
        };

        const blob = (x, y, r, rgb, alpha) => {
            const grad = g.createRadialGradient(x, y, 0, x, y, r);
            grad.addColorStop(0, `rgba(${rgb},${alpha})`);
            grad.addColorStop(1, `rgba(${rgb},0)`);
            g.fillStyle = grad;
            g.fillRect(x - r, y - r, r * 2, r * 2);
        };

        function frame(t) {
            const time = t / 1000;
            if (frameCount++ % 30 === 0) measure();
            const analyser = getAnalyser();
            if (analyser) analyser.getByteFrequencyData(bins);
            else bins.fill(0);

            // ~94 Hz per bin at 48 kHz: bins 1-3 bass, 4-23 mids, 24-95 highs.
            const target = analyser
                ? { bass: band(1, 4), mid: band(4, 24), high: band(24, 96) }
                : { bass: 0, mid: 0, high: 0 };
            for (const k in level) level[k] += (target[k] - level[k]) * 0.18;

            g.globalCompositeOperation = 'source-over';
            g.fillStyle = '#07060b';
            g.fillRect(0, 0, w, h);

            // Colour fields, added together so overlaps glow.
            g.globalCompositeOperation = 'lighter';
            const m = Math.max(w, h);
            blob(w * (0.28 + 0.08 * Math.sin(time * 0.13)), h * (0.32 + 0.07 * Math.cos(time * 0.11)),
                m * (0.42 + 0.14 * level.bass), '230,57,70', 0.26 + 0.3 * level.bass);
            blob(w * (0.74 + 0.07 * Math.cos(time * 0.09)), h * (0.28 + 0.08 * Math.sin(time * 0.12)),
                m * (0.36 + 0.12 * level.mid), '124,58,237', 0.22 + 0.28 * level.mid);
            blob(w * (0.52 + 0.1 * Math.sin(time * 0.07)), h * (0.8 + 0.05 * Math.cos(time * 0.1)),
                m * (0.3 + 0.1 * level.high), '255,45,111', 0.14 + 0.25 * level.high);

            // Spectrum ring, mirrored left/right, turning slowly.
            const { cx, cy, r0 } = place;
            const half = RING_BARS / 2;
            for (let i = 0; i < half; i++) {
                // Low frequencies get more bars than highs, the way ears hear them.
                const bin = Math.min(150, Math.floor(2 + Math.pow(i / half, 1.8) * 148));
                const live = analyser ? bins[bin] / 255 : 0;
                const ambient = 0.05 + 0.035 * Math.sin(time * 1.4 + i * 0.35);
                const v = Math.max(live, ambient);
                ring[i] += (v - ring[i]) * 0.35;
            }
            g.lineCap = 'round';
            g.lineWidth = Math.max(1.6, r0 * 0.018);
            const spin = reduceMotion ? 0 : time * 0.05;
            for (let i = 0; i < RING_BARS; i++) {
                const v = ring[i < half ? i : RING_BARS - 1 - i];
                const a = spin + (i / RING_BARS) * Math.PI * 2 - Math.PI / 2;
                const len = r0 * (0.04 + (MAX_REACH - 1.04) * v);
                const cos = Math.cos(a);
                const sin = Math.sin(a);
                g.strokeStyle = `rgba(255,${Math.round(70 + 90 * v)},${Math.round(110 + 60 * v)},${0.35 + 0.6 * v})`;
                g.beginPath();
                g.moveTo(cx + cos * r0, cy + sin * r0);
                g.lineTo(cx + cos * (r0 + len), cy + sin * (r0 + len));
                g.stroke();
            }
            g.globalCompositeOperation = 'source-over';
            g.strokeStyle = 'rgba(255,255,255,0.08)';
            g.lineWidth = 1;
            g.beginPath();
            g.arc(cx, cy, r0 - 6, 0, Math.PI * 2);
            g.stroke();

            // Darken the bottom so the player stays legible.
            const shade = g.createLinearGradient(0, 0, 0, h);
            shade.addColorStop(0.5, 'rgba(0,0,0,0)');
            shade.addColorStop(1, 'rgba(0,0,0,0.7)');
            g.fillStyle = shade;
            g.fillRect(0, 0, w, h);

            if (!reduceMotion) raf = requestAnimationFrame(frame);
        }

        window.addEventListener('resize', resize);
        resize();
        if (!reduceMotion) raf = requestAnimationFrame(frame);
        return () => {
            cancelAnimationFrame(raf);
            window.removeEventListener('resize', resize);
        };
    }, []);

    return <canvas ref={canvasRef} className="visualizer" aria-hidden="true" />;
}

export default Visualizer;
