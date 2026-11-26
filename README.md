# Hitung Pondasi

Aplikasi Android (Kotlin + Jetpack Compose, offline) untuk perhitungan:

- **Pondasi telapak (footplate)**: daya dukung tanah dari N-SPT (Meyerhof, revisi Bowles) atau qa manual,
  kontrol eksentrisitas & tegangan tanah, geser satu arah, geser pons, tulangan lentur, pencarian ukuran minimum otomatis.
- **Tiang pancang & pilecap**: tiang bulat, persegi, spun pile, dan bore pile.
  Tahanan aksial dari kekuatan bahan dan SPT (Meyerhof), tahanan lateral (Brinch Hansen),
  gaya aksial/lateral tiap tiang, geser satu arah, geser pons, tulangan lentur & susut pilecap.
- **Laporan PDF** berisi seluruh langkah perhitungan.

Acuan: SNI 2847 (beton) dan SNI 8460 (geoteknik). Hasil perhitungan wajib diperiksa oleh perencana yang berwenang.

## Struktur

- `core/` — mesin perhitungan Kotlin murni + uji (dicocokkan dengan contoh Excel pondasi tower crane Cipete).
- `app/` — aplikasi Android.

## Build

```
./gradlew :core:test          # uji perhitungan (tidak perlu Android SDK)
./gradlew :app:assembleDebug  # APK, perlu Android SDK
```

GitHub Actions membangun APK otomatis pada setiap push (lihat tab *Actions* → artifact `HitungPondasi-apk`).
