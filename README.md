# Player IPTV Android — baseado no index.html da conversa

Este projeto Android Studio usa o `index.html` enviado na conversa como interface principal, empacotado em `app/src/main/assets/index.html`.

## Como compilar
1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Use Build > Build APK(s).
4. O APK de depuração normalmente fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Observações
- Este pacote contém o código-fonte do projeto, não um APK já compilado.
- A reprodução continua usando a camada HTML/WebView existente. A migração completa para Media3/ExoPlayer exige integrar controles de reprodução nativos à interface; não está afirmado como concluído nesta versão.
- A compatibilidade depende dos formatos, codecs, playlists e restrições dos servidores dos canais.
