# Scorebuddy Stats — capture fáza

Appka číta obsah obrazovky appky **Unicorn Scorebuddy** (`com.joofunn.idart`)
cez Android Accessibility Service. V tejto fáze len **zaznamenáva presnú
štruktúru obrazoviek** do textových dumpov, aby sme vedeli navrhnúť
spoľahlivé rozpoznanie obrazovky s výsledkom legu.

Bodovací systém je už hotový a otestovaný (`PlacementScorer.kt`):
posledné miesto = 1 bod, predposledné = 2, ..., víťaz = N bodov
(N = počet hráčov). Zapojí sa hneď, ako budeme mať presnú detekciu
obrazovky z reálneho dumpu.

## Ako appku zostaviť (bez Android Studia)

1. Vytvor si nový **prázdny GitHub repozitár** (napr. `scorebuddy-stats`).
2. Nahraj doň celý obsah tohto priečinka (celú štruktúru súborov tak, ako je).
3. GitHub Actions (`.github/workflows/build-apk.yml`) appku automaticky
   zostaví pri každom pushi do `main` vetvy.
4. V repe choď do záložky **Actions** → posledný beh → v sekcii
   **Artifacts** nájdeš `scorebuddy-stats-debug-apk` na stiahnutie.
5. Rozbaľ, dostaneš `app-debug.apk` — presuň na tablet a nainštaluj
   (treba povoliť inštaláciu z neznámych zdrojov).

Ak máš Android Studio, samozrejme funguje aj klasicky: File → Open →
vyber tento priečinok → Run.

## Ako appku použiť

1. Otvor appku **Scorebuddy Stats**, klikni "Otvoriť nastavenia
   zjednodušenia" a appke povoľ Accessibility permission (bude v zozname
   ako "Scorebuddy Stats").
2. Otvor appku **Unicorn Scorebuddy** a odohraj jeden leg až po obrazovku
   s výsledkom.
3. Vráť sa do appky Scorebuddy Stats, klikni **"Zdieľať posledný dump"**
   a pošli mi ten súbor (napr. cez e-mail, WhatsApp, alebo mi jeho obsah
   skopíruj priamo do chatu).
4. Na základe toho dumpu doplním presnú logiku, ktorá z obrazovky vytiahne
   poradie hráčov a automaticky uloží + odošle výsledky.

## Kam sa výsledky ukladajú

- Vždy lokálne: `Android/data/com.example.scorebuddystats/files/results/leg_results.csv`
  (dá sa vyzdieľať priamo z appky tlačidlom "Zdieľať results.csv")
- Voliteľne aj na webhook URL, ktorú zadáš v appke (JSON POST s poľom
  hráčov, umiestnení a bodov) — môže to byť vlastný server, Google Apps
  Script webhook mierený do Google Sheets, webhook.site na test, atď.
