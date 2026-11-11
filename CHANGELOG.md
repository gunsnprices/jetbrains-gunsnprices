<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Gun & Ammo Title Parser Changelog

## [Unreleased]

## [0.1.1] - 2026-11-11

### Fixed

- Cost per Round: the price and rounds fields opened only a few characters wide. They now show their whole text at the dialog's default size.
- Settings | Tools | Gun & Ammo Title Parser: the explanation under each option was cut off on the right. It now wraps to fit the page.

## [0.1.0] - 2026-11-11

### Added

- Parse Gun or Ammo Title: parses the selected text, the string literal at the caret or the current line, and shows the brand, model, generation, calibers, magazine capacity, barrel length, rounds per box, price and cost per round in a popup. Copy as JSON.
- Several selected lines are parsed into a table that can be copied as JSON or CSV.
- The same parse as an intention (Alt+Enter) on string literals that look like a gun or ammo title, with a preview.
- Cost per Round: price and round count, or price and title.
- Normalize Caliber: replaces caliber text such as "9mm Luger" with its standard name (293 calibers).
- Caliber Lookup tool window: searches the caliber list by name and by the spellings stores use.
- Settings page (Settings | Tools | Gun & Ammo Title Parser): links to GunsNPrices caliber price pages (off by default), plain "x" in caliber names.
- Built on the gunsnprices Java library 0.1.0. Works offline, no telemetry.
