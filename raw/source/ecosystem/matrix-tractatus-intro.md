
Wittgenstein's Tractatus opens with an interesting phrase: **the world is composed of all facts, not things**. Let's define things as the atoms that index what can be said (music, theorem, dog, color), and note that things in the abstract mean nothing; they only acquire meaning through their relationships. One thing is related to another through facts (the dog is white, the music is playing, the number adds). Therefore, what allows us to represent the world and give it a logical structure is not the empty enumeration of the things that compose it, but the totality of their relationships.

A proposition is a description of a fact in relation to reality; a fact that is expressed perceptibly through some medium (sound, written sign, bits, etc.). A proposition can be true or false not according to the fact it describes, but according to its relationship with reality. A proposition is meaningful according to its capacity to demonstrate the truth of the fact and its capacity to be articulated within a logical framework. Based on its state of meaning, a proposition can be distinguished into three states.

* Sinnvoll: propositions that are meaningful and capable of representing a possible situation in the world. For example, in Spain the climate is warm, Beethoven wrote his Ninth Symphony, electromagnetic phenomena can be described by Maxwell's four elementary equations, etc.

* Sinnlos: propositions that say nothing about the world but legitimately belong to logical symbolism, such as tautologies and contradictions. For example, goes up upwards, a person is a human, Schrödinger's cat is both dead and alive at the same time.

* Unsinnig: formulations without logical meaning, where the symbols have not been given a significant function within the system; in other words, absurd propositions. The dog rides, the reason for unreason, p = ~p, etc.

A sign is the perceptible medium that allows the mapping and transmission of a fact, an object, or their relationships. It can be a sound signal, a quality of a sound signal, written text, an image, music, a theorem, a code, among many others. The sign is not the fact itself, but rather a mapping of the fact to that which can be expressed. A fact can be expressed in many signs; the dog is friendly, the canine is reliable, and the dog is man's best friend all express the closeness of both specimens; 3 in Arabic notation, 00000011 in binary, and drei in German designate the same numerical quantity. Similarly, a sign can represent different symbols;  in English, gift is an object given for free, while in German it means poison; the act of nodding one's head vertically is commonly accepted as an affirmative gesture, but in Bulgaria it means no.

A symbol is a sign and a structure within a logical space, used within a context; that is, within a sufficiently defined partition of the world. A name is a reference to this logical structure, that is, a mapping between sign and symbol, sufficiently determined by the context to distinguish it from other symbols. 

Let's then try describing the logical structure of a partition of the world $W_i$ by the following symbolic set: 

1- A context, that is, a partition over the world $W$ that limits what belongs into $W_i$. 
2- A logical space $L_i$ that describes the admitable mapping coordinates, for example objects and their properties. 
3- A Boolean matrix $V_i$ that describes all the relationships that map the facts in relation to their truth . 
4- A Boolean matrix $S_i$ that describes all the relationships that map the facts in relation to their meaning; that is, their applicability within the logical space.

Then, we can define $W_i$ as the boolean AND operation between $V_i$ and $S_i$. Let's consider this small example;

|         | vegetable | food | leaf | root | stem |
| ------- | --------- | ---: | ---: | ---: | ---: |
| lettuce | 1         |    1 |    1 |    0 |    0 |
| spinach | 1         |    1 |    1 |    0 |    0 |
| carrot  | 1         |    1 |    0 |    1 |    0 |
| celery  | 1         |    1 |    0 |    0 |    1 |

Something we can notice is that within this logical space, the properties "plant" and "food" are true for all objects containing our $W_i$, that is, they are tautological under the Tractatus definition and are therefore meaningless (sinnlos). Let's begin by removing them to facilitate further analysis. $W^{T}_i$, the transversal of this would essentially be an index of properties and which object they belong to.

|      | lettuce | spinach | carrot | celery |
| ---- | ------: | ------: | -----: | -----: |
| leaf |       1 |       1 |      0 |      0 |
| root |       0 |       0 |      1 |      0 |
| stem |       0 |       0 |      0 |      1 |

If we designate a Boolean semiring AND/OR over this space, we can verify some useful results.

* If we perform matrix multiplication of $W^T_i \otimes W_i$, we can obtain a map of property co-occurrences.
* The matrix $W$ serves as an indexing space, meaning we can take a one-hot-encoded vector of properties $v_q$ that denotes the objects we want to find. And by vector multiplication $W_i \otimes v_q$ find those vegetables that meet these properties.

