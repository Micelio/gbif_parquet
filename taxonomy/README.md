Taxonomy ID mapping provided by [Prof. Hoehndorf](https://github.com/bio-ontology-research-group/taxonomy-alignment)

This was converted from OWL syntax to ttl using the [ROBOT tooling]()

This uses the non official OBO translation of the NCBI taxonomy, these IRIs where also translated to the just as unoffical UniProt copy of the NCBI taxonomy:

```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
CONSTRUCT {
    ?s ?p ?o .
    ?obot owl:equivalentClass ?unit .
    ?s2 ?p2 ?unit .
} WHERE {
    {
        ?s ?p ?o .
    } UNION {
        ?obot a owl:Class .
        BIND(STR(?obot) AS ?obots)
        FILTER(STRSTARTS(?obots, 'http://purl.obolibrary.org/obo/NCBITaxon_'))
        BIND(SUBSTR(?obots, STRLEN('http://purl.obolibrary.org/obo/NCBITaxon_') + 1) AS ?tid)
        BIND(IRI(CONCAT('http://purl.uniprot.org/taxonomy/', ?tid)) AS ?unit)
    } UNION {
        ?s2 ?p2 ?o .
        BIND(STR(?o) AS ?obots)
        FILTER(STRSTARTS(?obots, 'http://purl.obolibrary.org/obo/NCBITaxon_'))
        BIND(SUBSTR(?obots, STRLEN('http://purl.obolibrary.org/obo/NCBITaxon_') + 1) AS ?tid)
        BIND(IRI(CONCAT('http://purl.uniprot.org/taxonomy/', ?tid)) AS ?unit)
    }
}
```
