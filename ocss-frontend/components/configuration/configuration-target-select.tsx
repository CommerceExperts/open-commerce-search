"use client"

import { ComponentProps, useMemo } from "react"
import { usePathname, useRouter } from "next/navigation"
import { useRecoilValue } from "recoil"

import {
  isIndexerConfigurationDirtyState,
  isSearchConfigurationDirtyState,
} from "@/lib/global-state"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"

type ConfigurationTargetSelectProps = {
  repo: string
  owner: string
  defaultValue?: string
} & ComponentProps<"div">

export default function ConfigurationTargetSelect({
  repo,
  owner,
  className,
}: ConfigurationTargetSelectProps) {
  const router = useRouter()
  const pathname = usePathname()
  const selectedConfiguration = useMemo(
    () =>
      pathname.includes("/indexer")
        ? "indexer"
        : pathname.includes("/search")
        ? "search"
        : undefined,
    [pathname]
  )
  const isSearchConfigurationDirty = useRecoilValue(
    isSearchConfigurationDirtyState
  )
  const isIndexerConfigurationDirty = useRecoilValue(
    isIndexerConfigurationDirtyState
  )

  return (
    <Tabs
      onValueChange={(value) =>
        router.push(`/config/${owner}/${repo}/${value}`)
      }
      defaultValue={selectedConfiguration ?? "search"}
    >
      <TabsList className={className}>
        <TabsTrigger className="w-full gap-1" value="search">
          Search
          {isSearchConfigurationDirty && (
            <span className="font-bold text-red-500">*</span>
          )}
        </TabsTrigger>
        <TabsTrigger className="w-full gap-1" value="indexer">
          Indexer
          {isIndexerConfigurationDirty && (
            <span className="font-bold text-red-500">*</span>
          )}
        </TabsTrigger>
      </TabsList>
    </Tabs>
  )
}
