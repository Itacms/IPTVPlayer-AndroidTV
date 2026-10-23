# Player IPTV (Android / Android TV)

Interface HTML (app/src/main/assets/index.html) dentro de um WebView transparente,
com player nativo Media3/ExoPlayer por baixo e HLS.js/HTML5 como alternativa.

- Player Automático: tenta ExoPlayer primeiro e troca para o player do navegador se o canal falhar (e vice-versa).
- Diagnóstico em Configurações > Player e diagnóstico (mensagens de erro só aparecem sobre o vídeo quando há falha).
- Listas M3U por endereço ou arquivo local (botão "Subir lista local").
- Controles para controle remoto (setas/OK) e para toque (toque, duplo toque, deslizar).

Compilação: GitHub Actions (.github/workflows/build.yml) gera o APK em Actions > Artifacts.
Este código-fonte ainda não foi compilado pelo autor: confirme o resultado do build antes de instalar.
