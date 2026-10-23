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


## Ajuste visual
- A mensagem de status/diagnóstico sobreposta ao vídeo foi ocultada.
- O painel de diagnóstico nas configurações continua disponível.


## Atualizar por cima da versão instalada (sem desinstalar)

O projeto mantém o identificador `applicationId = "com.iptv.player"`. Para o Android aceitar uma atualização por cima da instalação existente, **o APK novo precisa usar a mesma chave de assinatura da versão instalada** e ter `versionCode` maior. Dados locais normalmente são preservados, mas não há garantia para dados que o próprio app apague.

### Assinatura persistente no GitHub Actions

1. Use a chave de assinatura (keystore `.jks`) da versão que já está instalada. Se não tiver essa chave, um APK assinado diferente normalmente não poderá atualizar a instalação existente; será necessário recuperar a chave original ou desinstalar (o que pode apagar dados).
2. No GitHub, abra **Settings → Secrets and variables → Actions → New repository secret** e crie: `ANDROID_KEYSTORE_BASE64` (conteúdo do `.jks` convertido para Base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`. Nunca coloque a chave ou senhas no repositório.
3. Em **Actions → Build Android APK → Run workflow**, informe um `version_code` maior que o da versão instalada e um `version_name` novo.
4. Baixe o artefato **IPTVPlayer-APK-release-update**. Esse é o APK de release assinado para atualização.

O artefato `IPTVPlayer-APK-debug-testing` serve para testes. Não o use para atualizar uma instalação de release, porque a chave de depuração pode ser diferente. Se a chave original da versão instalada for desconhecida, não é possível garantir atualização sem desinstalar.

## Observação sobre a versão

O valor padrão foi configurado como `versionCode 2` e `versionName 1.1` apenas como ponto de partida. Antes de publicar, confirme o `versionCode` usado no APK instalado e escolha um número superior. O `applicationId` não deve ser alterado entre atualizações.
