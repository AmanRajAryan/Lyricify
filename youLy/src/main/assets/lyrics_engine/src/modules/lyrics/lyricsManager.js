/* =================================================================
   STATE VARIABLES
   ================================================================= */

// [FIX] Don't init AudioContext immediately, it can hang WebView
let audioCtx = null;

let currentFetchMediaId = null;
let currentDisplayMode = 'none'; 
let lastProcessedDisplayMode = 'none'; 

let lastKnownSongInfo = null;
let lastFetchedLyrics = null;

let lyricsFetchDebounceTimer = null;
let lastRequestedSongKey = null;
const DEBOUNCE_TIME_MS = 200;

/* =================================================================
   HELPER FUNCTIONS
   ================================================================= */

function combineLyricsData(baseLyrics, translation, romanization) {
  const combinedLyrics = JSON.parse(JSON.stringify(baseLyrics)); 

  const translationData = translation?.data;
  const romanizationData = romanization?.data;

  combinedLyrics.data = combinedLyrics.data.map((line, index) => {
    const translatedLine = translationData?.[index];
    const romanizedLine = romanizationData?.[index];
    let updatedLine = { ...line };

    if (translatedLine?.translatedText) {
      updatedLine.translatedText = translatedLine.translatedText;
    }

    if (romanizedLine) {
      if (baseLyrics.type === "Word" && romanizedLine.chunk?.length > 0 && updatedLine.syllabus?.length > 0) {
        updatedLine.syllabus = updatedLine.syllabus.map((syllable, sylIndex) => {
          const romanizedSyllable = romanizedLine.chunk[sylIndex];
          return {
            ...syllable,
            romanizedText: romanizedSyllable?.text || syllable.text
          };
        });
      }
      else if (romanizedLine.text) {
         updatedLine.romanizedText = romanizedLine.text;
      }
    }
    return updatedLine;
  });

  return combinedLyrics;
}

function determineFinalDisplayMode(intendedMode, hasTranslation, hasRomanization) {
  if (intendedMode === 'both') {
    if (hasTranslation && hasRomanization) return 'both';
    if (hasTranslation) return 'translate';
    if (hasRomanization) return 'romanize';
  }
  if (intendedMode === 'translate' && hasTranslation) {
    return 'translate';
  }
  if (intendedMode === 'romanize' && hasRomanization) {
    return 'romanize';
  }
  return 'none'; 
}


/* =================================================================
   CORE LOGIC: FETCHING AND PROCESSING
   ================================================================= */

