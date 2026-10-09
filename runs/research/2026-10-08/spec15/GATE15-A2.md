# GATE15 — Carril A (ronda 2)

Auditor sin contexto. Solo lectura. Repo `kimun-worktrees/spec15`, rama `close/spec15`
(`c3551476`). Auditado: `docs/v2/15-capa-g.md` + `test/fixtures/g/*.edn`.
No leídos: informes de ronda 1 ni `SPEC15-R1-report.md`.

## Hallazgos

**H1 — bloqueante.** 15 §1: «Ingestar el vocabulario (`contexts define`, §9) crea, en una
transacción: … y los hechos de propiedad `(property R p) @ W_i` de §4». Choca con 15 §1:
«**`:relations` no lleva `:properties`**» y con los fixtures: `vocabulary.edn` no declara
ningún `p`, y `facts.edn` produce `:fact/f2`, `:fact/f2b` y `:fact/f4` con evidencia de motor
(`:engine "descompositor/heuristic"`) y `:prov` sobre spans de `source.md`, no del
documento de vocabulario. El propio `facts.edn` afirma que f2/f2b/f4 son «la única fuente
del álgebra». Corrección mínima: quitar de §1 que `contexts define` crea los hechos de
propiedad, y decir que se afirman con su propia evidencia (como en el fixture).

**H2 — grave.** 15 §7: «`source_hash` … es **`sha-256:<hex>`** del archivo fuente …
la misma clave y etiqueta en §3, §7 y los fixtures»; y 15 §10: «`:source-hash` es una
sola clave (`sha-256:<hex>`) en §3, §7 y fixtures». Choca con `11 §4.1`, que documenta el
campo del protocolo que el perfil `propositions` dice extender/«compartir»: «`source_hash`
| `"sha256:<hex>"` o null». `15 §11` revisa `11` («perfil `propositions` … y despacho por
perfil») pero no declara el cambio de etiqueta. Corrección mínima: añadir `11 §4.1` a la
tabla «Revisa» con la etiqueta canónica `sha-256:<hex>` (`02 §6.4`, decisión 11) —o emitir
`sha256:`.

**H3 — grave.** 15 §6: «una arista `binding` S→M (span → término/proposición) con `:prov`
y `:context` en su evidencia». Los fixtures no lo cumplen: `facts.edn` `:edge/b1` es
`{:alias :edge/b1 :type :binding :from :sign/span1 :to :prop/f1 :evidence {:ref-hash
:prop/f1 :context :ctx/doc :actor "human/jp"}}` (sin `:prov`); igual `b2`, `b2b`, `b3`,
`b4`. Solo las `projection` llevan `:prov`. Además `15 §13` afirma que
`fixtures_test.cljc` comprueba los fixtures «campo a campo», pero ese test no verifica
`:prov` en `binding`. Corrección mínima: añadir `:prov` a la evidencia de los `binding` del
fixture (o retirar el requisito de §6).

**H4 — menor.** 15 §1 fija la taxonomía con `:taxonomies [{:name "terms" :root "thing"}
…]` y «`:axis [t1 t2]` cita la **raíz**», donde `:root` es un **nombre**. Choca con
`02 §3.1`, donde el mismo campo de descriptor es un id: «{:tree <id> :kind …
:root <node-id>}», y con `context.edn`, que usa `:tree {:tree :tree/ctx-doc :kind :context
:name "…" :root :node}` (alias/id). Corrección mínima: distinguir en §1 el `:root` del
vocabulario (nombre) del `:root` del descriptor de árbol (id).

**H5 — menor.** 15 §2: «La pertenencia es un hecho `(member X <term>) @ W_transversal`».
Choca con 15 §1: «El vocabulario solo declara qué relaciones se admiten», y no hay en todo
el 15 ninguna forma de vocabulario `L_i` para los contextos `:granularity :transversal`
que declare la relación `member`. Corrección mínima: declarar el vocabulario de los
transversales (o eximirlos) para que `member` sea admisible.

**H6 — menor.** 15 §12 Q7: «declaración del vocabulario `L_i` en el repo (`13` es el
dueño)». La existencia del vocabulario como documento `source` ya está fijada por
`14 §4` («El **vocabulario admisible** de un contexto … es un documento `source` en EDN»)
y por `05` decisión 23; cómo lo declara `kimun.include.edn` es tema de `13`, que el propio
15 nombra dueño. Corrección mínima: sacar Q7 de §12 (o remitirla al dueño de `13`), no al
dueño del producto.

