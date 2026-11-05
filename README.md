# Sample Count

## Get the APK (no PC needed)
1. Upload everything in this folder to a GitHub repo (keep the `.github` folder).
2. Actions tab -> Build APK -> wait ~5 min -> Artifacts -> download `RateCalculator-APK` -> install `app-release.apk`.

## Notes
- Installs side by side with the old app (applicationId ends in `.v2`).
- Formulas are in `Calc.kt`: Total = rate x qty; CC = qty / tablets-per-CC.
- - CC tab: type CC (or Pot) per product; quantity = CC x tablets-per-CC.
- History tab: saved invoices as PNG files (also copied to Pictures/SampleCount on Android 10+).
- Products come from app/src/main/assets/rates.csv on first run.

## Features added
- Font size (menu > Font size), pencil = change date (today or pick), undo/redo next to search,
  CC clear confirmation, Save as New / Overwrite Existing (latest invoice).