async function fetchAndDisplayLyrics(currentSong, isNewSong = false, forceReload = false) {
  console.log("fetchAndDisplayLyrics called for " + currentSong.title);
  
  const songKey = `${currentSong.title}-${currentSong.artist}-${currentSong.album}`;
  
  if (lyricsFetchDebounceTimer && lastRequestedSongKey === songKey && !forceReload && currentDisplayMode === lastProcessedDisplayMode) {
    return;
  }
  clearTimeout(lyricsFetchDebounceTimer);
  lyricsFetchDebounceTimer = setTimeout(() => {
    lyricsFetchDebounceTimer = null;
    lastRequestedSongKey = null;
  }, DEBOUNCE_TIME_MS);
  lastRequestedSongKey = songKey;

  const localCurrentFetchMediaId = currentSong.videoId || currentSong.songId;
  currentFetchMediaId = localCurrentFetchMediaId;

  if (window.LyricsPlusAPI && LyricsPlusAPI.cleanupLyrics) {
      LyricsPlusAPI.cleanupLyrics();
  }

  try {
    let effectiveMode = currentDisplayMode;
    // Safe access to currentSettings
    const settings = window.currentSettings || {};
    
    if (isNewSong) {
      const { translationEnabled, romanizationEnabled } = settings;
      if (translationEnabled && romanizationEnabled) effectiveMode = 'both';
      else if (translationEnabled) effectiveMode = 'translate';
      else if (romanizationEnabled) effectiveMode = 'romanize';
      else effectiveMode = 'none';
      currentDisplayMode = effectiveMode;
    }

    // --- FETCH BASE LYRICS ---
    console.log("Sending FETCH_LYRICS message...");
    const originalLyricsResponse = await pBrowser.runtime.sendMessage({
      type: 'FETCH_LYRICS',
      songInfo: currentSong,
      forceReload: forceReload
    });
    
    // [DEBUG] PRINT THE FULL DATA
    console.log("FETCH_LYRICS response received: " + JSON.stringify(originalLyricsResponse));

    if (currentFetchMediaId !== localCurrentFetchMediaId) {
        console.log("Song ID changed. Aborting render.");
        return;
    }

    if (!originalLyricsResponse || !originalLyricsResponse.success) {
      console.warn('Failed to fetch original lyrics. Error:', originalLyricsResponse?.error);
      if (LyricsPlusAPI.displaySongNotFound) LyricsPlusAPI.displaySongNotFound();
      return;
    }

    console.log("Success! Processing lyrics type: " + originalLyricsResponse.lyrics?.type);
    let baseLyrics = originalLyricsResponse.lyrics;

    // --- FETCH ADDITIONAL DATA ---
    const htmlLang = document.documentElement.getAttribute('lang') || 'en';
    const promises = [];
    
    // [DEBUG] Log Translation Config
    const needsTranslation = effectiveMode === 'translate' || effectiveMode === 'both';
    const needsRomanization = effectiveMode === 'romanize' || effectiveMode === 'both' || settings.largerTextMode === "romanization";
    console.log(`Checking Translations... Needs Trans: ${needsTranslation}, Needs Rom: ${needsRomanization}`);

    if (needsTranslation) {
      promises.push(pBrowser.runtime.sendMessage({
        type: 'TRANSLATE_LYRICS', action: 'translate', songInfo: currentSong, targetLang: htmlLang
      }));
    } else {
      promises.push(Promise.resolve(null));
    }

    if (needsRomanization) {
      promises.push(pBrowser.runtime.sendMessage({
        type: 'TRANSLATE_LYRICS', action: 'romanize', songInfo: currentSong, targetLang: htmlLang
      }));
    } else {
      promises.push(Promise.resolve(null));
    }
    
    console.log("Awaiting translations...");
    const [translationResponse, romanizationResponse] = await Promise.all(promises);
    console.log("Translations done.");

    if (currentFetchMediaId !== localCurrentFetchMediaId) return;

    const hasTranslation = translationResponse?.success && translationResponse.translatedLyrics;
    const hasRomanization = romanizationResponse?.success && romanizationResponse.translatedLyrics;

    // --- COMBINE & RENDER ---
    console.log("Combining lyrics data...");
    var lyricsObjectToDisplay = combineLyricsData(
      baseLyrics,
      hasTranslation ? translationResponse.translatedLyrics : null,
      hasRomanization ? romanizationResponse.translatedLyrics : null
    );
    
    const finalDisplayModeForRenderer = determineFinalDisplayMode(effectiveMode, hasTranslation, hasRomanization);

    if (lyricsObjectToDisplay.type === "Word" && !settings.wordByWord) {
      lyricsObjectToDisplay = convertWordLyricsToLine(lyricsObjectToDisplay);
    }
    
    lyricsObjectToDisplay.type = lyricsObjectToDisplay.type === "Line" ? "Line" : "Word";
    lastFetchedLyrics = lyricsObjectToDisplay;
    
    if (LyricsPlusAPI.displayLyrics) {
      // Lazy init AudioContext only when needed
      if (!audioCtx) {
          try { audioCtx = new AudioContext(); } catch(e) { console.warn("AudioContext init failed", e); }
      }
      
      console.log("Calling LyricsPlusAPI.displayLyrics...");
      LyricsPlusAPI.displayLyrics(
        lyricsObjectToDisplay,
        currentSong,
        finalDisplayModeForRenderer,
        settings,
        fetchAndDisplayLyrics,
        setCurrentDisplayModeAndRender,
        settings.largerTextMode,
        audioCtx?.outputLatency || 0
      );
      console.log("displayLyrics called.");
    } 
    
    lastKnownSongInfo = currentSong;
    lastProcessedDisplayMode = finalDisplayModeForRenderer;

  } catch (error) {
    console.error('CRASH in fetchAndDisplayLyrics:', error);
    if (LyricsPlusAPI.displaySongError) LyricsPlusAPI.displaySongError();
  }
}

function setCurrentDisplayModeAndRender(mode, songInfoForRefetch) {
  currentDisplayMode = mode;
  const songToRefetch = songInfoForRefetch || lastKnownSongInfo;

  if (songToRefetch) {
    fetchAndDisplayLyrics(songToRefetch, false, false);
  } else {
    currentDisplayMode = 'none';
    if (LyricsPlusAPI.displaySongError) LyricsPlusAPI.displaySongError();
  }
};

function convertWordLyricsToLine(lyrics) {
  if (lyrics.type !== "Word") return lyrics;
  const lines = lyrics.data.map(line => ({ ...line, syllables: [] }));
  return {
    type: "Line",
    data: lines,
    metadata: lyrics.metadata,
    ignoreSponsorblock: lyrics.ignoreSponsorblock
  };
}

export { fetchAndDisplayLyrics };
