---
name: assigned_to
direction: directed
cardinality: many_to_one
axis: WHERE
source_types:
- Booking
target_types:
- Table
condition: capacity >= {party_size}
---

# assigned_to

## Description

assigned_to.
