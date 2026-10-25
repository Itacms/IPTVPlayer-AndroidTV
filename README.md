# Player IPTV (Android / Android TV)

Interface HTML (app/src/main/assets/index.html) dentro de um WebView transparente,
com player nativo Media3/ExoPlayer por baixo e HLS.js/HTML5 como alternativa.

- Player Automático: tenta ExoPlayer primeiro e troca para o player do navegador se o canal falhar (e vice-versa).
- Diagnóstico em Configurações > Player e diagnóstico (só erros aparecem sobre o vídeo).
- Listas M3U por endereço ou arquivo local; várias listas ao mesmo tempo.
- EPG (XMLTV .xml/.xml.gz), aba Guia com grade de horários e fontes de logo ({slug}, {name}, {id}).
- Temas (Escuro, Azul escuro, Azul claro, Claro), cores de destaque, fundo (tema, gradiente ou imagem), slide no destaque e opções de miniatura.
- Backup local (.json) e restauração.
- Controles para controle remoto (setas/OK) e para toque.

Compilação: GitHub Actions (.github/workflows/build.yml) gera o APK em Actions > Artifacts.
Este código-fonte ainda não foi compilado pelo autor: confirme o resultado do build antes de instalar.

## Alterações de aparência (versão personalizada)
- Imagem de fundo por URL ou seleção de imagem local no Android.
- O fundo decorativo é ocultado durante a reprodução em tela cheia para evitar cobrir o vídeo nativo.
- Tema com efeito de vidro, ajuste de opacidade do tema e das caixas de logos.
- Seleção de pasta local de logos (arquivos de imagem diretamente na pasta, com nome correspondente ao slug do canal) e fontes de logos online por modelo `{slug}`, `{name}` ou `{id}`.

Para gerar o APK: envie o projeto para um repositório GitHub e execute Actions > Build APK. O APK é gerado como artefato `IPTVPlayer-APK`. Esta edição foi validada quanto à sintaxe JavaScript e ao XML do Manifest, mas ainda precisa de compilação Android e teste de reprodução num dispositivo real.
