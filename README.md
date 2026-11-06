# IPTV Clone

App Android (TV + celular) em Kotlin/Compose com Media3, layout inspirado no TCL Stream.

- Aba **Listas IPTV**: adicionar lista por URL (com botão Colar), por arquivo local
  (.m3u/.m3u8), ativar/desativar, atualizar e remover.
- Build: push no `main` → GitHub Actions gera o APK debug (artefato `iptv-clone-debug`).
- Local: `gradle assembleDebug` (Gradle 8.9, JDK 17).
