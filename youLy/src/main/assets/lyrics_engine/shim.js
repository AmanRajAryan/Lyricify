import { LyricsPlusRenderer } from './src/modules/lyrics/lyricsRenderer.js';
import { MessageHandler } from './src/background/core/messageHandler.js'; 

// --- 1. MOCK ENVIRONMENT ---
window.t = (key) => key; 
window.DOMPurify = { sanitize: (html) => html }; 

// --- 2. STICKY SMOOTH CLOCK ---
const SmoothClock = {
    baseSysTime: 0,
    baseSongTime: 0,
    isPlaying: false,

    sync: (newSongTime) => {
        const now = performance.now();
        if (!SmoothClock.isPlaying) {
            SmoothClock.baseSysTime = now;
            SmoothClock.baseSongTime = newSongTime;
            SmoothClock.isPlaying = true;
            return;
        }
        // Sync only if drift is > 200ms to prevent jitter
        const currentProjected = SmoothClock.baseSongTime + (now - SmoothClock.baseSysTime) / 1000;
        const drift = Math.abs(newSongTime - currentProjected);
        if (drift > 0.2) {
            SmoothClock.baseSysTime = now;
            SmoothClock.baseSongTime = newSongTime;
        }
    },

    getTime: () => {
        if (!SmoothClock.isPlaying) return 0;
        const now = performance.now();
        return SmoothClock.baseSongTime + ((now - SmoothClock.baseSysTime) / 1000);
    }
};

// --- 3. STATE MANAGEMENT ---
let rendererInstance = null;
let cachedLyricsData = null;     // Stores the fetched lyrics
let isSearchOnlyMode = false;    // Flag: "Don't render yet, just fetch"
let lastSongId = "";             // To prevent re-fetching the same song

function initRenderer() {
    console.log("[Shim] Initializing LyricsPlusRenderer...");
    try {
        rendererInstance = new LyricsPlusRenderer({
            patchParent: '#lyrics-parent', 
            player: '#dummy-player',       
            getCurrentTime: SmoothClock.getTime, 
            disableNativeTick: false, 
            selectors: [] 
        });
        window.renderer = rendererInstance;
        console.log("[Shim] Renderer initialized.");

        // --- GLOW FIX (2x Intensity) ---
        const style = document.createElement('style');
        style.innerHTML = `
            @keyframes grow-dynamic {
                0% { transform: matrix3d(var(--min-scale), 0, 0, 0, 0, var(--min-scale), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1); filter: drop-shadow(0 0 0 rgba(255, 255, 255, 0)); }
                25%, 30% { transform: matrix3d(var(--max-scale), 0, 0, 0, 0, var(--max-scale), 0, 0, 0, 0, 1, 0, var(--char-offset-x, 0), var(--translate-y-peak, -2%), 0, 1); filter: drop-shadow(0 0 0.2em rgba(255, 255, 255, 0.8)); }
                100% { transform: matrix3d(var(--min-scale), 0, 0, 0, 0, var(--min-scale), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1); }
            }
        `;
        document.head.appendChild(style);

        // --- CLICK TO SEEK LISTENER ---
const parent = document.querySelector('#lyrics-parent');
if (parent) {
    parent.addEventListener('click', (e) => {
        const lineElement = e.target.closest('.lyrics-line');
        
        if (lineElement && window.AndroidBridge && window.AndroidBridge.seekTo) {
            let seekTime = -1;
            
            // Strategy 1: data-time attribute
            const dataTime = lineElement.getAttribute('data-time');
            if (dataTime) seekTime = parseFloat(dataTime);
            
            // Strategy 2: data-start-time attribute
            if (seekTime < 0) {
                const dataStartTime = lineElement.getAttribute('data-start-time');
                if (dataStartTime) seekTime = parseFloat(dataStartTime);
            }
            
            // Strategy 3: Array Index Fallback
            if (seekTime < 0 && rendererInstance && rendererInstance.lyrics) {
                const allLines = Array.from(document.querySelectorAll('.lyrics-line'));
                const index = allLines.indexOf(lineElement);
                if (index >= 0 && rendererInstance.lyrics[index]) {
                    seekTime = rendererInstance.lyrics[index].startTime;
                }
            }

            if (seekTime >= 0) {
                window.AndroidBridge.seekTo(String(Math.round(seekTime * 1000)));
                SmoothClock.sync(seekTime); 
            }
        }
    });
}

    } catch (e) {
        console.error("[Shim] Failed to init renderer:", e);
    }
}

