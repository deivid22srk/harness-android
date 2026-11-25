# Avisos de terceiros

Este aplicativo incorpora ou distribui os seguintes componentes de terceiros.

## PRoot (GPL-2.0)

- Projeto: https://github.com/proot-me/proot
- Fork usado: https://github.com/termux/proot (patches Android)
- Licença: **GNU General Public License v2.0**
- Forma de distribuição: binário executável **separado** (`libproot.so` e
  `libproot-loader.so`), empacotado em `jniLibs/arm64-v8a/`, sem linkagem
  estática ao restante do aplicativo.
- Código-fonte correspondente: https://github.com/termux/proot (tag
  `v5.1.107.102`) e o script de build em `native/proot/build-proot.sh`.
- Veja `COPYING` do PRoot para o texto completo da GPL-2.0:
  https://github.com/proot-me/proot/blob/master/COPYING

Quem redistribuir este aplicativo deve preservar este aviso e disponibilizar o
código-fonte correspondente do PRoot conforme a GPL-2.0.

## Alpine Linux (várias licenças, base MIT)

- https://alpinelinux.org/ — rootfs distribuído/montado em runtime.
- Alpine usa predominantemente a licença MIT; pacotes individuais têm as suas.
  Consulte `/lib/apk/db/installed` e `/usr/share/licenses` dentro do rootfs.

## Pi coding agent (MIT)

- https://pi.dev/ · https://github.com/earendil-works/pi
- Pacote: `@earendil-works/pi-coding-agent`
- Licença: MIT.

## JetBrains Mono (SIL Open Font License 1.1)

- https://www.jetbrains.com/lp/mono/ · https://github.com/JetBrains/JetBrainsMono
- Fonte monoespaçada embarcada em `res/font/` (`jetbrainsmono_*.ttf`), v2.304.
- Licença: SIL Open Font License, Version 1.1 — texto completo em
  `docs/OFL-JetBrainsMono.txt`.

## Node.js (MIT, com componentes de terceiros)

- https://nodejs.org/ — instalado dentro do Alpine.

## libandroid-shmem (BSD-3-Clause)

- https://github.com/termux/libandroid-shmem — usado na compilação do PRoot.

## talloc (GPL-3.0)

- https://talloc.samba.org/ — biblioteca de alocação exigida pelo PRoot
  (linkada estaticamente ao `libproot.so`).

## AndroidX / Jetpack Compose / Kotlin (Apache-2.0)

- https://developer.android.com/jetpack · https://kotlinlang.org/

## kotlinx.serialization / kotlinx.coroutines (Apache-2.0)

- https://github.com/Kotlin
