"use client"

import { useSearchParams } from "next/navigation"
import { useRecoilState } from "recoil"

import { SearchParamsMap } from "@/types/searchParams"
import { isConfigurationLoadedState } from "@/lib/global-state"
import { Separator } from "@/components/ui/separator"
import { GeneralForm } from "@/components/configuration/general-form"
import Loader from "@/components/misc/loader"

export default function GeneralIndexerSettings() {
  const [isConfigurationLoaded, setIsConfigurationLoadedState] = useRecoilState(
    isConfigurationLoadedState
  )
  const searchParams = useSearchParams()

  const configParam = searchParams.get(SearchParamsMap.config)

  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-lg font-medium">General</h3>
        <p className="text-sm text-muted-foreground">
          Manage general settings of your indexer service.
        </p>
      </div>
      <Separator />
      {isConfigurationLoaded ? (
        <GeneralForm indexConfig={configParam || "default-index-config"} />
      ) : (
        <div>
          <Loader />
        </div>
      )}
    </div>
  )
}
