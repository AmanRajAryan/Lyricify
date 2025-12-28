import { LyricsPlusRenderer } from './src/modules/lyrics/lyricsRenderer.js';
import { MessageHandler } from './src/background/core/messageHandler.js'; 

// =========================================================
//  USER CONFIGURATION
// =========================================================
const BUTTON_POS_BOTTOM = '12em';   // Distance from bottom
const BUTTON_POS_RIGHT  = '1.5em'; // Distance from right
const FONT_SIZE_BASE    = 40;      // Font size
const FONT_WEIGHT       = 900;     // Boldness (800 or 900)
// =========================================================

// --- 1. MOCK ENVIRONMENT ---
window.t = (key) => key; 
window.DOMPurify = { sanitize: (html) => html }; 

// --- 2. STICKY SMOOTH CLOCK ---
const SmoothClock = {
    baseSysTime: 0,
    baseSongTime: 0,
    isPaused: true,
    pausedTime: 0,

    sync: (newSongTime) => {
        const now = performance.now();
        if (SmoothClock.isPaused) {
            SmoothClock.pausedTime = newSongTime;
            return;
        }
        const currentProjected = SmoothClock.baseSongTime + (now - SmoothClock.baseSysTime) / 1000;
        const drift = Math.abs(newSongTime - currentProjected);
        if (drift > 0.2) {
            SmoothClock.baseSysTime = now;
            SmoothClock.baseSongTime = newSongTime;
        }
    },

    setPlaying: (isPlaying) => {
        const now = performance.now();
        if (isPlaying) {
            if (SmoothClock.isPaused) {
                SmoothClock.isPaused = false;
                SmoothClock.baseSongTime = SmoothClock.pausedTime;
                SmoothClock.baseSysTime = now;
            }
        } else {
            if (!SmoothClock.isPaused) {
                SmoothClock.pausedTime = SmoothClock.getTime();
                SmoothClock.isPaused = true;
            }
        }
    },

    getTime: () => {
        if (SmoothClock.isPaused) return SmoothClock.pausedTime;
        const now = performance.now();
        return SmoothClock.baseSongTime + ((now - SmoothClock.baseSysTime) / 1000);
    }
};

