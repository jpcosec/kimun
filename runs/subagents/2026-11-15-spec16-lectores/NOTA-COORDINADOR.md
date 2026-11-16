# Nota del coordinador

El ejecutor hizo que `stores update` **omitiera** un documento spec 16 `:superseded`, por
considerar que releer el archivo bajo la clase nueva es una decisión. En paralelo, M1h hizo
que `stores update` lo **reparara**. Las dos lecturas chocaban (4 pruebas de M1h fallaban al
apilar). Se siguió el spec: `docs/v2/10-escala.md §11.1` lista el cambio de **clase** con la
fuente intacta como `contract/changed`, de reparación segura por `docs update` / `stores
update`. El cambio de `stores.cljc` de este corte se retiró y su prueba 3 se reescribió:
`stores update` re-ancla el documento bajo la clase vigente sin reescribir el archivo, en una
revisión que el journal nombra.

Queda como pregunta para el dueño: para una clase spec 16 no hay renderizador que conserve
el payload (como sí lo hay para v1), así que esa «reparación segura» puede cambiar lo que
queda anclado. `REPORT.md` describe el comportamiento que NO se integró.
