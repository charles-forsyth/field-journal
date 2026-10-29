# Decisions

D1 Toolchain: AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14, Gradle 8.9, BOM 2024.06. Platforms;android-35 for
   compileSdk/targetSdk 35. Build-tools 34.0.0 used for apksigner. Lint is off for release builds.
D2 Signing: Debug signing configured for reproducible open-source builds. Users building production releases
   can configure their own release keystore via gradle properties or signing configs.
D3 Persistence: plain SQLiteOpenHelper instead of Room, to keep kapt/KSP out of the build. Schema v1, 6 seed entries inserted in onCreate.
D4 Zone geometry: built-in demo zones are neutral offsets from the demo site center. User zones and custom home location
   can be configured via Settings > Calibration (the smallest containing radius wins).
D5 Triangulation: local ENU tangent-plane intersection around the A/B midpoint (accurate at <20 km). Returns null if the rays are
   parallel or meet behind an observer. Needs a baseline of at least 5 m.
D6 Rangefinder: d = eyeHeight / tan(-pitch). Eye height defaults to 1.6 m and can be changed in Settings. Returns null at or above the horizon.
D7 Baro elevation: 44330*(1-(p/p0)^(1/5.255)) against a calibrated base elevation pressure (default 1013.25). Trend dead-band is +/-0.5 hPa over about 3 h.
D8 Theme: Night mode uses one red-only palette and RED_620_MATRIX (luminance goes to R, G and B are zeroed). It filters the camera preview,
   map tiles, photos and the spectrogram. OSM gets a dark-invert filter in Forest Dusk. USGS imagery is left unfiltered in Sunlight and Dusk.
D9 Tiles: only USGS TNM (Z/Y/X via a getTileURLString override) and OSM Mapnik. A unit test checks that no tile URL needs a key (CARTO/Mapbox/etc).
D10 Telemetry feed: read-only GETs with a 2.5 s callTimeout. The parser accepts several key spellings and nested objects.
    If shelter/ridge temps exist but no freeze margin, it is computed as temp-32. Offline values are stored as null and shown as "--".
D11 AI: optional. The deterministic NoteParser always runs. If a key is set, POST {gateway}/chat/completions (OpenAI-compatible)
    adds hashtags in the background. The key is kept only in EncryptedSharedPreferences, with no secrets in source or APK.
D12 Spectrogram: 16 kHz mono, FFT 512 / hop 256, Hann window, 64 mel bins, dB clamped to -90..0. Live rendering is capped at about 60 fps.
    Without a mic, a simulated spring-noise + chirp + 120 Hz hum waveform is streamed instead. Clips are saved as WAV next to the entry.
D13 Speech: Android SpeechRecognizer (EXTRA_PREFER_OFFLINE). Volume keys are only captured on the Lens tab and only when enabled in Settings.
D14 GDD curve on the scrubber is a static normals-shaped proxy. Live GDD comes from station telemetry in the stamp.
D15 Network security config allows cleartext HTTP for local sensor feeds and custom LAN MQTT/HTTP stations.

## v1.1 redesign — "vintage expedition gear / alpine field guide"
D16 Palette lives in core/PaletteSpec (plain ARGB ints) so ContrastTest checks WCAG AA without Android. Sunlight uses the brief's
    hexes exactly. Secondary text needed one extra colour not in the brief: "dim" #65553F (6.0:1 on paper, 5.3:1 on parchment).
    Lichen #8A9A6B, brass #A8864F and khaki #CBB994 fail 4.5:1 on cream, so in Sunlight they are used only for rules, ticks,
    curves and active marks, never for text. Forest Dusk: bg #1E1A15, cards #2A241C, cream text, brass #C8A464, lichen #A7B585.
    Night: every colour has G = B = 0 (unit-tested). Hierarchy at night comes from size and weight, not hue.
D17 Fonts: EB Garamond 400/500/italic (titles), Source Sans 3 400/600 (body/UI), IBM Plex Mono 400/500 (readings and gear tags),
    from github.com/google/fonts (OFL, licences in licenses/). Variable fonts were pinned to static weights with fontTools
    instancer and subset to Latin-1 + punctuation/arrows: 7 files, 366 KB total. Journal metadata uses tracked mono caps
    instead of Garamond small caps, because Garamond's old-style figures made "1014" read as "IOI4" on screen.
D18 Controls: one filled PrimaryButton per screen (Snap; Record; Export), hairline FieldButton/TextAction for the rest, ~32 dp
    visible faces inside 48 dp touch boxes, 4 dp corners. At night the primary is outlined, not filled, so it never makes a
    large lit block. Secondary actions moved into ModalBottomSheets (Journal: export / jump / recorder; Map: go-to / clear rays;
    Lens: ghost-frame picker). The display-mode switcher left the masthead: it is in Settings and in a sheet opened by tapping
    the mode tag in the masthead.
