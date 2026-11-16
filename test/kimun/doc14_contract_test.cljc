(ns kimun.doc14-contract-test
  "Contract checks for DOC1 (`docs/v2/14-juego-del-lenguaje.md`): thematic
   reorganization, integration of addenda §16-§21, explicit separation between
   target contract, implemented state, open decisions (D0/D-G) and historical
   records, preservation of incoming citations, and F18 reconciliations."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- read-doc14
  "Read `docs/v2/14-juego-del-lenguaje.md` from the active working tree."
  []
  (slurp (str (fs/path (System/getProperty "user.dir") "docs" "v2" "14-juego-del-lenguaje.md"))))

(defn- section-slice
  "Return the substring starting at heading `h` up to the next `## ` heading (or EOF)."
  [doc heading]
  (when-let [idx (str/index-of doc heading)]
    (let [after (+ idx (count heading))
          next-h (str/index-of doc "\n## " after)]
      (if next-h
        (subs doc idx next-h)
        (subs doc idx)))))

(deftest doc14-thematic-structure-and-preserved-sections
  (let [doc (read-doc14)]
    (testing "all six thematic parts are present"
      (doseq [theme ["# Tema I —" "# Tema II —" "# Tema III —" "# Tema IV —" "# Tema V —" "# Tema VI —"]]
        (is (str/includes? doc theme) (str "Missing theme header: " theme))))
    (testing "all numbered section anchors ## 1. through ## 21. remain present"
      (doseq [n (range 1 22)
              :let [prefix (str "\n## " n ". ")]]
        (is (str/includes? doc prefix) (str "Missing section heading prefix: " prefix))))
    (testing "every §18 subpoint 1.-9. and §16 / §10 / §13 incoming citation anchor is preserved"
      (let [s18 (section-slice doc "## 18. ")
            s10 (section-slice doc "## 10. ")
            s13 (section-slice doc "## 13. ")]
        (is (some? s18))
        (doseq [k (range 1 10)]
          (is (re-find (re-pattern (str "(?m)^" k "\\. ")) s18)
              (str "Missing §18." k)))
        (doseq [needle ["etapa S.2" "10 §11"]]
          (is (str/includes? s13 needle) (str "Missing §13 anchor: " needle)))
        (doseq [needle [":content :inline" "markdown/source"]]
          (is (str/includes? s10 needle) (str "Missing §10 anchor: " needle)))))))

(deftest doc14-principles-decisions-and-open-d0-boundaries
  (let [doc (read-doc14)]
    (testing "principles P1 through P15 are integrated in §1 and §16 keeps anchor references"
      (let [s1 (section-slice doc "## 1. ")
            s16 (section-slice doc "## 16. ")]
        (is (some? s1))
        (is (some? s16))
        (doseq [n (range 1 16)]
          (is (re-find (re-pattern (str "\\*\\*P" n " —")) s1)
              (str "Missing P" n " in §1")))))
    (testing "decisions 21..46 and questions Q1..Q15 remain indexed"
      (let [s12 (section-slice doc "## 12. ")
            s17 (section-slice doc "## 17. ")]
        (doseq [d (range 21 47)]
          (is (re-find (re-pattern (str "(?m)^\\| " d " \\|")) s12)
              (str "Missing decision row " d " in §12")))
        (doseq [q (range 1 16)]
          (is (re-find (re-pattern (str "(?m)^\\| Q" q " \\|")) s17)
              (str "Missing Q" q " row in §17")))))
    (testing "2026-11-14 owner directions remain intact; D0 is the S–M contract the council derived from them, not a question pending for the owner, and its rules are labelled as council inferences"
      (let [s21 (section-slice doc "## 21. ")]
        (doseq [phrase ["no es el hash ni su anclaje estructural en la topología"
                        "no aprueba CAS, UUID ni otra codificación"
                        "Autopoiesis y VSM aplican al sistema, no a la unidad"
                        "Realidad y hecho son cosas\n> distintas"
                        "D0 no es una pregunta pendiente al dueño"
                        "D0: contrato S–M derivado"]]
          (is (str/includes? doc phrase) (str "Missing owner/D0 guard: " phrase)))
        (is (not (str/includes? doc "D0 ABIERTO")) "the obsolete block is gone")
        (is (str/includes? s21 "sustituye"))
        (doseq [needle ["inferencias del concilio" "no hay\n> pregunta pendiente al dueño"
                        "hecho detectado" "anclaje" "observación"
                        "Una unidad no se bifurca ni se funde"
                        "El sensor propone; el cierre, la continuidad y la proyección entre contextos se declaran"
                        "La ausencia no termina una unidad"
                        "proyección declarada"
                        "Sigue sin aprobar CAS, UUID ni otra codificación"
                        "pendiente tras `D0`" "Implementación (`K1`)" "Diseño de G, no de S–M"
                        "runs/research/2026-11-15/concilio/d0-contrato-sintesis.md"]]
          (is (str/includes? s21 (str/replace needle "\\n" "\n")) (str "Missing D0 contract marker in §21: " needle)))
        (is (not (str/includes? s21 "ABIERTO en `D0`")))))))

