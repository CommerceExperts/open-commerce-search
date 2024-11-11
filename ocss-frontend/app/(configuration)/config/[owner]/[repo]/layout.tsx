import { env } from "@/env.mjs"

import { Separator } from "@/components/ui/separator"
import CommitRef from "@/components/configuration/commit-ref"
import ConfigurationStateInitialization from "@/components/configuration/configuration-state-initialization"
import ConfigurationTargetSelect from "@/components/configuration/configuration-target-select"

type ConfiguratorPageProps = {
  children: React.ReactNode
  params: { repo: string; owner: string }
}

export default async function ConfigurationLayout({
  params: { repo, owner },
  children,
}: ConfiguratorPageProps) {
  return (
    <>
      <ConfigurationStateInitialization
        owner={owner}
        repo={repo}
        indexerConfigFilePath={env.INDEXER_CONFIG_FILE_PATH}
        searchConfigFilePath={env.SEARCH_CONFIG_FILE_PATH}
      />
      <section className="container grid items-center gap-6 pb-8 pt-6 md:py-10">
        <div className="flex flex-wrap justify-between">
          <div className="space-y-0.5">
            <h1 className="break-all text-2xl font-bold tracking-tight">
              Configuration of {repo} <CommitRef />
            </h1>
            <p className="text-muted-foreground">
              Manage your search & indexer service settings.
            </p>
            <ConfigurationTargetSelect
              className="mt-4 w-[300px]"
              repo={repo}
              owner={owner}
            />
          </div>
        </div>
        <Separator className="my-6" />
        {children}
      </section>
    </>
  )
}
