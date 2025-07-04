import { useEffect } from "react";
import { useSearchStore } from "./search-store";
import { useIndexStore } from "./index-store";
import type {
  ApplicationSearchProperties,
  ConfigResponse,
  IndexConfiguration,
} from "./ocs-types";

async function fetchServiceConfig(
  service: "index" | "search",
  id?: string
): Promise<ConfigResponse | null> {
  const path = id
    ? `/config-api/v1/${service}/${encodeURIComponent(id)}`
    : `/config-api/v1/${service}`;
  const url = `http://localhost:8538${path}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(
        `Failed to fetch ${service} config: ${response.statusText}`
      );
    }
    return await response.json();
  } catch (error) {
    console.error(`Error fetching ${service} config:`, error);
    return null;
  }
}

export default function ConfigInitializer({ id }: { id?: string }) {
  const setTenantConfig = useSearchStore((state) => state.setTenantConfig);
  const setDefaultTenantConfig = useSearchStore(
    (state) => state.setDefaultTenantConfig
  );
  const setIndexConfig = useIndexStore((state) => state.setIndexConfig);
  const setDefaultIndexConfig = useIndexStore(
    (state) => state.setDefaultIndexConfig
  );

  useEffect(() => {
    async function init() {
      const [searchConfig, indexConfig] = await Promise.all([
        fetchServiceConfig("search", id),
        fetchServiceConfig("index", id),
      ]);

      if (!searchConfig || !indexConfig) {
        // TODO: Error handling
        return;
      }

      setDefaultIndexConfig(indexConfig.defaultConfig as IndexConfiguration);
      Object.keys(indexConfig.scopedConfig).forEach((key) => {
        setIndexConfig(
          key,
          indexConfig.scopedConfig[key] as IndexConfiguration
        );
      });

      setDefaultTenantConfig(
        searchConfig.defaultConfig as ApplicationSearchProperties
      );
      Object.keys(searchConfig.scopedConfig).forEach((key) => {
        setTenantConfig(
          key,
          searchConfig.scopedConfig[key] as ApplicationSearchProperties
        );
      });
    }

    init();
  }, [id]);

  return null;
}
