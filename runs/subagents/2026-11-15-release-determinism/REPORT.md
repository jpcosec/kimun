# R1 — release-determinism

## Medición previa (la inferencia del plan era cierta)
`sha256.txt` en este directorio. Sin cambio (HEAD ad3b2541), `--local-bb --platforms linux-amd64`, sin pod (no existe en el worktree):

| capa | dos corridas | qué difiere |
|---|---|---|
| tar.gz con el mismo jar (jar1) | b43fd105… vs e6358f37… DISTINTO | mtime y modo (umask) de cada entrada, uid/gid del usuario (jp/jp, 1000) |
| gzip | igual | la cabecera ya traía mtime 0 (tar -czf por stdin); no era causa |
| uberjar (`bb uberjar`, dos builds) | f1037a66… vs e5f241a3… DISTINTO | mismas 125 entradas, mismo orden, mismo contenido; solo los timestamps de entrada (dos segundos de resolución) |
| SHA256SUMS | distinto | consecuencia |

## Cambio
- `scripts/release.clj`: `source-epoch` (`SOURCE_DATE_EPOCH` o 315532800 = 1980-01-01, mínimo del zip; entero inválido → exit 2); `normalize-jar!` reescribe el jar con entradas ordenadas y timestamp fijo; `normalize-tree!` fija mtime y permisos (rwxr-xr-x / rw-r--r--, independientes del umask); tar con `--sort=name --owner=0 --group=0 --numeric-owner --mtime=@epoch` y `gzip -n -9` (vía `--use-compress-program`, así un fallo de tar sí aborta).
- `test/kimun/cli/release_test.cljc`: 2 pruebas nuevas (las 2 existentes intactas; total 4), helper `release!` con jar y env opcionales, `build-jar!`.
- `docs/v2/12` fila «Operación y vista»: nota de avance R1.
- No toqué `release.edn`, `bb.edn` ni `.github/workflows/*`.

## Pruebas (4 en total, 46 aserciones, 0 fallos: `directed.txt`; `lint.txt`: ok, 104 archivos)
1. (existente) bundle corre sin bb en PATH, symlink, `--help`, stores init/check — confirma que el jar reescrito funciona.
2. (existente) rechaza plataforma desconocida.
3. NUEVA `release-archives-are-byte-identical-across-builds`: dos uberjars construidos con 2,5 s de diferencia, dos releases con `--out` distintos, mtimes del primer árbol tocados entre medio → mismo sha256 del tar.gz, mismo SHA256SUMS, y SHA256SUMS = «sha  nombre».
4. NUEVA `release-archive-entries-are-sorted-…`: entradas en orden lexicográfico, todas 0/0, mtime = 1980-01-01 por defecto y 2023-11-14 con `SOURCE_DATE_EPOCH=1700000000`, y el sha cambia con el epoch.
Después del cambio, con jar1 y jar2 distintos: mismo tar.gz (082fd0fd…) y mismo SHA256SUMS (ce033a4e…) (`sha256.txt`).

## Mutación (`mutation.txt`)
Con `scripts/release.clj` de HEAD y las pruebas nuevas: 6 fallos (sha distinto, SHA256SUMS distinto, orden, uid/gid 1000, mtime 2026-11-15 ×2); las 2 pruebas existentes pasan. Restaurado después (`git status`: solo los 3 archivos y esta carpeta).

## Límites
- Solo verificado linux-amd64, misma máquina, mismo tar GNU 1.35, gzip y JVM de bb. Zip de windows: se fijan mtimes del árbol, pero `fs/zip` no fija el orden de entradas y NO lo probé. Reproducible entre máquinas o versiones de zlib/gzip no está probado ni definido (plan R1 «ambigüedad»; `docs/v2/12` solo dice «CI/release reproducible», sin definición; `08` no usa «reproducible»).
- El jar se recomprime con `java.util.zip`; su nivel/zlib de la JVM de bb entra en los bytes (misma máquina: estable).
- Las pruebas corrieron sin pod (no hay `pods/treesitter/target` en el worktree); el pod se copia con `fs/copy` y entra al árbol normalizado igual que bb, pero no se ejercitó.
- `bb jar` (bb.edn:17-22) no se cambió: el jar de `target/kimun.jar` sigue siendo no determinista; solo el bundle lo normaliza. Además `resources/git-sha` entra al jar, por lo que un commit distinto da otro release (esperado).
- El gate de `--local-bb`: el bb copiado entra tal cual; con bb descargado, reproducible = mismo binario de entrada (sha pinado).

## Bloqueado, no tocado
- El pod no se construye en CI: `08 §4` (l. ~84) dice «staging de CI» y `.github/workflows/release.yml` no lo construye (plan §1.6).
- `:sha256 nil` en los 5 pods de `release.edn` (l. 29-47): un pod sin pin no es verificable; requiere construirlo en CI.

## Ambigüedades
- `release.edn` l. 1-9 comenta `--pin` y los pods, pero `pin!` reescribe el encabezado de esa cabecera con un texto más corto (scripts/release.clj, `pin!`): perdería el comentario de pods. No relacionado con R1; no corregido.
- Epoch por defecto: el plan dice «`SOURCE_DATE_EPOCH` o un valor fijo»; elegí 1980-01-01; no es decisión del spec.
