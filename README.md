# FishBot — bot wędkarski dla Minecraft 26.2 (Fabric)

Zaawansowany, w pełni konfigurowalny system automatycznego wędkowania dla Minecraft **26.2** (Fabric, klient).
Projekt wzorowany na [XPlus Autofish](https://github.com/Wudji/XPlus-AutoFish) (GPL-3.0), rozbudowany o pełne
zarządzanie ekwipunkiem, bezpieczeństwo AFK i powiadomienia.

> ⚠️ **Uwaga:** automatyzacja gry może łamać regulaminy serwerów. Używaj wyłącznie tam, gdzie jest to dozwolone
> (np. singleplayer, własne serwery). Używasz na własną odpowiedzialność.

---

## Funkcje

### 🎣 Mechanika łowienia i optymalizacja łupu
- **Detekcja pakietowa (Server-side Hook Detection)** — domyślny tryb `PACKET` czyta zsynchronizowane dane encji
  spławika (flaga `biting` ustawiana przez serwer w momencie brania). Zacięcie w ułamku sekundy, ~100% skuteczności.
  Dostępne też starsze tryby `MOTION` (pakiety ruchu spławika) i `SOUND` (dźwięk plusku).
- **Walidator „Open Water" (zasada 1.16+)** — klientowska replikacja algorytmu `calculateOpenWater` z gry:
  sprawdzanie obszaru 5×4×5 wokół spławika (woda źródłowa bez kolizji pod spodem, powietrza/rzęsy nad powierzchnią).
  Ostrzeżenie na ekranie i na HUD, gdy akwen nie daje skarbów (książki, siodła, łuki…).
- **Humanizacja kliknięć** — konfigurowalny losowy jitter: opóźnienie zacięcia (domyślnie 150–350 ms) oraz
  opóźnienie ponownego rzutu z losowością ±%.
- **Tryb ciągły (persistent)** — automatyczne ponowne zarzucanie, gdy spławik zniknie (np. po lagach).
- **Detekcja rzadkich łupów** — po braniu bot obserwuje upuszczone przedmioty przy spławiku i liczy skarby.

### 🎒 Pełne zarządzanie ekwipunkiem i stanem postaci
- **Cykl wędek z ochroną przed złamaniem** — automatyczna zmiana wędki na najlepszą dostępną w pasku,
  gdy wytrzymałość spadnie do progu (domyślnie 10 pkt); awaryjne zwinięcie przy 1 pkt wytrzymałości.
- **Mending Management** — gdy w pobliżu pojawiają się kule doświadczenia, wędka z *Mending* jest automatycznie
  przekładana do drugiej ręki (off-hand) pakietem `ContainerInput.SWAP`, aby regenerować wytrzymałość,
  a po wchłonięciu expa wraca na miejsce.
- **Auto-sortowanie i opróżnianie do skrzyń** — gdy ekwipunek się zapełni (konfigurowalny próg), bot znajduje
  najbliższą skrzynię / beczkę / shulker box w promieniu (do 6 bloków), obraca się do niej, otwiera ją
  i przekłada przedmioty według reguł:
  - tryb **KEEP_LIST** — odkłada wszystko poza listą zatrzymywaną (domyślnie wędki i perły),
  - tryb **TREASURES** — odkłada tylko skarby z listy,
  - **śmieci** (skórzane buty, miski, patyki…) są wyrzucane lub odkładane wg ustawień.
- **Auto-jedzenie** — wybór najlepszego jedzenia z paska i jedzenie, gdy głód spadnie poniżej progu
  (priorytet złotych jabłek przy niskim zdrowiu).

### 🛡️ Bezpieczeństwo i niezawodność (Overnight AFK)
- **Panic System / Failsafe** — po otrzymaniu obrażeń (mob, gracz, lawa) lub spadku HP poniżej progu (domyślnie 30%):
  tylko alert, **rozłączenie z serwerem**, albo **rzut perłą Kresu i rozłączenie**, jeśli zagrożenie nie minęło.
- **Anty-AFK Bypass** — subtelne, nieregularne mikro-ruchy: losowe obroty kamery ±1–4°, krótkie kucnięcia,
  skoki i kroki w bok, w losowych odstępach 40–90 s (konfigurowalne).

### 📊 Interfejs i powiadomienia
- **HUD ze statystykami na żywo** — nakładka ekranowa: czas sesji, ryby (+ ryby/h), rzadkie przedmioty,
  zdobyty EXP (+ EXP/h), paski wytrzymałości wędek, status bota i stan Open Water.
- **Integracja z Discord Webhook** — asynchroniczne powiadomienia (embed): rzadki drop z nazwą przedmiotu,
  zapełniony magazyn / odłożenie łupu, atak, panika i rozłączenie. Przycisk testowy w GUI.
- **Presety jednym kliknięciem** — wbudowane profile: *Nocny AFK*, *Łowca Skarbów*, *Szybkie Łowienie*,
  *Domyślne* + zapis/odczyt własnych presetów (`config/fishbot_presets/*.json`).
- **Własne GUI konfiguracyjne** (bez zewnętrznych zależności) — zakładki: Wędkowanie / Ekwipunek /
  Bezpieczeństwo / Interfejs / Presety, pełne PL i EN.

---

## Instalacja

1. Zainstaluj [Fabric Loader](https://fabricmc.net/use/) **0.19.3+** dla Minecraft **26.2**.
2. Pobierz [Fabric API](https://modrinth.com/mod/fabric-api) dla 26.2 (np. `0.158.0+26.2`) i wrzuć do `mods/`.
3. Wrzuć `fishbot-1.0.0.jar` do folderu `mods/`.

## Budowanie

Wymagane **JDK 25**.

```bash
./gradlew build
```

Gotowy mod pojawi się w `build/libs/` (bez suffixa `-sources`).

## Sterowanie

| Klawisz | Akcja |
|---|---|
| `R` | Otwórz/zamknij GUI FishBot |
| `G` | Szybkie włączenie/wyłączenie bota |

(Klawisze można zmienić w ustawieniach sterowania gry → kategoria *fishbot*.)

## Konfiguracja

Ustawienia zapisywane w `config/fishbot.json` (m.in.):

- `detectionMode` — `PACKET` / `MOTION` / `SOUND`
- `reelInDelayMinMs` / `reelInDelayMaxMs` — humanizacja zacięcia (domyślnie 150–350 ms)
- `recastDelayMs` / `recastJitterPercent` — opóźnienie i losowość ponownego rzutu
- `rodDurabilityThreshold` — próg zmiany wędki (domyślnie 10)
- `mendingOffhand` — przekładanie wędki z Mending do off-hand przy kulach expa
- `autoDeposit`, `depositRadius`, `depositTriggerSlots`, `depositMode`, `keepItems`, `treasureItems`, `trashItems`, `discardTrash`
- `autoEat`, `eatThreshold`
- `panicEnabled`, `panicHealthPercent`, `panicOnDamage`, `panicAction` (`ALERT_ONLY` / `DISCONNECT` / `PEARL_THEN_DISCONNECT`)
- `antiAfk`, `antiAfkMinSec`, `antiAfkMaxSec`
- `hudEnabled`, `hudX`, `hudY`
- `discordEnabled`, `discordWebhookUrl`, `discordOnRare`, `discordOnStorage`, `discordOnAttack`, `rareItems`

### Discord Webhook

1. Na serwerze Discord: *Ustawienia kanału → Integracje → Webhooki → Nowy webhook*, skopiuj URL.
2. W GUI FishBot → zakładka **Interfejs** → wklej URL, włącz „Discord webhooks", użyj „Wyślij testowe powiadomienie".

---

## Jak działa detekcja pakietowa?

Serwer w momencie brania ustawia we flagach danych spławika (`SynchedEntityData`) pole `biting=true`
i wysyła do klienta pakiet `ClientboundSetEntityDataPacket`. Mixin na
`FishingHook.onSyncedDataUpdated` przechwytuje ten moment niezależnie od trybu gry (singleplayer/multiplayer)
i natychmiast planuje zacięcie z ludzkim jitterem. To dokładnie ten sam sygnał, na podstawie którego
sama gra „wie", że ryba bierze — dlatego skuteczność jest praktycznie 100%.

## Struktura projektu

```
src/main/java/dev/fishbot/
├── FishBot.java            — punkt wejścia klienta (klawisze, tick)
├── FishBotCore.java        — orkiestrator podsystemów
├── config/                 — konfiguracja JSON + presety
├── fishing/                — detekcja brań, scheduler, walidator Open Water, skaner łupów
├── inventory/              — cykl wędek + Mending, opróżnianie skrzyń, auto-jedzenie
├── safety/                 — panic failsafe, anty-AFK
├── stats/                  — statystyki sesji
├── notify/                 — Discord webhook
├── gui/                    — HUD + ekran ustawień
└── mixin/                  — Mixiny (spławik, pakiety)
```

## Podziękowania i licencja

- [XPlus Autofish](https://github.com/Wudji/XPlus-AutoFish) (troyboy50, Wudji) — wzorzec architektury
  detekcji i scheduler, GPL-3.0.
- [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod) — szablon projektu dla 26.2.

Kod FishBot jest udostępniany na licencji **GPL-3.0** (patrz `LICENSE`).