// --- 4. API BRIDGE (INTERCEPTOR) ---
window.LyricsPlusAPI = {
    cleanupLyrics: () => { 
        if (rendererInstance) rendererInstance.cleanupLyrics();
        SmoothClock.isPlaying = false;
        cachedLyricsData = null; 
        lastSongId = "";
    },

    // This is called by the bundle when it finishes fetching/parsing
    displayLyrics: (lyrics, songInfo, mode, settings) => { 
        if (!rendererInstance) initRenderer();
        
        // INTERCEPTION LOGIC
        if (isSearchOnlyMode) {
            console.log("[Shim] Search finished. Caching data (Background Mode).");
            cachedLyricsData = { lyrics, songInfo, mode, settings }; // Save for later
            return; // STOP! Do not render yet.
        }

        // RENDER LOGIC
        console.log("[Shim] Rendering lyrics now.");
        renderInternal(lyrics, songInfo, mode, settings);
    },
    displaySongNotFound: () => rendererInstance?.displaySongNotFound(),
    displaySongError: () => rendererInstance?.displaySongError(),
    t: window.t
};

// Helper to actually draw the screen
function renderInternal(lyrics, songInfo, mode, settings) {
    const container = document.getElementById('lyrics-plus-container');
    if (container) {
        const size = settings.fontSize || 26; 
        container.style.setProperty('--lyplus-font-size-base', size + 'px');
        container.style.display = 'block';
        container.style.height = '100vh';
    }

    rendererInstance.displayLyrics(
        lyrics, 
        songInfo, 
        mode || 'none', 
        settings, 
        window.fetchAndDisplayLyrics, 
        () => {}, 
        settings.largerTextMode || 'lyrics', 
        0
    );
}

// --- 5. ANDROID INTERFACE ---
window.AndroidAPI = {
    // A. BACKGROUND FETCH (Call this when song starts)
    searchSong: (title, artist, album, duration) => {
        const uniqueId = title + artist;
        if (uniqueId === lastSongId) return; // Ignore duplicate calls
        
        lastSongId = uniqueId;
        cachedLyricsData = null; // Clear old cache
        isSearchOnlyMode = true; // ENABLE BACKGROUND MODE

        const safeAlbum = album || "";
        const safeDuration = duration || 0;
        const songInfo = { title, artist, album: safeAlbum, duration: safeDuration, id: uniqueId, source: "Android" };

        if (rendererInstance) rendererInstance.cleanupLyrics();
        
        // Start the fetch (Result hits LyricsPlusAPI.displayLyrics above)
        if (window.fetchAndDisplayLyrics) window.fetchAndDisplayLyrics(songInfo, true);
    },

    // B. FOREGROUND RENDER (Call this when user opens UI)
    showSong: () => {
        isSearchOnlyMode = false; // DISABLE BACKGROUND MODE

        if (cachedLyricsData) {
            // Case 1: Search already finished -> Render Instantly
            console.log("[Shim] Cache hit! Rendering instantly.");
            renderInternal(
                cachedLyricsData.lyrics, 
                cachedLyricsData.songInfo, 
                cachedLyricsData.mode, 
                window.currentSettings
            );
        } else {
            // Case 2: Search still running -> It will render automatically when it hits displayLyrics
            console.log("[Shim] Cache miss. Waiting for search to finish...");
        }
    },

    // C. LEGACY LOAD (Immediate)
    loadSong: (title, artist, album, duration) => {
        isSearchOnlyMode = false;
        const uniqueId = title + artist;
        lastSongId = uniqueId;
        
        const safeAlbum = album || "";
        const safeDuration = duration || 0;
        const songInfo = { title, artist, album: safeAlbum, duration: safeDuration, id: uniqueId, source: "Android" };
        
        if (rendererInstance) rendererInstance.cleanupLyrics();
        if (window.fetchAndDisplayLyrics) window.fetchAndDisplayLyrics(songInfo, true);
    },

    updateTime: (timeMs) => {
        SmoothClock.sync(timeMs / 1000);
    }
};

// --- 6. SETTINGS & NETWORK ---
window.pBrowser = {
    runtime: { sendMessage: (m) => new Promise(r => MessageHandler.handle(m, {}, r)) },
    getURL: (p) => p
};

window.currentSettings = {
    isEnabled: true,
    lyricsProvider: 'kpoe',
    wordByWord: true, 
    theme: 'dark',
    translationEnabled: false,
    romanizationEnabled: false,
    largerTextMode: 'lyrics',
    useSponsorBlock: false,
    lightweight: false, 
    blurInactive: true, 
    fontSize: 32,       
    customCSS: ''
};

window.pendingRequests = {};
window.fetch = async (url, options = {}) => {
    const reqId = Math.random().toString(36).substring(7);
    return new Promise((resolve, reject) => {
        window.pendingRequests[reqId] = { resolve, reject };
        if (window.AndroidBridge) window.AndroidBridge.performNetworkRequest(url, options.method||'GET', options.body||'', reqId);
        else reject(new Error("AndroidBridge not found"));
    });
};
window.handleNativeResponse = (reqId, isSuccess, status, content) => {
    const r = window.pendingRequests[reqId];
    if (r) {
        delete window.pendingRequests[reqId];
        isSuccess ? r.resolve({ ok: status>=200, json: ()=>Promise.resolve(JSON.parse(content)), text: ()=>Promise.resolve(content) }) : r.reject(new TypeError("Net Error"));
    }
};

console.log("Shim loaded. Split Search/Render Mode.");