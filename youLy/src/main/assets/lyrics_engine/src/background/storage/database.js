// ==================================================================================================
// DUMMY DATABASE (Fixes WebView Hangs)
// ==================================================================================================

class DummyDatabase {
  constructor() {}

  async open() { return {}; }
  async get(key) { return null; } // Always return null (Not found)
  async set(data) { return true; } // Pretend we saved it
  async getAll() { return []; }
  async delete(key) { return true; }
  async clear() { return true; }
  async estimateSize() { return { sizeKB: 0, count: 0 }; }
}

// Export dummy instances to satisfy imports
export const lyricsDB = new DummyDatabase();
export const translationsDB = new DummyDatabase();
export const localLyricsDB = new DummyDatabase();
