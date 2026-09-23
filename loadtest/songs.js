import http from 'k6/http';
import { Trend, Rate } from 'k6/metrics';

// Target: k6 run -e BASE_URL=http://localhost:8080 ... to bypass nginx.
const url = __ENV.BASE_URL || 'http://localhost';

// Label this run: k6 run -e LABEL="3 backends + redis" songs.js
const LABEL = __ENV.LABEL || 'unlabelled';

// open() only works during init, so read the existing results here and append to
// them in handleSummary. Without this each run would overwrite the last.
const PREVIOUS = (() => {
    try {
        return open('./results.md').trimEnd();
    } catch {
        return '';
    }
})();

const songsTime = new Trend('songs_time');
const songsErr = new Rate('songs_errors');
const coverTime = new Trend('cover_time');
const coverErr = new Rate('cover_errors');
const streamTime = new Trend('stream_time');
const streamErr = new Rate('stream_errors');

export const options = {
    vus: 20, // Number of virtual users
    duration: '30s', // Duration of the test
    thresholds: {
        songs_time: ['p(95)<500'],
        stream_time: ['p(95)<500'],
        songs_errors: ['rate<0.01'],
        stream_errors: ['rate<0.01'],
        cover_errors: ['rate<0.01'],
    },
};

// Runs once before the test: read the real songs so ids are never hard-coded.
export function setup() {
    const songs = http.get(`${url}/api/songs`).json();
    return {
        ids: songs.map((s) => s.id),
        coverIds: songs.filter((s) => s.hasCover).map((s) => s.id),
    };
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];

export default function (data) {
    let r = http.get(`${url}/api/songs`);
    songsTime.add(r.timings.duration);
    songsErr.add(r.status >= 400);

    // Only songs that have embedded cover art return 200 here.
    if (data.coverIds.length > 0) {
        r = http.get(`${url}/api/songs/${pick(data.coverIds)}/cover`);
        coverTime.add(r.timings.duration);
        coverErr.add(r.status >= 400);
    }

    r = http.get(`${url}/api/songs/${pick(data.ids)}/stream`, { headers: { 'Range': 'bytes=0-1048575' } });
    streamTime.add(r.timings.duration);
    streamErr.add(r.status != 206);

}

export function handleSummary(data) {
    const secs = data.state.testRunDurationMs / 1000;
    const vus = data.metrics.vus_max ? data.metrics.vus_max.values.max : '?';
    const date = new Date().toISOString().slice(0, 16).replace('T', ' ');

    const row = (name, t, e) => {
        if (!data.metrics[t]) return `| ${LABEL} | ${name} | ${vus} | n/a | n/a | n/a | ${date} |`;
        // Trend values have no count; the matching Rate has one sample per request.
        const count = data.metrics[e].values.passes + data.metrics[e].values.fails;
        const rps = (count / secs).toFixed(1);
        const p95 = data.metrics[t].values['p(95)'].toFixed(0);
        const err = (data.metrics[e].values.rate * 100).toFixed(1);
        return `| ${LABEL} | ${name} | ${vus} | ${rps} | ${p95} ms | ${err}% | ${date} |`;
    };

    const rows = [
        row('Songs', 'songs_time', 'songs_errors'),
        row('Cover', 'cover_time', 'cover_errors'),
        row('Stream', 'stream_time', 'stream_errors'),
    ].join('\n');

    const header = [
        '| Run | Endpoint | VUs | RPS | P95 | Errors | When |',
        '|-----|----------|-----|-----|-----|--------|------|',
    ].join('\n');

    // Append under the existing table, or start one if results.md is empty.
    const file = PREVIOUS ? `${PREVIOUS}\n${rows}\n` : `${header}\n${rows}\n`;

    return {
        'results.md': file,
        stdout: `\n${header}\n${rows}\n`,
    };
}
