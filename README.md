# Gun & Ammo Title Parser for JetBrains IDEs

Turn gun and ammo product titles into structured data without leaving the editor: brand, model, generation,
caliber, magazine capacity, barrel length, rounds per box and cost per round. Made for people who write scrapers,
product feeds, imports and tests for gun and ammo stores, and who spend their days in code, CSV exports and JSON feeds
full of titles like `G19 GEN5 9MM 15RD FS`.

Works in every IntelliJ-based IDE 2024.3 or newer: IntelliJ IDEA, PyCharm, WebStorm, PhpStorm, GoLand, RubyMine,
Rider, CLion, DataGrip, RustRover and Android Studio.

Put the caret in a string such as

```java
String title = "Glock 19 Gen 5 MOS 9mm 4.02\" 15rd Pistol PA195S203MOS";
```

and press <kbd>Alt+Enter</kbd> > **Parse gun or ammo title**, or right-click > **GunsNPrices** > **Parse Gun or Ammo
Title**. A popup shows:

```text
Glock 19 Gen 5 MOS
Kind:           firearm
Brand:          Glock
Model:          Glock 19 Gen 5 MOS
Family:         Glock 19
Generation:     5
Configuration:  MOS
Caliber:        9×19mm Parabellum (handgun)
Capacity:       15 rounds
Barrel:         4.02 in
                [Copy as JSON]
```

