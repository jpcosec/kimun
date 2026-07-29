import os
import re

for r, d, f in os.walk('desk/routines'):
    for file in f:
        if file.endswith('.md'):
            path = os.path.join(r, file)
            with open(path, 'r', encoding='utf-8') as fh:
                content = fh.read()
            
            # if steps: is not in the yaml frontmatter, add it
            if 'steps:' not in content:
                new_content = re.sub(r'(decomposition:.*?\n)', r'\1steps: []\n', content, flags=re.DOTALL)
                if new_content != content:
                    with open(path, 'w', encoding='utf-8') as fh:
                        fh.write(new_content)
                    print(f"Fixed {path}")
