# Player IPTV — migração nativa Android TV

Esta branch contém a primeira etapa da migração da interface para layouts nativos Android, com reprodução via Media3/ExoPlayer. O vídeo é criado apenas ao iniciar a reprodução; a janela PIP da tela inicial foi removida.

- Importação de lista M3U por URL ou arquivo local.
- Interface Android nativa para navegação inicial e seleção de canais.
- Player Media3/ExoPlayer criado sob demanda.

**Limitações desta etapa:** o guia EPG/XMLTV, Multi View, temas avançados, favoritos e backup/restauração ainda não foram migrados integralmente. A compilação bem-sucedida não substitui teste em uma Android TV Box real.

O workflow `.github/workflows/build.yml` compila o APK no GitHub Actions. Em um pull request para `main`, o arquivo APK fica disponível em **Actions → execução do workflow → Artifacts → IPTVPlayer-APK**.
