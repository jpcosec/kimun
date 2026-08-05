import os
import re

directories = [
    "desk/atoms",
    "desk/tasks",
    "desk/drawer/features",
    "docs/architecture/contracts",
    "docs/architecture/spec2viz",
    "docs",
    "."
]

def rename_files():
    for d in directories:
        if not os.path.isdir(d):
            continue
        for filename in os.listdir(d):
            if "rust" in filename.lower() and filename.endswith(".md"):
                new_name = filename.replace("rust", "clojure").replace("Rust", "Clojure")
                os.rename(os.path.join(d, filename), os.path.join(d, new_name))
                print(f"Renamed {filename} to {new_name}")

def replace_content():
    for d in directories:
        if not os.path.isdir(d):
            continue
        for filename in os.listdir(d):
            if filename.endswith(".md") or filename.endswith(".yml") or filename.endswith(".yaml"):
                filepath = os.path.join(d, filename)
                if not os.path.isfile(filepath): continue
                with open(filepath, "r") as f:
                    content = f.read()
                
                # We do some targeted replacements
                new_content = content.replace("Rust kernel", "Clojure kernel")
                new_content = new_content.replace("rust_kernel_backend", "clojure_kernel_backend")
                new_content = new_content.replace("rust-core", "clojure-core")
                new_content = new_content.replace("Rust", "Clojure (Babashka)")
                new_content = new_content.replace("rust-testing", "clojure-testing")
                new_content = new_content.replace("rust-patterns", "clojure-patterns")
                new_content = new_content.replace("rust-code", "clojure-code")
                new_content = new_content.replace("- rust-", "- clojure-")
                new_content = new_content.replace("petgraph", "DataScript/Asami")
                
                if new_content != content:
                    with open(filepath, "w") as f:
                        f.write(new_content)
                    print(f"Updated {filepath}")

if __name__ == "__main__":
    rename_files()
    replace_content()
