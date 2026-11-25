# Harness

Cliente de codificação com IA para Android. Por baixo, um Alpine Linux roda sob
PRoot e dentro dele roda o [Pi](https://pi.dev/) em modo RPC. O usuário nunca vê
um terminal — só uma interface de chat minimalista.

> Status: **MVP** — veja `docs/ARCHITECTURE.md` para o que já foi validado no
> dispositivo e o que depende do CI/aparelho.

## Arquitetura em uma frase

UI Compose → `RpcClient` (JSONL) → `pi --mode rpc` → Node → Alpine → PRoot → Android.

## Requisitos

- Android 8.0+ (API 26), **arm64-v8a**.
- Conexão na primeira execução (baixa o Alpine, ~4 MB, e instala Node + Pi).
- Uma chave de API de um provedor suportado pelo Pi.

## Instalação

1. Baixe o APK `harness-<versão>-arm64-v8a.apk` nos artifacts do workflow ou no
   Release.
2. Instale no aparelho (é preciso permitir instalação de fontes desconhecidas).
3. Na primeira abertura, aguarde o onboarding ("Preparando o ambiente").
4. Em **Configurações**, cole a chave do provedor e escolha o modelo.
5. Crie ou clone um projeto e comece a conversar.

## Build local

O APK só compila com o Android SDK (o Termux não o possui). No CI o workflow
`.github/workflows/build.yml` cuida de tudo:

1. `proot` — compila `libproot.so` + `libproot-loader.so` (aarch64, NDK) e
   publica como artifact.
2. `smoke` — valida Alpine → Node → Pi → RPC em container aarch64 (QEMU).
3. `apk` — baixa o artifact do PRoot, roda lint + testes + `assembleRelease`.
4. `release` — em tags `v*`, anexa o APK a um GitHub Release.

Localmente (com SDK instalado):

```sh
./gradlew lintDebug testDebugUnitTest assembleRelease
```

## Assinatura

Se os secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD` existirem, o APK é assinado com a
chave de release. Caso contrário, cai na chave de debug para continuar
instalável.

## Estrutura

```
harness-android/
├─ app/                      módulo Android (Kotlin/Compose)
│  └─ src/main/
│     ├─ java/dev/harness/
│     │  ├─ core/env/        HarnessPaths, ProotCommand, Provisioner,
│     │  │                   RootfsDownloader, TarExtractor, ProcessRunner
│     │  ├─ core/rpc/        JsonlFramer, Protocol, RpcClient
│     │  ├─ core/state/      ChatState, ChatReducer
│     │  ├─ core/pi/         PiProcess, AgentSession
│     │  ├─ core/keys/       SecretStore (Keystore)
│     │  ├─ core/projects/   ProjectRepository
│     │  └─ ui/              tema, componentes, telas
│     └─ res/
├─ native/proot/             build-proot.sh (compilação do PRoot)
├─ scripts/                  provision/smoke (POSIX sh)
├─ docs/                     DESIGN.md, ARCHITECTURE.md
└─ .github/workflows/        build.yml
```

## Licenças

Veja `THIRD_PARTY_NOTICES.md`. PRoot é GPL-2.0 e é distribuído como executável
separado.
