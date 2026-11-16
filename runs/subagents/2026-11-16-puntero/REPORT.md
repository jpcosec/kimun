# puntero: «miré hasta este commit»

## Archivos
- Nuevo `src/kimun/host/seen.cljc`: `raw`, `read-pointer` (valida con git; `git/bad-pointer` si es ilegible o el sha es desconocido), `write-pointer!` (temporal + rename atómico, solo `<store>/seen.edn` = `{:commit "<sha>"}`), `point-at-head!` (nil sin fallar si no hay repo o commits).
- `src/kimun/cli/commands/stores.cljc`: `changed` sin `--since` usa el puntero (sin puntero: `cli/usage` que dice `stores seen --set HEAD`); `:data` añade `:pointer`; `--since` no lo mueve. Nuevo `seen`. Ayuda del grupo.
- `src/kimun/cli/commands/include.cljc`: `build`/`--rebuild` dejan el puntero en HEAD al terminar con éxito (`:data :seen` lleva el sha cuando se escribió).
- `src/kimun/cli/main.cljc` (ayuda), `docs/v2/06-superficies-y-cli.md` (fila de `changed` ajustada + fila nueva de `seen`), `docs/v2/drafts/diseno-declaraciones.md` (§4 una frase, §8 viñeta sustituida).
- Pruebas: nuevo `test/kimun/cli/seen_test.cljc` (6 tests, 50 aserciones); ajustado `stores_test.cljc` (lista de subcomandos conocidos ahora incluye `seen`).
- `kimun.surface.changes` sin tocar; los helpers copiados no se unificaron.

## Forma de las respuestas
- `stores seen`: `{:commit sha-o-nil :head sha :behind bool}`.
- `stores seen --set COMMIT`: `{:previous sha-o-nil :commit sha :head sha :behind bool}`.
- `stores changed`: lo de antes más `:pointer` (sha guardado o nil).
- Errores: `cli/usage` (sin puntero), `git/bad-pointer` (ilegible o sha desconocido), los de git de antes.

## Qué cubren las pruebas
Sin puntero -> error declarado y no se crea archivo; `seen --set` + `changed` == `--since <sha>` (salvo nada más) y el archivo contiene solo `{:commit sha}`; commit nuevo -> `:behind true` y `changed` lo muestra; `--since` no mueve el puntero; `--set` otra vez -> `changed` vacío; árbol sucio + puntero en HEAD sigue mostrando lo sin commitear (tracked y untracked); `--set` solo añade `seen.edn` (sha256 de todos los demás archivos de `.kimun` idénticos, `git status --porcelain` idéntico) y las lecturas no cambian nada; puntero ilegible y sha desconocido -> `git/bad-pointer` (`--since` explícito sigue funcionando; `--set` lo repara); `include build` y `--rebuild` en repo git dejan HEAD; repo sin commits: no crea puntero y no falla.

## Evidencia
`lint.txt` (ok, 106 archivos), `oracle.txt` (9 filas, 0 discrepancias), `directed.txt` (seen+changed 13 tests/134 asertos; stores+sense+main+lint-test; include+identity-carry 12/77; stores-test tras el ajuste 26/185; todo en 0 fallos), `mutation.txt`: sin los cambios de `src/` (parche propio en /tmp, sin stash, restaurado y verificado con `diff -r`) fallan 24 FAIL + 2 ERROR en seen-test y stores-test. Probé que cada test de seen_test falla sin el cambio.

## Desvíos, límites y problemas del diseño
1. **`--set` sin valor no es posible**: la opción `--set` ya existe en el spec global de opciones como la repetible `FIELD=VALUE` de `docs update` (`src/kimun/cli/commands/docs.cljc:907`, que no podía tocar), y `kimun.cli.args` la trata como opción con valor. Por eso es `stores seen --set COMMIT` (COMMIT obligatorio; `HEAD` vale como COMMIT), no «`--set [COMMIT]` por defecto HEAD». Para el default hace falta renombrar una de las dos opciones o un booleano propio (decisión del dueño/coordinador).
2. `include build` ya exigía estar dentro de un repo git (`include/invalid` fuera); por eso «fuera de git no crea puntero» solo es alcanzable como repo sin commits, que es lo que prueba el test. `point-at-head!` igualmente no escribe ni falla en ambos casos.
3. `seen --set` no valida el puntero anterior: `:previous` sale tal cual (nil si ilegible), así un puntero dañado se repara con `--set`. `seen` sin `--set` y `changed` sin `--since` sí validan (error declarado).
4. `:pointer` en `changed` con `--since` es la lectura cruda del archivo (no se valida, para que `--since` no dependa de un archivo dañado).
5. `seen.edn` vive dentro del directorio del store pero fuera del log; `stores check/verify` no se quejan de un archivo extra (las suites dirigidas pasan), no se probó cada diagnóstico por separado. Un store movido por `--rebuild` se lleva su `seen.edn` al `.bak`; el nuevo recibe el suyo.
6. Riesgo de diseño: un puntero que avanza solo con `--set`/`include build` puede quedar atrás sin que nadie se dé cuenta; `:behind` lo hace visible en `seen`, pero `changed` no lo avisa (muestra `:since` y `:pointer`, y `:head`).
7. Dos procesos que mueven el puntero a la vez: última escritura gana (rename atómico, sin lock).
