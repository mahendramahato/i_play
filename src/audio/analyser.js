// Shared Web Audio analyser. The player attaches its <audio> element on the
// first play, and the background visualizer reads frequency data from here.
// One AudioContext per page: browsers only let audio start after a user
// gesture, which is why this is created lazily from the play button.

let context = null;
let analyser = null;
let attachedTo = null;

export function attachAnalyser(audio) {
    if (!audio) return;
    // Routing cross-origin audio through Web Audio makes the browser silence it
    // (CORS protection). In production the audio is same-origin; in local dev
    // (Vite on :5173, API on :80) it isn't, so there the visualizer just stays in
    // its ambient mode instead of muting the player.
    const src = audio.currentSrc || audio.src;
    if (!src || new URL(src, window.location.href).origin !== window.location.origin) return;
    try {
        if (!context) {
            const Ctx = window.AudioContext || window.webkitAudioContext;
            if (!Ctx) return;
            context = new Ctx();
        }
        // createMediaElementSource may only be called once per element.
        if (attachedTo !== audio) {
            const source = context.createMediaElementSource(audio);
            analyser = context.createAnalyser();
            analyser.fftSize = 512;
            analyser.smoothingTimeConstant = 0.82;
            source.connect(analyser);
            analyser.connect(context.destination);
            attachedTo = audio;
        }
        if (context.state === 'suspended') context.resume();
    } catch {
        // Unsupported or blocked: playback still works, the background stays ambient.
    }
}

export function getAnalyser() {
    return context && context.state === 'running' ? analyser : null;
}
