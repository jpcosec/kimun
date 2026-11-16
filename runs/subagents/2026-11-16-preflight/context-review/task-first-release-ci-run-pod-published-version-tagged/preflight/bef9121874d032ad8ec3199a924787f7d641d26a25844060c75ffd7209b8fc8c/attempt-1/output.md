REFORMULACION
Tarea ejecutable (`task.code`), no agregado. Propósito: ejecutar por primera vez el CI de release, publicar el pod, fijar su hash, probar las plataformas restantes y documentar la instalación. Resultado: “An installable, tagged kimun.” Repo: kimun (según ruta/título); raíz exacta no declarada. Límites: “.github/workflows/, scripts/release.clj, release.edn, install docs.” Contexto: “Approved plan stage 6”; “release.edn has :sha256 nil; only linux-amd64 was built and tested.” La validación está vacía.

AMBIGUEDADES
1. “publish the pod”: no define registro, coordenadas, versión, credenciales ni formato del pod. Obligaría a inventar destino y procedimiento de publicación.
2. “tagged”: no indica nombre/formato del tag ni si el tag dispara el CI o se crea después. Obligaría a inventar política de versionado.
3. “pin its hash”: no dice dónde fijarlo, qué artefactos cubre ni formato. Solo se infiere `release.edn :sha256`, pero no se afirma.
4. “test the other platforms”: no enumera plataformas/arquitecturas ni método de prueba. Obligaría a inventar matriz y criterios.
5. “document install”: no indica archivo, formato, requisitos ni público. Obligaría a inventar ubicación y contenido.
6. “The bundle installs on a clean machine and the tour runs”: no define “bundle”, “clean machine” ni comando/alcance del “tour”. Obligaría a inventar aceptación observable.
7. “Approved plan stage 6”: remite a un plan externo no incluido. Obligaría a inventar sus detalles.
8. “Validation”: sección vacía; no hay checks verificables. Obligaría a inventar criterios de cierre.

DRY-RUN
Lecturas: TaskDoc, `.github/workflows/`, `scripts/release.clj`, `release.edn`, docs de instalación y artefactos linux-amd64 existentes. Cambios previstos: ejecutar/ajustar workflow de release, publicar pod, actualizar hash en `release.edn`, probar plataformas restantes y documentar instalación. Dependencias: credenciales/registro, tag/versión, runners, artefactos y plan stage 6. Validaciones futuras: CI verde, hash verificado, pod descargable, instalación limpia y tour. Sin ejecutar nada.

FUTURO
Un humano debe decidir versión/tag, registro y credenciales, plataformas objetivo y criterio de “clean machine”/“tour”. Otro nodo debe aportar el plan stage 6, el CI de release y los artefactos previos.
