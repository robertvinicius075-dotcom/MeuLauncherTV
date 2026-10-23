# Meu Launcher TV

Launcher nativo para Android TV 11, com visual inspirado nas telas modernas de TV
(abas, banner grande, favoritos, ícones redondos, foco com borda luminosa).
Identidade própria: sem logotipos, imagens ou nomes de terceiros.

> Estado: o código foi escrito e a sintaxe conferida, mas **ainda não foi compilado nem
> testado em uma TV**. O primeiro build pode mostrar erros pequenos de compilação; se
> aparecerem, mande o texto do erro para corrigir.

## Tecnologia escolhida

**Java nativo, só com o framework do Android (sem AndroidX, sem WebView).**

- Um launcher precisa ser um app Android com uma Activity que declare a categoria `HOME`.
  Uma página HTML aberta no navegador não pode ser launcher.
- HTML/CSS/JS dentro de uma `WebView` até funcionaria, mas em uma TV com cerca de 1 GB de RAM
  custa memória e tempo de abertura, e o foco do controle remoto fica mais difícil de acertar.
  Por isso a interface é montada em código com `View`s nativas, e **não há HTML no projeto**.
- Sem bibliotecas externas, o APK fica pequeno (poucas centenas de KB) e o consumo de memória, baixo.
- Medidas em "unidades de 1080p" (altura da tela ÷ 1080): a mesma interface escala em 720p, 1080p e 4K.

## Arquivos

| Arquivo | Função |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Declara `HOME`, `LEANBACK_LAUNCHER` e `<queries>` (Android 11 esconde os outros apps sem isso) |
| `.../HomeActivity.java` | Tela: barra superior, abas, banner, favoritos, grade, biblioteca, diálogos, fundo |
| `.../AppRepository.java` | Lê os apps instalados (apps de TV e também APKs comuns instalados por fora) |
| `.../Prefs.java` | Favoritos, recentes e fundo (guardados só na TV) |
| `.../Ui.java` | Escala por resolução, cantos arredondados e efeito de foco |
| `.../AppEntry.java` | Dados de um app |
| `.github/workflows/build-apk.yml` | Compila o APK na nuvem do GitHub, sem Android Studio |

## Como usar

- **Setas** movem o foco. **OK** abre. **Segurar OK** num app: favoritar, mover para o início, informações.
- **Menu** (ou o botão "Personalizar"): escolher o fundo.
- **Voltar** leva à aba Início; o launcher nunca fecha.
- Favoritos iniciais, se estiverem instalados: UNI TV, YouTube, Netflix, Globoplay, Prime Video, Disney+ e Spotify.
- Abas: Início (banner, favoritos, apps), Aplicativos (grade completa) e Biblioteca (abertos recentemente e dicas).

### Fundo com imagem

Copie uma imagem `wallpaper.jpg` para `/sdcard/Android/data/com.meulauncher.tv/files/` e escolha
"Usar imagem de fundo". Com ADB:

```
adb push wallpaper.jpg /sdcard/Android/data/com.meulauncher.tv/files/wallpaper.jpg
```

## Como gerar o APK

### Opção A: GitHub (não precisa de computador potente)

1. Crie uma conta e um repositório no GitHub.
2. Envie para ele **todo o conteúdo desta pasta**, mantendo as pastas (incluindo `.github`).
3. Aba **Actions** → "Compilar APK" → **Run workflow**.
4. Quando terminar, baixe o arquivo `MeuLauncherTV-apk` (um ZIP com `app-debug.apk`).

### Opção B: Android Studio

Abra a pasta no Android Studio (JDK 17), aguarde a sincronização e use **Build → Build APK(s)**.
Ou, com o Gradle 8.7 e o Android SDK 34 instalados: `gradle assembleDebug`.
O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Como instalar na TV

1. Na TV: Opções do desenvolvedor → **Depuração USB** ligada.
2. No computador, na mesma rede Wi-Fi:
   ```
   adb connect IP-DA-TV:5555
   adb install -r app-debug.apk
   ```
   Se o `adb install` travar, envie o arquivo e instale pelo shell:
   ```
   adb push app-debug.apk /data/local/tmp/
   adb shell pm install -r /data/local/tmp/app-debug.apk
   ```
3. Sem computador, também dá para instalar por um pendrive com um gerenciador de arquivos
   instalado na TV, liberando "fontes desconhecidas" para ele.

## Como definir como tela inicial

1. Aperte **Home** no controle. Se a TV mostrar uma lista de launchers, escolha **Meu Launcher**
   e marque **Sempre**.
2. Se a lista não aparecer, use:
   ```
   adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME
   adb shell cmd package set-home-activity com.meulauncher.tv/.HomeActivity
   ```
3. Confirme que funcionou: aperte Home dentro de outro app e veja se abre o Meu Launcher.

Para voltar ao original: `adb uninstall com.meulauncher.tv`.

## Limitações de firmware (leia antes)

- **Algumas TVs recusam instalar um launcher.** Alguns firmwares respondem
  `INSTALL_FAILED_VERIFICATION_FAILURE` a qualquer instalação, ou só aceitam apps com
  `HOME` assinados pelo fabricante. Se acontecer, não há o que fazer no app.
- **Alguns firmwares ignoram o launcher padrão** e continuam abrindo o da fábrica ao apertar Home,
  mesmo com o comando acima respondendo "Success". Teste sempre apertando Home.
- **Botões especiais do controle** (Netflix, YouTube, Entradas) podem continuar abrindo o que o
  fabricante definiu; o launcher não captura esses botões.
- **Não há recomendações de filmes nem serviços do Google.** Os cartões são atalhos dos seus apps;
  as imagens vêm do banner que o próprio app declara (ou do ícone, se não houver).
- **Sem permissão especial:** o app só pede visibilidade dos apps que têm ícone de abrir,
  e não acessa a internet.
- Os botões Wi-Fi e Configurações abrem as telas do sistema; se o fabricante tiver removido uma
  delas, o launcher avisa com uma mensagem.
