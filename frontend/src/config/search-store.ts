import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { ApplicationSearchProperties, ProductSetType } from "./ocs-types";

interface SearchStore {
  defaultTenantConfig: ApplicationSearchProperties;
  tenantConfig: Map<string, ApplicationSearchProperties>;

  setDefaultTenantConfig: (config: ApplicationSearchProperties) => void;
  setTenantConfig: (
    tenant: string,
    config: ApplicationSearchProperties
  ) => void;
  removeTenantConfig: (tenant: string) => void;
  clearTenantConfigs: () => void;
}

export const useSearchStore = create<SearchStore>()(
  persist(
    (set, get) => ({
      defaultTenantConfig: {
        indexName: "",
        locale: "ROOT",
        useDefaultFacetConfig: false,
        useDefaultScoringConfig: false,
        useDefaultQueryConfig: false,
        useDefaultSortConfig: false,
        variantPickingStrategy: "pickIfBestScored",
        queryProcessing: {
          userQueryPreprocessors: [],
          userQueryAnalyzer: null,
        },
        facetConfiguration: {
          defaultTermFacetConfiguration: undefined,
          defaultNumberFacetConfiguration: undefined,
          facets: [],
          maxFacets: 5,
        },
        scoringConfiguration: {
          scoreMode: "AVG",
          boostMode: "AVG",
          scoreFunctions: [],
        },
        queryConfiguration: {},
        sortConfiguration: [],
        rescorers: [],
        customProductSetResolver: {} as Record<ProductSetType, string>,
        pluginConfiguration: {},
      },

      tenantConfig: new Map(),

      setDefaultTenantConfig: (config) => set({ defaultTenantConfig: config }),

      setTenantConfig: (tenant, config) => {
        const updated = new Map(get().tenantConfig);
        updated.set(tenant, config);
        set({ tenantConfig: updated });
      },

      removeTenantConfig: (tenant) => {
        const updated = new Map(get().tenantConfig);
        updated.delete(tenant);
        set({ tenantConfig: updated });
      },

      clearTenantConfigs: () => set({ tenantConfig: new Map() }),
    }),
    {
      name: "search-store",
      partialize: (state) => ({
        defaultTenantConfig: state.defaultTenantConfig,
        tenantConfig: Array.from(state.tenantConfig.entries()),
      }),
      merge: (persisted, current) => {
        const persistedTyped = persisted as any;
        return {
          ...current,
          ...persistedTyped,
          tenantConfig: new Map(persistedTyped.tenantConfig || []),
        };
      },
    }
  )
);
