# Ritmi biorhythms

A personal biorhythms calculator as an installable web app (PWA). Works offline; all data stays on the device.

- Chart with 15-day window, physical (23), emotional (28), intellectual (33) and intuitive (38) rhythms, critical-day markers
- Three calculation methods: standard, midday count (days + 0.5), precise fractional periods
- Daily reading with share, compatibility between two people, monthly calendar with critical days and best/quietest days
- Multiple people, backup/restore to a JSON file, export critical days to a calendar (.ics)
- English and Latvian, six colour themes, light/dark/system
- Best-effort daily notification (Android Chrome, installed app)

Plain HTML/CSS/JS, no build step. Hosted with GitHub Pages.

## Android app

`android/` is a small WebView shell around the same `index.html` (offline, backups saved to the phone, daily 8:00 notification).
GitHub Actions builds `Ritmi.apk` on every push and publishes it under Releases.
