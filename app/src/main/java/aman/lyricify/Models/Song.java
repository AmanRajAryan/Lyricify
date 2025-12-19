package aman.lyricify;

import org.json.JSONObject;

public class Song {
    private String id, songName, artistName, albumName, artwork, releaseDate, duration, contentRating;
    private int matchScore = 0;
    private JSONObject fullTrackData; // Store complete track data from API

    public Song(String id, String songName, String artistName, String albumName, String artwork, String releaseDate, String duration, String contentRating) {
        this.id = id;
        this.songName = songName;
        this.artistName = artistName;
        this.albumName = albumName;
        this.artwork = artwork;
        this.releaseDate = releaseDate;
        this.duration = duration;
        this.contentRating = contentRating;
    }

    public String getId() { return id; }
    public String getSongName() { return songName; }
    public String getArtistName() { return artistName; }
    public String getAlbumName() { return albumName; }
    public String getArtwork() { return artwork; }
    public String getReleaseDate() { return releaseDate; }
    public String getDuration() { return formattedDuration(); }
    public String getContentRating() { return contentRating; }
    public int getMatchScore() { return matchScore; }
    public JSONObject getFullTrackData() { return fullTrackData; }

    public void setMatchScore(int matchScore) {
        this.matchScore = Math.max(0, Math.min(100, matchScore));
    }
    
    public void setFullTrackData(JSONObject fullTrackData) {
        this.fullTrackData = fullTrackData;
    }

    public void calculateMatchScore(String query) {
        if (query == null || query.trim().isEmpty()) {
            matchScore = 100;
            return;
        }

        String queryLower = query.toLowerCase().trim();
        String[] queryWords = queryLower.split("\\s+");
        
        String songLower = songName.toLowerCase();
        String artistLower = artistName.toLowerCase();
        String[] songWords = songLower.split("\\s+");
        String[] artistWords = artistLower.split("\\s+");

        boolean songExactMatch = songLower.equals(queryLower);
        boolean artistExactMatch = artistLower.equals(queryLower);
        
        if (songExactMatch && artistExactMatch) {
            matchScore = 100;
            return;
        }

        int songScore = 0;
        int artistScore = 0;
        boolean songMatched = false;
        boolean artistMatched = false;

        if (queryLower.contains(songLower) || songExactMatch) {
            songScore = 50;
            songMatched = true;
        }

        if (queryLower.contains(artistLower) || artistExactMatch) {
            artistScore = 50;
            artistMatched = true;
        }

        if (songMatched && artistMatched) {
            matchScore = 100;
            return;
        }

        if (!songMatched) {
            int songWordMatchCount = 0;
            for (String songWord : songWords) {
                if (songWord.length() >= 2) {
                    for (String queryWord : queryWords) {
                        if (queryWord.equals(songWord)) {
                            songWordMatchCount++;
                            break;
                        }
                    }
                }
            }
            
            if (songWords.length > 0) {
                songScore = (songWordMatchCount * 50) / songWords.length;
            }
        }

        if (!artistMatched) {
            int artistWordMatchCount = 0;
            for (String artistWord : artistWords) {
                if (artistWord.length() >= 2) {
                    for (String queryWord : queryWords) {
                        if (queryWord.equals(artistWord)) {
                            artistWordMatchCount++;
                            break;
                        }
                    }
                }
            }
            
            if (artistWords.length > 0) {
                artistScore = (artistWordMatchCount * 50) / artistWords.length;
            }
        }

        int totalScore = 0;
        
        if (songScore > 0 && artistScore > 0) {
            totalScore = songScore + artistScore;
        } else if (songScore > 0) {
            totalScore = songScore + (artistScore / 2);
        } else if (artistScore > 0) {
            totalScore = (artistScore / 2);
        }

        matchScore = Math.min(100, totalScore);
    }

    private String formattedDuration() {
        int durationMillis = Integer.parseInt(duration);
        int totalSeconds = durationMillis / 1000;

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + ":" + formatTwoDigits(minutes) + ":" + formatTwoDigits(seconds);
        } else {
            return formatTwoDigits(minutes) + ":" + formatTwoDigits(seconds);
        }
    }

    private String formatTwoDigits(int number) {
        return number < 10 ? "0" + number : String.valueOf(number);
    }
}