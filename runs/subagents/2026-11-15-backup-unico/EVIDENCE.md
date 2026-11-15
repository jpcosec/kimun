# Fix — el respaldo de `include build --rebuild` tiene nombre único

Base `9b8cc887` (K1d). Defecto previo, encontrado por Pi al probar K1d: el respaldo se llamaba
`<store>.bak-<yyyyMMdd-HHmmss>`; dos `--rebuild` en el mismo segundo elegían el mismo nombre
y el segundo fallaba.

## Qué cambia

- Nombre: `<store>.bak-<marca>-<ULID>`. El movimiento no reemplaza: un respaldo existente
  nunca se sobrescribe.
- La marca de tiempo sale de `backup-stamp`, para poder fijarla en la prueba.
- La prueba de K1d ya no duerme 1,1 s entre rebuilds.

## Alcance: secuencia, no concurrencia

`include build` no toma ningún lock alrededor del movimiento. Este fix evita la colisión de
nombres de respaldo; **no** coordina dos rebuilds concurrentes del mismo store (los dos leen
el registro de identidades y los dos reconstruyen). Queda dicho en el código y en `13-include.md`.

## Validación (árbol final, memcap, con pod)

| paso | archivo | resultado |
|---|---|---|
| `bb lint` | `lint.txt` | ok |
| dirigidas (5 namespaces) | `directed.txt` | 28 tests, 236 aserciones, 0 fallos |
| mutación (se quita solo el sufijo ULID) | `mutation.txt` | `two-rebuilds-in-a-row-keep-two-backups` falla (y la aserción del nombre en `what-cannot-be-carried-is-declared`) |
| `bb test` | `full-test.txt`, `full-exit.txt` | 1142 tests, 12635 passes, 0 fallos, 0 errores, 138 namespaces, pod disponible |
| `bb oracle` | `oracle.txt` | 9 filas, 0 diferencias |
| `bin/kimun include check` | `include-check.txt` | ok |

## Lo que salió mal antes de esto

La primera versión de la prueba buscaba los respaldos con un patrón que no veía el
directorio oculto y reventaba antes de afirmar. La segunda pasaba **también sin el fix**:
cada rebuild tarda cerca de un segundo y los dos caían en segundos distintos. Por eso la
prueba fija el reloj: los dos rebuilds ven el mismo segundo, que es el caso que fallaba.
