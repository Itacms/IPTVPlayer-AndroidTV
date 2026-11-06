# Relatório de engenharia reversa — TCL Stream 7.2.24

Método: análise **estática** do XAPK (manifest.json, recursos, assets, tabelas de strings dos DEX).
Não havia jadx/apktool nem acesso à rede no ambiente, então **não houve decompilação para Java**;
o que segue vem de recursos, arquivos .proto e nomes de classes/strings nos DEX.

## Pacote
- `com.tcl.stream`, versão 7.2.24 (code 100414), minSdk 28, targetSdk 36
- XAPK com 3 splits: base (15,6 MB), `config.arm64_v8a`, `config.mdpi`
- 8 arquivos DEX (multidex), protobuf, Firebase/Google Play Services, OkHttp, Kotlin

## Permissões
INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, WAKE_LOCK, FOREGROUND_SERVICE,
AD_ID + AD_SERVICES, e permissões proprietárias TCL (`com.tvos.permission.READ_DEVICE_INFO`,
`com.tcl.messagebox.access`, `com.tcl.bi.*`, `com.tcl.ttvs.deviceauthen.*`) — o app é
amarrado a TVs/firmware TCL.

## Arquitetura
- Código principal em `com.tcl.waterfall.overseas`: `MainActivity`, `MediaPlayerActivity`
  (player v2), `MediaDetailActivity`, `epgDetail/WatchNowDetailActivity`, `SubjectActivity`,
  `SettingActivity`, `MoviesModeActivity`, telas de senha/privacidade, `WebActivity`.
- Módulos TCL: `browser` (portal web + `PlayWebVideoActivity`), `dtv` (scan/pvr/ci — TV
  digital), `uniplayer` + `tuniplayer_exo` (wrapper do ExoPlayer), `videoad.ima` (anúncios),
  `bi.sdk` (analytics), `favorite.api`, `uicompat` (TCLButton/TCLTextView).
- UI: **Leanback** (≈119 referências) — layouts `lb_browse_fragment`, `fragment_side_drawer`,
  `vertical_tab_fg_layout`, `fg_epg_live_layout_*`, `fragment_epg_player`, `layout_media_player_activity`.
- Player: ExoPlayer (legado `exoplayer2` + wrapper TCL), com HLS e DASH; shaders GLSL de
  transformação/HDR em `assets/shaders`.
- Layout dirigido por servidor (protobuf em `com.tcl.database`):
  - `TabResponse{msg, Tabs{waterFallTabs[], templateId, signature, hasKidsMode, adStrategy}}`;
    `Tab{tabId, tabName, tabTitle, isLane, ícones de foco, tabAd, immersiveSwitch, firstScreenAd}`
  - `SideBarSet{sideBars[], md5, mainPage}`; `SideBar{id, type, title, 4 ícones}`
  - `ColumnsResponse`: itens de conteúdo com `Actionurl` (activity/package/uri/extras),
    legendas (`Captionlist`), DRM (`TVDrmConfig`/`DrmConfig`: tipo, expiração, licenseUrl,
    headers), anúncios (`AdInfo`), pôsteres, trailers.
- `assets/HomePage.json`: atalhos de navegador embutidos (Google, YouTube, Crunchyroll, IGN…).
- Monetização/consentimento: IMA SDK, Google Ads, chaves IABTCF/GPP de CMP.

## Conclusões úteis para o clone
1. **O app original não tem suporte a listas M3U do usuário.** Conteúdo (canais FAST, VOD,
   EPG) vem de backend TCL via protobuf; as ocorrências de "m3u" nos DEX são do parser HLS.
2. Padrão de navegação a replicar: sidebar vertical + abas dirigidas por dados + player em
   Activity própria + EPG ao vivo.
3. A "aba nova" precisa ser **local** (não depende do backend TCL): importação de M3U por URL
   e por arquivo (SAF), cache em disco, ativar/desativar, atualizar, remover.

## O que o clone faz / não faz
- Faz: sidebar (TV/tablet) ou barra inferior (celular), Canais, Filmes, Favoritos,
  **Listas IPTV (URL + arquivo local)**, Configurações, player Media3 (HLS/DASH/TS).
- Não copia código, assets, marca, anúncios nem backend da TCL — é uma implementação
  original que imita a estrutura de navegação. Sem EPG ainda (próximo passo: XMLTV via
  `url-tvg` do M3U).