Let's return to Wittgenstein for a moment; what are the logical restrictions of this system? They are those given by the categories of sense defined in the Tractatus. *Sinnlos* in this case denotes an operation that even though can be performed, does not returns any usefull result. Acording to the Tractatus, they are divided into two categories:

* Tautology, when the property returns a positive result in all cases, like the properties food and vegetable in the original example.

* Contradiction, when the search vector yields a combination of properties that returns no object, as would be the case when searching for leaf and stem in our example.

On the other hand, the category of *unsinnig*, in our case, would be corresponded to operations that are not possible to be done, because of finding no coordinate in the $L_i$, the logical, such as asking for the "vegetable's" doors, or in our small universe, some property that we haven't mapped, such as sweet taste.

Until here, the proposed system does not offer anything new to the fundamental works of logic and computation. The system becomes useful once we set restrictions that allow us to turn it into a kind of logical truth machine. Indexability and unambiguity.

Let's start addressing it's extensibility. Extending $W_i$ to contain new facts is a pretty straightforward operation. If the fact has a defined coordinate (object, property) that is mappable to the current $L_i$, the mechanism is to find the coordinate and set its value to true.  Otherwise, the operation will require to add a new object or property to the coordinates and check its truthfulness $W_i$ and sense $S_i$ for every other proposition that the dimensional addition generates.

Regarding ambiguity in $W_i$, the proposed system allows us to detect it through an elementary algebraic operation.

* $W^i \otimes W^T_i$ gives us a matrix of objects against objects, containing all the facts that denote some truthfulness in the map. Note that, in this case, since we are interested only in the objects that share some property in reality, the sense layer $S_i$ can simply be ignored. Furthermore, the intersection of anything with ∅ can simply be considered 0.

* $(W^i \otimes W^T_i) - \mathbb{I}$ allows us to clear the diagonal, which contains only tautological results.

* Finally, by dimensionally collapsing this matrix by multiplying it by a vector of ones, we can detect those objects that are colliding in some property in $W_i$.

Then, to disambiguate these objects, we introduce a new property to $L_i$ that will allow us to differentiate them. In our case, since the collapse dimension is a leaf, the easiest thing to do is to add inherited properties to it, for example, rough and smooth.

|         | leaf | root | stem | (leaf) rough | (leaf) smooth |
| ------- | ---: | ---: | ---: | -----------: | ------------: |
| lettuce |    1 |    0 |    0 |            0 |             1 |
| spinach |    1 |    0 |    0 |            1 |             0 |
| carrot  |    0 |    1 |    0 |            Ø |             Ø |
| celery  |    0 |    0 |    1 |            Ø |             Ø |

Now we can notice another detail: if we make a partition defined by the objects lettuce and spinach, and the properties related to leaf, then leaf itself becomes a tautological property. However, for the carrot and celery objects, the newly introduced properties make no sense, meaning that even though the search operation can be performed on these properties, the resultant value will not reflect the degree of truth that this coordinate has for these objects. The solution can be approached in two ways:

* Use a sense mask $S_i$ around the facts that are indexable by these objects and properties, setting the result to 0 for every possible case if querying about truth.

* Create another matrix $W_i'$ that indexes only leafy vegetables. Then, the operation to search for plants by the property about their leaves becomes $W_i \otimes p'_i \otimes W'_i \otimes \otimes p'_i$. 

This gives us some interesting results:

- $W_i$ is basically a hierarchical routing space that partitions $W_i \cup W'_i$, in other words, a context index over a tensorial logic space where we can identify objects by their properties without the need to use $S_i$.
- The tensorial expansion and collapsing are reversible: $S( W_i \cup W'_i)$ can be recovered by filling the facts that the properties of $L'_i$ do not map in $L_i$ with Ø.
- The process is infinitely recursive, allowing us to perform unions, intersections and every other set operations that a boolean ring allows. And as long as we correctly permute the dimensions, we can reorganize the tensorial routes as we want.
- The introduction of hierarchies of contexts allows us to use this logic as a tree search index. Or, to treat a tree search index as a combination of truth and sense matrices.
- In the proposed example, we worked with an $L_i$ that mapped objects to properties, but $L$ contains every relation expressible with boolean logic. State machines, s-expressions, if-else flows, among others, are just instances of what can be computed with the combination of sense and logic.
- Finally, by the usage of these results, we can obtain partitions of $S$, as a set of algebraically computable logical grammar rules for determined contexts.

In Wittgenstein's words, we have defined a direct projection between thought and its logical image, represented in bits and operable through Boolean logic.