(deftest doc14-resolves-f18-contradictions-across-strata
  (let [doc (read-doc14)
        s5 (section-slice doc "## 5. ")
        s10 (section-slice doc "## 10. ")
        s12 (section-slice doc "## 12. ")
        s15 (section-slice doc "## 15. ")
        s17 (section-slice doc "## 17. ")
        s18 (section-slice doc "## 18. ")]
    (testing "P15 aligns with decision 38 and marks Q13 initial default as discarded"
      (is (str/includes? (section-slice doc "## 1. ") "sin idioma fijo ni"))
      (is (str/includes? (section-slice doc "## 7. ") "modelo **multilingüe**"))
      (is (str/includes? s17 "default descartado")))
    (testing "historical §10 points to 10-escala.md for the formats that are no longer opened"
      (is (str/includes? s10 "`stores upgrade` ya no existe"))
      (is (str/includes? s10 "10-escala.md")))
    (testing ":contract edge is explicitly located on `reference`, not `derived`"
      (is (str/includes? s18 "arista `reference`"))
      (is (str/includes? s15 "`reference`"))
      (is (str/includes? s12 "`reference`")))
    (testing "D-G decisions 24 and 31 remain marked EN REVISIÓN"
      (is (str/includes? s5 "EN REVISIÓN (`D-G`"))
      (is (re-find #"(?m)^\| 24 \|.*EN REVISIÓN" s12))
      (is (re-find #"(?m)^\| 31 \|.*EN REVISIÓN" s12)))))

(deftest doc14-tracks-exact-stage-and-commit-status
  (let [doc (read-doc14)
        s12 (section-slice doc "## 12. ")
        s13 (section-slice doc "## 13. ")]
    (is (some? s12))
    (is (some? s13))
    (testing "§13 distinguishes S1 base, S1 phase 2 (~3%), and S2 phase 3 waiting for K1"
      (is (str/includes? s13 "etapa S.2"))
      (is (str/includes? s13 "65ca4b5f"))
      (is (str/includes? s13 "~3 %"))
      (is (str/includes? s13 "**`S2` fase 3"))
      (is (str/includes? s13 "**espera a `K1`**"))
      (is (not (str/includes? s13 "Bloqueados por `D0`"))))
    (testing "§13 and decision 44 mark K2a as partial: C2a/C2b/C4/C5 implemented, schema 2 the tree, the fan-out cost still open"
      (is (not (str/includes? s13 "**`K2a` (cerrada):**")))
      (doseq [needle ["C2a" "C2b" "C4" "C5" "Merkle incremental" "b4931b7c"
                      "esquema 2" "fan-out"]]
        (is (str/includes? s13 needle) (str "Missing K2a/C5 marker in §13: " needle))
        (is (str/includes? s12 needle) (str "Missing K2a/C5 marker in §12: " needle)))
      (is (str/includes? s13 "2c56e680"))
      (is (not (str/includes? s13 "sigue pendiente `C5`")) "C5 is no longer pending in §13")
      (is (not (str/includes? s12 "pendiente en `C5`")) "nor in decision 44"))
    (testing "§20 states route schema 2 and the limits of C5 without closing D0"
      (let [s20 (section-slice doc "## 20. ")]
        (doseq [needle ["esquema 2" "b4931b7c"
                        "no hay fan-out" "route-coverage" "`K1`/`D0`" "runs/subagents/2026-11-15-C5/EVIDENCE.md"]]
          (is (str/includes? s20 needle) (str "Missing C5 marker in §20: " needle)))
        (is (not (str/includes? s20 "Pendiente en `K2a` (`C5`")) "C5 is not listed as pending")))
    (testing "§13 records closed K3/K8/C1, K6 Markdown with rebuild-mismatch implemented, and P1 first cut"
      (doseq [needle ["076be35b" "43d9db6d" "bcd85a53" "555b2dbc" "rebuild-mismatch" "contract/rebuild-mismatch" "70b8abe9" "971b6b2c"]]
        (is (str/includes? s13 needle) (str "Missing status marker in §13: " needle))))))
