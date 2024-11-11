// Default configuration for indexer and search service in YAML

export const defaultIndexerServiceConfig = `
spring:
  logging:
    level:
      de.cxp.ocs: DEBUG
ocs:
  index-config: {}
  default-index-config:
    field-configuration:
      dynamic-fields: []
      fields: {}

`
export const defaultSearchServiceConfig = `
ocs:
  tenant-config: {}
  default-tenant-config:
    facet-configuration:
      maxFacets: 5
      facets: []
`