// --- 3. STATE MANAGEMENT ---
let rendererInstance = null;
let cachedLyricsData = null;
let currentActiveLyrics = null; 
let isSearchOnlyMode = false;    
let lastSongId = "";             

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

        // --- VISUAL BOOST PATCH ---
        const style = document.createElement('style');
        style.innerHTML = `
            /* 1. BOOST GLOW */
            @keyframes grow-dynamic {
                0% { transform: matrix3d(var(--min-scale), 0, 0, 0, 0, var(--min-scale), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1); filter: drop-shadow(0 0 0 rgba(255, 255, 255, 0)); }
                25%, 30% { 
                    transform: matrix3d(var(--max-scale), 0, 0, 0, 0, var(--max-scale), 0, 0, 0, 0, 1, 0, var(--char-offset-x, 0), var(--translate-y-peak, -2%), 0, 1); 
                    filter: drop-shadow(0 0 0.2em rgba(255, 255, 255, 0.8)); 
                }
                100% { transform: matrix3d(var(--min-scale), 0, 0, 0, 0, var(--min-scale), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1); }
            }

            /* 2. SUPER BOLD FONT */
            .lyrics-line {
                font-weight: ${FONT_WEIGHT} !important; 
            }

            /* 3. PURE WHITE PALETTE */
            #lyrics-plus-container {
                --lyplus-lyrics-pallete: #ffffff !important; 
                --lyplus-text-primary: #ffffff !important;
                -webkit-font-smoothing: antialiased;
            }
            .lyrics-line.active {
                opacity: 1 !important;
            }

            /* 4. BUTTON POSITION */
            #lyrics-plus-buttons-wrapper {
                display: flex !important;
                visibility: visible !important;
                opacity: 1 !important;
                z-index: 9999;
                bottom: ${BUTTON_POS_BOTTOM} !important;
                right: ${BUTTON_POS_RIGHT} !important;
                position: fixed !important; 
            }

            /* 5. FIX DROPDOWN MENU POSITION */
            #lyrics-plus-translation-dropdown {
                top: auto !important;       
                bottom: 120% !important;    
                left: auto !important;      
                right: 0 !important;        
                transform: none !important; 
                min-width: 160px;
                background-color: rgba(20, 20, 20, 0.95) !important;
                border: 1px solid rgba(255,255,255,0.2) !important;
            }
        `;
        document.head.appendChild(style);

        // --- CLICK TO SEEK ---
        const parent = document.querySelector('#lyrics-parent');
        if (parent) {
            parent.addEventListener('click', (e) => {
                const lineElement = e.target.closest('.lyrics-line');
                if (lineElement && window.AndroidBridge && window.AndroidBridge.seekTo) {
                    let seekTime = -1;
                    const dataTime = lineElement.getAttribute('data-time');
                    if (dataTime) seekTime = parseFloat(dataTime);
                    
                    if (seekTime < 0) {
                        const dataStartTime = lineElement.getAttribute('data-start-time');
                        if (dataStartTime) seekTime = parseFloat(dataStartTime);
                    }
                    
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

// --- 4. API BRIDGE ---
window.LyricsPlusAPI = {
    cleanupLyrics: () => { 
        if (rendererInstance) rendererInstance.cleanupLyrics();
        SmoothClock.isPaused = true;
        cachedLyricsData = null; 
        currentActiveLyrics = null;
        lastSongId = "";
    },

    displayLyrics: (lyrics, songInfo, mode, settings) => { 
        if (!rendererInstance) initRenderer();
        
        if (isSearchOnlyMode) {
            console.log("[Shim] Search finished. Caching data.");
            cachedLyricsData = { lyrics, songInfo, mode, settings };
            return;
        }

        renderInternal(lyrics, songInfo, mode, settings);
    },
    displaySongNotFound: () => rendererInstance?.displaySongNotFound(),
    displaySongError: () => rendererInstance?.displaySongError(),
    t: window.t
};

function renderInternal(lyrics, songInfo, mode, settings) {
    const container = document.getElementById('lyrics-plus-container');
    if (container) {
        const size = settings.fontSize || FONT_SIZE_BASE; 
        container.style.setProperty('--lyplus-font-size-base', size + 'px');
        container.style.display = 'block';
        container.style.height = '100vh';
    }

    currentActiveLyrics = lyrics;

    rendererInstance.displayLyrics(
        lyrics, 
        songInfo, 
        mode || 'none', 
        settings, 
        window.fetchAndDisplayLyrics, 
        // 6th Argument: Mode Switch Callback
        (newMode, songInfoArg) => {
            console.log("[Shim] Mode switch requested: " + newMode);
            const targetInfo = songInfoArg || songInfo;

            // 1. Update GLOBAL settings so the fetcher knows what to get
            if (newMode === 'translate') {
                window.currentSettings.translationEnabled = true;
                window.currentSettings.romanizationEnabled = false;
            } else if (newMode === 'romanize') {
                window.currentSettings.translationEnabled = false;
                window.currentSettings.romanizationEnabled = true;
            } else if (newMode === 'both') {
                window.currentSettings.translationEnabled = true;
                window.currentSettings.romanizationEnabled = true;
            } else {
                window.currentSettings.translationEnabled = false;
                window.currentSettings.romanizationEnabled = false;
            }

            // 2. TRIGGER RE-FETCH (The missing piece!)
            // This forces the lyrics manager to download the translation.
            if (window.fetchAndDisplayLyrics && targetInfo) {
                console.log("[Shim] Refetching with new settings...");
                window.fetchAndDisplayLyrics(targetInfo, true); // true = force reload
            } else {
                // Fallback if fetcher is missing
                if (rendererInstance && currentActiveLyrics) {
                    rendererInstance.updateDisplayMode(currentActiveLyrics, newMode, window.currentSettings);
                }
            }
        }, 
        settings.largerTextMode || 'lyrics', 
        0
    );
}

// --- 5. ANDROID INTERFACE ---
window.AndroidAPI = {
    searchSong: (title, artist, album, duration) => {
        const uniqueId = title + artist;
        if (uniqueId === lastSongId) return;
        
        lastSongId = uniqueId;
        cachedLyricsData = null; 
        isSearchOnlyMode = true; 

        if (!rendererInstance) initRenderer();
        if (rendererInstance) rendererInstance.cleanupLyrics(); 

        const safeAlbum = album || "";
        const safeDuration = duration || 0;
        const songInfo = { title, artist, album: safeAlbum, duration: safeDuration, id: uniqueId, source: "Android" };

        if (window.fetchAndDisplayLyrics) window.fetchAndDisplayLyrics(songInfo, true);
    },

    showSong: () => {
        isSearchOnlyMode = false;
        if (cachedLyricsData) {
            console.log("[Shim] Cache hit! Rendering.");
            renderInternal(
                cachedLyricsData.lyrics, 
                cachedLyricsData.songInfo, 
                cachedLyricsData.mode, 
                window.currentSettings
            );
            cachedLyricsData = null; 
        } else {
            console.log("[Shim] Cache miss. Waiting for search.");
        }
    },

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
    },

    setPlaying: (isPlaying) => {
        SmoothClock.setPlaying(isPlaying);
    }
};

// --- 6. SETTINGS ---
window.pBrowser = {
    runtime: { sendMessage: (m) => new Promise(r => MessageHandler.handle(m, {}, r)) },
    getURL: (p) => p
};

window.currentSettings = {
    isEnabled: true,
    lyricsProvider: 'kpoe',
    wordByWord: true, 
    theme: 'dark',
    translationEnabled: false, // Default off
    romanizationEnabled: false,
    largerTextMode: 'lyrics',
    useSponsorBlock: false,
    lightweight: false, 
    blurInactive: true, 
    fontSize: FONT_SIZE_BASE, 
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

console.log(`Shim loaded. Live Re-Fetch Enabled.`);