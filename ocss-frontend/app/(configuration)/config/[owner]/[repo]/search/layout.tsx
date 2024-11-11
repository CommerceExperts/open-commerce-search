import { env } from "@/env.mjs"

import CommitHistory from "@/components/configuration/commit-history"
import SaveConfigurationButton from "@/components/configuration/save-configuration-button"
import { SearchTenantSelect } from "@/components/configuration/search-tenant-select"
import { SidebarNav } from "@/components/configuration/sidebar-nav"

type IndexerConfigurationLayoutProps = {
  children: React.ReactNode
  params: { repo: string; owner: string }
}

export default function IndexerConfigurationLayout({
  params: { repo, owner },
  children,
}: IndexerConfigurationLayoutProps) {
  const sidebarNavItems = [
    {
      title: "Query Processing",
      href: `/config/${owner}/${repo}/search/query-processing-configuration`,
    },
    {
      title: "Scoring",
      href: `/config/${owner}/${repo}/search/scoring-configuration`,
    },
    {
      title: "Facets",
      href: `/config/${owner}/${repo}/search/facet-configuration`,
    },
    {
      title: "Sorting",
      href: `/config/${owner}/${repo}/search/sort-configuration`,
    },
  ]

  return (
    <div className="flex flex-col space-y-8 lg:flex-row lg:space-x-12 lg:space-y-0">
      <aside className="-mx-4 flex flex-col gap-4 lg:w-1/5">
        <SearchTenantSelect />
        <SidebarNav items={sidebarNavItems} />
        <SaveConfigurationButton
          owner={owner}
          repo={repo}
          indexerConfigFilePath={env.INDEXER_CONFIG_FILE_PATH}
          searchConfigFilePath={env.SEARCH_CONFIG_FILE_PATH}
        />
      </aside>
      <div className="w-full space-y-8 md:grid md:grid-cols-3 md:gap-4">
        <div className="col-span-2 flex-1 lg:max-w-2xl">{children}</div>
        <CommitHistory
          indexerConfigFilePath={env.INDEXER_CONFIG_FILE_PATH}
          searchConfigFilePath={env.SEARCH_CONFIG_FILE_PATH}
        />
      </div>
    </div>
  )
}