Everything runs on your machine. The plugin makes no network requests, collects no telemetry and needs no account or
API key. The parsing is done by [`com.gunsnprices:gunsnprices`](https://central.sonatype.com/artifact/com.gunsnprices/gunsnprices),
the open source title parser behind the price comparison site [GunsNPrices](https://gunsnprices.com), so you get the
same model and caliber names the site uses to group listings from 40+ US stores.

## Install

<kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > search for "Gun & Ammo Title Parser" >
<kbd>Install</kbd>.

Or download the zip from the [releases](https://github.com/gunsnprices/jetbrains-gunsnprices/releases) and use
<kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install Plugin from Disk...</kbd>.

## Actions

The actions are in the editor context menu and in <kbd>Tools</kbd> under **GunsNPrices**, and in
<kbd>Find Action</kbd> (<kbd>Ctrl+Shift+A</kbd> / <kbd>Cmd+Shift+A</kbd>). None has a shortcut by default; assign one
in <kbd>Settings</kbd> > <kbd>Keymap</kbd> if you use it often.

| Action | What it does |
|---|---|
| **Parse Gun or Ammo Title** | Parses the selection, the string literal at the caret or the current line and shows the result. Several selected lines give a table. |
| **Cost per Round...** | Asks for a price and a round count (or a title) and shows the cost per round |
| **Normalize Caliber** | Replaces the selected caliber text with its standard name |
| **Caliber Lookup** | Opens the Caliber Lookup tool window and searches for the selected text |

### Parse Gun or Ammo Title

What is parsed:

| Where the caret is | Text that is parsed |
|---|---|
| A selection on one line | The selected text |
| A string literal in any language (Java, Kotlin, Python, JavaScript, PHP, Go, JSON, YAML, XML ...) | The string, with escapes such as `\"` resolved |
| A quoted string in a file without a language parser | The text between the quotes |
| A cell in a `.csv` or `.tsv` file | The cell |
| Anything else | The current line, without a list marker (`-`, `*`, `1.`) |

A price in the text (`$18.99`, `USD 18.99`, `18.99 USD`) is taken out of the title and used for the cost per round.
A price in another cell of the same CSV row, or a `"price"` / `"sale_price"` property on the same JSON line, is used
too.

The popup has **Copy as JSON**. The JSON has the keys of the library's `ParseResult.toMap()` (the same shape as the
Python package and the `java -jar gunsnprices.jar` command line) plus `price`:

```json
{
  "title": "Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds",
  "kind": "ammo",
  "brand": "",
  "model": null,
  "caliber": {
    "id": "9x19mmparabellum",
    "name": "9×19mm Parabellum",
    "type": "handgun"
  },
  "calibers": [
    {
      "id": "9x19mmparabellum",
      "name": "9×19mm Parabellum",
      "type": "handgun"
    }
  ],
  "capacity": null,
  "barrel_length": null,
  "rounds": 50,
  "price": 18.99,
  "cost_per_round": 0.3798
}
```

Select several lines to parse each of them into a table, with **Copy as JSON** (an array, each object with a `line`
number) and **Copy as CSV**:

```text
line,title,kind,brand,model,family,generation,configs,features,caliber,caliber_type,calibers,capacity,barrel_in,rounds,price,cost_per_round
2,"Glock 19 Gen 5 MOS 9mm 4.02"" 15rd",firearm,Glock,Glock 19 Gen 5 MOS,Glock 19,5,MOS,,9×19mm Parabellum,handgun,9×19mm Parabellum,15,4.02,,549.99,
3,Federal American Eagle 9mm Luger 115gr FMJ 50 Rounds,ammo,,,,,,,9×19mm Parabellum,handgun,9×19mm Parabellum,,,50,18.99,0.3798
```

Each line is read according to what it looks like: a CSV/TSV row (the cell that parses best is the title, the first
cell that holds only a price is the price), a line with quoted strings (a JSON line, an array element in code), or a
plain title. Lines without a title are skipped. Selections over 2,000 lines show a progress bar and can be cancelled.

### Intention

<kbd>Alt+Enter</kbd> on a string literal that looks like a gun or ammo title offers **Parse gun or ammo title**. The
intention preview already shows the parsed fields; choosing it opens the same popup. In plain text, CSV, TSV and
Markdown files it is offered on any line that looks like a title. Strings and lines that merely contain a number that
could be a caliber (`call me at 308 tomorrow`) are ignored. The intention changes nothing in the file. Turn it off in
<kbd>Settings</kbd> > <kbd>Editor</kbd> > <kbd>Intentions</kbd> > <kbd>GunsNPrices</kbd>.

### Cost per Round

Asks for the price of the box or case and for the number of rounds or a title that contains it (`50 Rounds`,
`1000rd case`, `20/box`). When the caret is on a title with a price, both fields are filled in. The result updates as
you type:

```text
$289.99 for 1000 rounds = $0.29 (29.00¢) per round
```

**Copy** puts `0.29` on the clipboard.

### Normalize Caliber

Select a caliber, or a column of them (one per line, or with several carets), and run **Normalize Caliber**. Without a
selection it works on the string literal at the caret.

| Selected | Becomes |
|---|---|
| `9mm Luger`, `9x19` | `9×19mm Parabellum` |
| `5.56 NATO` | `5.56×45mm NATO` |
| `.223 Remington` | `.223 Rem` |
| `12ga` | `12 Gauge` |
| `300 BLK` | `.300 AAC Blackout` |
| `45 auto` | `.45 ACP` |
| `6.5 CM` | `6.5mm Creedmoor` |

Quotes and spaces around each value are kept. Text that isn't recognized is left as it is, and so is text that is more
than a caliber (a whole title, or `9mm / .380`), so selecting too much never loses data; a message lists what was left.
The change can be undone with <kbd>Ctrl+Z</kbd> / <kbd>Cmd+Z</kbd>.

### Caliber Lookup

A tool window (right side) that searches the 293 calibers by name, by id and by the spellings stores use. The caliber
that the parser reads from your query comes first, so `300 BLK`, `6.5 CM` or `12ga` find the right entry. Each entry
shows its type, id and store spellings, with **Copy Name** and **Insert into Editor**.

## Settings

<kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>Gun & Ammo Title Parser</kbd>

| Setting | Default | |
|---|---|---|
| Link calibers to their price comparison pages on GunsNPrices | off | Shows a link in the result popup and in Caliber Lookup, and adds `url` to JSON and `caliber_url` to CSV |
| Write × as x in caliber names | off | `9x19mm Parabellum` instead of `9×19mm Parabellum`, for feeds and databases that expect plain ASCII |

## What it recognizes

| Field | Example in a title | Result |
|---|---|---|
| Firearm or ammo | `... 150gr ... 20rds` | ammo |
| Brand and model (50 brands) | `G19 Gen5 MOS`, Glock part number `PA195S203` | Glock 19 Gen 5 MOS |
| Calibers (293) | `.357 Mag / .38 Spl`, `6.5 CM`, `12ga`, `300 BLK` | .357 Magnum, .38 S&W Special, ... |
| Magazine capacity | `15+1`, `17RD`, `10-shot` | 15, 17, 10 |
| Barrel length, inches | `4.02in`, `16 1/2"`, `22 BBL` | 4.02, 16.5, 22.0 |
| Rounds per box | `50/Box`, `Box of 20`, `1,000 Rounds` | 50, 20, 1000 |

Brands with model dictionaries include Glock, Sig Sauer, Smith & Wesson, Ruger, Springfield, Taurus, Mossberg,
Remington, Beretta, Benelli, Browning, Winchester, CZ, FN, HK, Henry, Marlin, Savage, Tikka, Walther and 30 more. The
same parser is available for [Java](https://central.sonatype.com/artifact/com.gunsnprices/gunsnprices),
[Python](https://pypi.org/project/gunsnprices/) and [Node.js](https://www.npmjs.com/package/gunsnprices), with
identical results, and as an extension for [VS Code](https://github.com/gunsnprices/vscode-gunsnprices).

## Privacy

The plugin reads only the text you select or put the caret on, in memory. It sends nothing anywhere: there is no
telemetry and no network code. With the links setting turned on, caliber names become ordinary links that open in your
browser when you click them.

## Building

JDK 21. The build downloads Gradle and IntelliJ IDEA Community 2024.3 (about 1 GB).

```bash
./gradlew test            # unit tests and headless platform tests
./gradlew buildPlugin     # build/distributions/gun-title-parser-<version>.zip
./gradlew verifyPlugin    # IntelliJ Plugin Verifier: IDEA 2024.3.7 and IntelliJ IDEA 2026.3
./gradlew runIde          # try it in a sandbox IDE
```

The plugin is written in Java and uses only `com.intellij.modules.platform`, so it runs in every IntelliJ-based IDE.
It is built from the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

## License

MIT. The model and caliber data come from the `gunsnprices` library (CC BY 4.0, attribution: GunsNPrices,
https://gunsnprices.com).

Questions, corrections and missing models: open an issue or write to app@gunsnprices.com.