**H7 — menor.** 15 §3 fija la forma del estado como resuelta («`:status` es el **estado de
decisión**», con `:proposed` incluido y usado en `§7` y los fixtures), pero 15 §12 Q1 la
declara abierta: «(a) ampliar `:status` con `:proposed`; (b) `:status` de decisión +
`:curation … aparte`». `14 §5` enumera «`:status ∈ #{observed confirmed rejected
retracted}`». Corrección mínima: marcar el `:proposed` de §3/§7/fixtures como el default
pendiente de Q1.

**H8 — menor.** 15 §9: «`facts assert` lee un `:fact/entry` …; sin `--write`/`assert`,
`facts plan` solo muestra el `TransactionPlan` planificado». La tabla de §9 no incluye
`plan` en el grupo `facts`: «`facts` | `list …` · `show ID` · `assert FILE\|-` · `retract
ID [--note N]` · `consolidate …` · `anchors ID`». Corrección mínima: añadir `plan
[FILE|-]` a la fila `facts`.

**H9 — menor.** 15 §5 marca `:candidates` como «solo `:ambiguous`», pero
`conflict.edn` incluye `:candidates []` en un `:kind :contradiction`. Corrección mínima:
omitir `:candidates` en las contradicciones o declararlo `[]` cuando no aplica.

**H10 — menor.** 15 §13: «cinco hechos … y **el hecho de propiedad** `(property R p)` como
única fuente del álgebra». `facts.edn` tiene tres hechos de propiedad (`f2`, `f2b`, `f4`).
Corrección mínima: pluralizar («los hechos de propiedad»).

## Ambigüedades

**Am1.** 15 §1: «sus hojas son ids de término o literales canónicos» (para `:symbol/:proposition`)
frente a 15 §1: «Un literal se representa como un término de la taxonomía `literals`» y
«**:Revisa** `02 §2.1` (`:object <id|literal>` pasa a `:object <id>`)». Lectura (i): todo
literal es siempre un término, también en `:form`; lectura (ii): solo el hecho `:triple`
fuerza ids, la proposición conserva literales crudos (`02 §2.1`: «args son ids de símbolos
o literales»).

**Am2.** 15 §2: «aristas **promovidas** … `equivalent` … y `projection` (unidireccional)».
`projection` aquí nombrea la relación de ruteo entre contextos, pero `02 §3.2` y 15 §3 usan
`projection` como la arista M→G proposición→hecho. Lectura (i): son el mismo tipo con
`:relation` en evidencia; lectura (ii): la relación de ruteo es un valor de `:semantic`
distinto del tipo de arista `projection`.

**Am3.** 15 §2: «`:of` referencia **rutas y spans relativos**, nunca ids nominales del
store». Lectura (i): vale para todo `:of`; lectura (ii): la forma de transversal
`{:transversal "data-class"}` es una excepción no-ruta.

**Am4.** 15 §2: «la política `:promotions` del vocabulario (§1) es la que decide qué hechos
promocionan (`:to :confirmed`) cuando la equivalencia se confirma» y, a la vez, «una
pregunta cruza mundos solo por aristas **promovidas**». Lectura (i): la promoción promueve
hechos y el ruteo se sigue de ellos; lectura (ii): la propia arista `:semantic` de ruteo
tiene estado/tier que hay que promocionar.

**Am5.** 15 §5: el conflicto lo produce el evaluador «al unir contextos por ruteo promovido
o al **promover** un hecho que vuelve inconsistente un `W_i`», pero `conflict.edn` declara
una contradicción entre `:fact/f2` y `:fact/f2b`, ambos `:status :proposed` y `:tier
:statistical`, ya presentes en el mismo `W_i` sin promoción; 15 §3 dice «**`statistical`
nunca deduce**». Lectura (i): la detección de contradicción no es deducción y ve hechos
`statistical`; lectura (ii): sin promoción no debería haber informe.

## Veredicto

**ABIERTO.** Bloqueante: H1. Graves: H2, H3. Menores: H4–H10.
