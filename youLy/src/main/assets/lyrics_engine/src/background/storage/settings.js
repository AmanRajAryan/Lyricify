// ==================================================================================================
// SETTINGS MANAGEMENT (Fixed for Android WebView)
// ==================================================================================================

import { PROVIDERS } from '../constants.js';

export class SettingsManager {
  static async get(keys) {
    // [FIX] 1. Intercept calls and return the Mock Settings from shim.js
    if (typeof window !== 'undefined' && window.currentSettings) {
      return window.currentSettings;
    }

    // 2. Safe check for Browser/Chrome (prevents crash if undefined)
    if (typeof browser !== 'undefined' && browser.storage?.local) {
      return browser.storage.local.get(keys);
    }
    
    // 3. Only use chrome.storage if 'chrome' AND 'storage' actually exist
    if (typeof chrome !== 'undefined' && chrome.storage?.local) {
      return new Promise(resolve => chrome.storage.local.get(keys, resolve));
    }

    // 4. Fallback (e.g. for first load)
    return {};
  }

  static async getLyricsSettings() {
    return this.get({
      'lyricsProvider': PROVIDERS.KPOE,
      'lyricsSourceOrder': 'apple,lyricsplus,musixmatch,spotify,musixmatch-word',
      'customKpoeUrl': '',
      'cacheStrategy': 'aggressive'
    });
  }

  static async getTranslationSettings() {
    return this.get({
      'translationProvider': PROVIDERS.GOOGLE,
      'romanizationProvider': PROVIDERS.GOOGLE,
      'geminiApiKey': '',
      'geminiModel': 'gemini-pro',
      'geminiRomanizationModel': 'gemini-pro',
      'overrideTranslateTarget': false,
      'customTranslateTarget': '',
      'overrideGeminiPrompt': false,
      'customGeminiPrompt': '',
      'overrideGeminiRomanizePrompt': false,
      'customGeminiRomanizePrompt': ''
    });
  }
}
