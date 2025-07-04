import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { IndexConfiguration } from "./ocs-types";

interface IndexStore {
  defaultIndexConfig: IndexConfiguration;
  indexConfig: Map<string, IndexConfiguration>;

  setDefaultIndexConfig: (config: IndexConfiguration) => void;
  setIndexConfig: (key: string, config: IndexConfiguration) => void;
  removeIndexConfig: (key: string) => void;
  clearIndexConfigs: () => void;
}

export const useIndexStore = create<IndexStore>()(
  persist(
    (set, get) => ({
      defaultIndexConfig: {
        indexSettings: {
          replicaCount: 1,
          refreshInterval: "5s",
          minimumDocumentCount: 1,
          waitTimeMsForHealthyIndex: 3000,
          useDefaultConfig: true,
        },
        dataProcessorConfiguration: {
          processors: [],
          configuration: {},
          useDefaultConfig: true,
        },
        fieldConfiguration: {
          fields: {},
          dynamicFields: [],
          useDefaultConfig: true,
        },
      },

      indexConfig: new Map(),

      setDefaultIndexConfig: (config) => set({ defaultIndexConfig: config }),

      setIndexConfig: (key, config) => {
        const updated = new Map(get().indexConfig);
        updated.set(key, config);
        set({ indexConfig: updated });
      },

      removeIndexConfig: (key) => {
        const updated = new Map(get().indexConfig);
        updated.delete(key);
        set({ indexConfig: updated });
      },

      clearIndexConfigs: () => set({ indexConfig: new Map() }),
    }),
    {
      name: "index-store",
      partialize: (state) => ({
        defaultIndexConfig: state.defaultIndexConfig,
        indexConfig: Array.from(state.indexConfig.entries()),
      }),
      merge: (persisted, current) => {
        const persistedTyped = persisted as any;
        return {
          ...current,
          ...persistedTyped,
          indexConfig: new Map(persistedTyped.indexConfig || []),
        };
      },
    }
  )
);