D19 Lens: readings left the overlay for a brass "instrument plate" below the viewfinder (Azimuth / Pitch / Range + Baro, Elev,
    Temp, GDD). Light is in the masthead, so it isn't repeated on the plate. The reticle is 1 dp brass with a faint paper halo so
    it stays visible over dark subjects. The no-camera fallback is a printed compass card.
D20 Map tiles: Sunlight = soft sepia matrix on every layer. Dusk: topo = warm luminance inversion; imagery = dimmed warm; OSM
    street = dimmed warm without inversion (inverting OSM puts white roads on white land darker than the land around them).
    Night: drawn maps are inverted into dim red (black paper), imagery is dimmed red. Nothing is brighter than R=160.
    Markers are stamped discs with a serif category letter (G garden, W wildlife, F fleet, S water/spring, M forage,
    N general) and a contrasting halo.
D21 Found and fixed a 1.0 bug: USGS basemap tiles return 404 above z16 at certain rural areas, and 1.0 opened the map at z18, so tiles never
    loaded. Each layer now has a max zoom (USGS 16, OSM 19) and the map opens at z16.
D22 Night mode hides the status and navigation bars (swipe from an edge to show them), because Android won't tint them red.
    Android shows its one-time "Viewing full screen" tip the first time this happens.
D23 Spectrogram: theme inks (sepia→pine→ink on parchment; loden→lichen→brass→cream at dusk; red at night) and per-clip dB
    normalisation (SpectroScale, 2nd–99.5th percentile) so quiet field clips still show structure.
D24 App icon: letterpress badge (mountain + pines in a compass ring, brown/green on cream), flattened to the paper
    colour, adaptive foreground WebP 432 px (11 KB) plus a monochrome layer (5 KB).
D25 Paper texture: faint topographic contours drawn once per size (drawWithCache) at 5% alpha; none in Night mode.
D26 Screenshots and showcase assets stored under docs/.

## v1.2 polish — fixes from the v1.1 eye review
D27 Contours (item 1): the full-screen topo texture is gone. Contours are drawn only in the masthead band (Modifier.topoBand,
    clipped to the band, 13% alpha day / 16% dusk), and every text block in the band sits on a paper "knockout" plate with a
    feathered edge, so no line can cross a glyph. Content areas (cards, lists, Settings, map frame, compass card) are plain
    paper. Night still has none. This also removes duplicate contours behind the map.
D28 Contrast (item 2): Sunlight "dim" darkened #65553F -> #54452F (7.7:1 on paper, 6.8:1 on parchment). PaletteSpec now
    names the UI text roles (placeholder, tabInactive, navInactive, meta, stamp text/fills, curveLabel); the Compose layer
    reads those names, and ContrastTest.everySmallUiTextMeetsAaOnItsActualBackground checks 26 fg/bg pairs per mode
    (placeholder, inactive tabs, nav, pin count, month letters, metadata, plate labels on card, unit column on card,
    switch stamps, ray state, GDD label, attribution...). Disabled controls keep full-strength dim text (AA) and show
    disabled-ness by border, never by alpha.
D29 Cards (item 3): the title is shown once. Readouts.bodyRepeatsTitle() hides bodies that are auto captions ("Photo @ X",
    "Acoustic clip 8s @ X", "Sighting @ X") or that equal/prefix the title. Under a photo: bearing + pitch + coordinates +
    baro; under a spectrogram: duration (from the WAV size) + "16 kHz mono" + coordinates + baro (Readouts.mediaLine).
    Nothing when no data.
D30 Filter row (item 4): ScrollFadeRow = paper fade + a small chevron at the edge with more content (tap scrolls 240 px),
    left fade appears once scrolled, 30 dp end padding so the last tab scrolls fully clear.
D31 Forest Dusk (item 5): bg #141A15, cards #1C241D, chip #26302A, rule #35413A, brass #B89758, primary (buttons/links)
    #CFAA72, cream text #EAE3D0, heading #F2EBDA, dim #B8B39D (8.4:1). Green is the strongest channel of bg/card.
D32 Lens (item 6): the reticle draws every stroke twice - a 3.4 dp dark halo then the 1.2 dp brass line.
    Ray A / Ray B are RayControl plates with state glyphs.
D33 Map (item 7): Sunlight tile filter: 65% saturation + per-channel scale. Layer tabs Aerial / Topo / Survey.
    Pins: core/PinClusters Web-Mercator screen-space clustering. Season scrubber: 12-cell ruler.
D34 Settings (item 8): Readings parchment plate of read-only LedgerRows. Settable items on paper: Calibration, Display, Sync.
D35 Nav icons: four engraved instrument icons on the 24 grid (Lens, Map, Journal, Settings).
D36 QA & Screenshots: debug build tested on Android emulator.
D37 Tests: all core and design tests pass.
