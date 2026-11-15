#!/usr/bin/env bash
# Recorrido observable de una mutación (T-V1, docs/v2/12 §1 «Operación y vista»).
#
# Demuestra, usando solo la CLI, que kimun ve una mutación de la fuente, la
# declara, la reconcilia, distingue un cambio de contrato de lectura (K6),
# declara un re-anclaje (K1b) y late. No crea un comando nuevo ni toca src/.
#
# Uso:  bash scripts/tour_mutation.sh
# Requiere: bb, memcap (~/bin/memcap), python3. El pod tree-sitter es opcional:
# sin él, el tramo K6 se omite y se dice.
#
# El proyecto vive en un directorio temporal y se borra al salir. No se toca
# ningún store real.

set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
BB=${BB:-bb}
CP="$ROOT/src:$ROOT/resources"
P=$(mktemp -d "${TMPDIR:-/tmp}/kimun-tour-XXXXXX")
cleanup() { rm -rf "$P"; }
trap cleanup EXIT

say()  { printf '\n=== %s\n' "$*"; }
note() { printf '    %s\n' "$*"; }

# TOUR_PARSER, si está puesto, es el parser con el que se lee (tramo K6)
kimun() { ( cd "$P" && env ${TOUR_PARSER:+KIMUN_PARSER=$TOUR_PARSER} ~/bin/memcap 3 "$BB" -cp "$CP" -m kimun.cli.main "$@" ); }
json()  { python3 -c "import json,sys;d=json.load(sys.stdin);print(json.dumps(eval(sys.argv[1])))" "$1"; }
id8()   { local s="$1"; [ ${#s} -gt 8 ] && printf '%s…' "${s:0:8}" || printf '%s' "$s"; }

# sha256 de todo el store: la huella con la que se comprueba que observar no escribe
store_sha() {
  ( cd "$P" && find .kimun -type f -print0 | sort -z | xargs -0 sha256sum | sha256sum | cut -d' ' -f1 )
}

# Ejecuta un comando de OBSERVACIÓN y falla si el store cambia (devuelve el JSON en stdout)
observe() {
  local label="$1"; shift
  local before after out
  before=$(store_sha)
  out=$(kimun "$@")
  after=$(store_sha)
  if [ "$before" != "$after" ]; then
    printf 'FALLO: `%s` escribió en el store (sha %s → %s)\n' "$label" "${before:0:8}" "${after:0:8}" >&2
    ( cd "$P" && find .kimun -type f | sort ) >&2
    return 1
  fi
  note "observación sin escrituras: $label (sha ${before:0:8})" >&2
  printf '%s' "$out"
}

# ---------------------------------------------------------------- paso 0: proyecto

say "paso 0 · proyecto temporal (stores init + modelo + un documento tipado y uno genérico)"
mkdir -p "$P/docs" "$P/notes"
cat > "$P/ticket.edn" <<'EDN'
{:model/name "TicketDoc" :model/version 1 :base []
 :fields [{:name "title" :type :string :required true :description "Title."}
          {:name "status" :type :string :required true :description "Status."}]
 :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}
EDN
printf '# T\n\nStatus: open\n' > "$P/docs/a.md"
printf '# Note\n\none\n'      > "$P/notes/plain.md"
kimun stores init --name tour >/dev/null
kimun models define -f ticket.edn >/dev/null
kimun docs track TicketDoc docs/a.md >/dev/null
kimun docs ingest notes/plain.md >/dev/null
note "store en $P/.kimun"

# ---------------------------------------------------------------- paso 1: reposo

say "paso 1 · reposo"
S=$(observe "stores sense (reposo)" stores sense)
note "señales: $(printf '%s' "$S" | json 'len(d["data"]["signals"])')  · valid=$(printf '%s' "$S" | json 'd["data"]["valid"]')"
H=$(observe "stores health (reposo)" stores health)
note "health: $(printf '%s' "$H" | json 'd["data"]["status"]')  · next=$(printf '%s' "$H" | json 'd["data"]["next"]')"

# ---------------------------------------------------------------- paso 2: mutación de la fuente

say "paso 2 · mutación de la fuente (se edita docs/a.md en disco)"
printf '# T\n\nStatus: closed\n' > "$P/docs/a.md"
S=$(observe "stores sense (fuente mutada)" stores sense)
note "señales: $(printf '%s' "$S" | json '[s["type"] for s in d["data"]["signals"]]')"
note "campos que cambiaron (según la señal): $(printf '%s' "$S" | json 'd["data"]["signals"][0]["detail"]["fields"]')"
H=$(observe "stores health (fuente mutada)" stores health)
STATUS=$(printf '%s' "$H" | json 'd["data"]["status"]')
NEXT=$(printf '%s' "$H" | json 'd["data"]["next"]')
ACTIONS=$(printf '%s' "$H" | json 'd["data"]["actions"]')
note "health: status=$STATUS"
note "next: $NEXT"
note "actions: $ACTIONS"
note "\":next\" pide una INSPECCIÓN, no es un comando: el circuito se detiene aquí"
note "hasta que alguien decide (status=$STATUS)."

say "paso 2b · inspección (lo que :next pide: mirar qué cambió)"
note "docs status: $(kimun docs status | json 'd["data"]')"
note "la fuente registrada frente al archivo: los campos que la señal nombra arriba."

say "paso 3 · decisión declarada y reconciliación"
note "el recorrido SIMULA aquí la decisión explícita de un actor: nadie revisó el cambio;"
note "el guion solo consultó docs status y la señal, y aplica la primera acción propuesta."
ACTION=$(printf '%s' "$ACTIONS" | python3 -c "
import json,sys
a=json.load(sys.stdin)
print(a[0] if a else '')")
[ -n "$ACTION" ] || { echo "FALLO: health no propuso ninguna acción" >&2; exit 1; }
note "acción elegida (leída de :actions, no fijada de antemano): $ACTION"
# la acción es un comando de la CLI: se ejecuta tal cual. El actor es el por
# defecto (human/$USER): un store nuevo solo da capacidades a él, así que
# `--actor tour` sería rechazado por el plan (capability).
kimun $(printf '%s' "$ACTION" | sed 's/^kimun //') >/dev/null
H=$(observe "stores health (reconciliado)" stores health)
note "health: $(printf '%s' "$H" | json 'd["data"]["status"]')"
J=$(kimun journal show --limit 1)
note "journal: operation=$(printf '%s' "$J" | json 'd["data"][0]["operation"]') fields=$(printf '%s' "$J" | json 'sorted(d["data"][0].get("fields", {}).keys())') actor=$(printf '%s' "$J" | json 'd["data"][0]["actor"]')"

# ---------------------------------------------------------------- paso 4: contrato de lectura (K6)

say "paso 4 · cambio del contrato de lectura (K6): la misma fuente bajo tree-sitter"
POD=$(cd "$ROOT" && ~/bin/memcap 3 "$BB" -cp "$CP" -e "(require '[kimun.host.treesitter :as ts]) (print (ts/available?))" 2>/dev/null || echo false)
if [ "$POD" = "true" ]; then
  TOUR_PARSER=treesitter
  S=$(observe "stores sense (KIMUN_PARSER=treesitter)" stores sense)
  note "señales (KIMUN_PARSER=treesitter): $(printf '%s' "$S" | json '[s["type"] for s in d["data"]["signals"]]')"
  note "detalle del contrato: $(printf '%s' "$S" | json 'd["data"]["signals"][0]["detail"]["contract"]')"
  H=$(observe "stores health (KIMUN_PARSER=treesitter)" stores health)
  note "health: $(printf '%s' "$H" | json 'd["data"]["status"]')  · next: $(printf '%s' "$H" | json 'd["data"]["next"]')"
  note "aquí :next sí es una reparación sin decisión (\"run: …\"), a diferencia del caso anterior"
  note "reparación sin decisión: stores update (no docs update)"
  kimun stores update >/dev/null
  S=$(observe "stores sense (tras stores update, mismo parser)" stores sense)
  note "señales tras stores update (mismo parser): $(printf '%s' "$S" | json '[s["type"] for s in d["data"]["signals"]]')"
  TOUR_PARSER=
  # el contrato queda registrado con el parser en vigor: se vuelve al nativo con
  # la MISMA reparación sin decisión, y se dice (no se oculta el cambio inverso)
  kimun stores update >/dev/null
  S=$(observe "stores sense (de vuelta al parser nativo)" stores sense)
  note "de vuelta al parser nativo, señales: $(printf '%s' "$S" | json '[s["type"] for s in d["data"]["signals"]]')"
else
  note "tramo K6 omitido: pod no disponible"
fi

# ---------------------------------------------------------------- paso 5: mover (K1b)

say "paso 5 · mover el archivo genérico en disco (K1b: el movimiento se declara)"
# el id de árbol se lee de docs list (docs show sin --ast no resuelve un documento genérico)
T0=$(kimun docs list | json '[x["tree"] for x in d["data"] if x["name"]=="plain"][0]')
mkdir -p "$P/b"
mv "$P/notes/plain.md" "$P/b/plain.md"
S=$(observe "stores sense (archivo ausente)" stores sense)
note "señales: $(printf '%s' "$S" | json '[s["type"] for s in d["data"]["signals"]]')"
note "la ausencia no termina la unidad: solo informa."
kimun docs reanchor plain b/plain.md --reason "movido a mano" >/dev/null
T1=$(kimun docs list | json '[x["tree"] for x in d["data"] if x["name"]=="plain"][0]')
note "tree antes=$(id8 "$T0") después=$(id8 "$T1")  · igual=$( [ "$T0" = "$T1" ] && echo sí || echo NO )"
S=$(observe "stores sense (tras re-anclar)" stores sense)
note "señales: $(printf '%s' "$S" | json '[s["type"] for s in d["data"]["signals"]]')"
J=$(kimun journal show --limit 1)
note "journal: operation=$(printf '%s' "$J" | json 'd["data"][0]["operation"]') declared=$(printf '%s' "$J" | json 'd["data"][0].get("declared")')"

# ---------------------------------------------------------------- paso 6: latido

say "paso 6 · latido (stores heartbeat --beats 2)"
before=$(store_sha)
BEATS=$(kimun stores heartbeat --beats 2)
after=$(store_sha)
[ "$before" = "$after" ] || { echo "FALLO: heartbeat escribió en el store" >&2; exit 1; }
note "latidos: $(printf '%s\n' "$BEATS" | wc -l | tr -d ' ') registros; segundo latido changed=$(printf '%s\n' "$BEATS" | python3 -c "
import json,sys
bs=[json.loads(l) for l in sys.stdin if l.strip()]
beats=[b for b in bs if b.get('kind')=='beat']
print(beats[1].get('changed') if len(beats)>1 else 'n/a')")"
note "changed es memoria de este proceso, no un evento guardado; observed-at es la hora del registro."

say "recorrido completo"
