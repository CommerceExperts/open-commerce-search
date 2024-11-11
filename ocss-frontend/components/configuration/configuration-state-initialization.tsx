"use client"

import { useEffect, useMemo, useState } from "react"
import { usePathname, useSearchParams } from "next/navigation"
import { useSession } from "next-auth/react"
import { useRecoilState, useRecoilValue } from "recoil"
import * as YAML from "yaml"

import { SearchParamsMap } from "@/types/searchParams"
import {
  fetchIndexerConfiguration,
  fetchSearchConfiguration,
} from "@/lib/github"
import {
  indexerConfigurationState,
  indexerConfigurationUpdatesState,
  isConfigurationLoadedState,
  isIndexerConfigurationDirtyState,
  isSearchConfigurationDirtyState,
  searchConfigurationState,
  searchConfigurationUpdatesState,
} from "@/lib/global-state"
import useCommitHistory from "@/hooks/use-commit-history"

type ConfigurationStateInitializationProps = {
  repo: string
  owner: string
  indexerConfigFilePath: string
  searchConfigFilePath: string
}

export default function ConfigurationStateInitialization({
  repo,
  owner,
  indexerConfigFilePath,
  searchConfigFilePath,
}: ConfigurationStateInitializationProps) {
  const session = useSession()
  const searchParams = useSearchParams()
  const commitRef = useMemo(
    () => searchParams.get(SearchParamsMap.commit) ?? undefined,
    [searchParams]
  )
  const [previousRepo, setPreviousRepo] = useState<string | undefined>()
  const [previousCommitRef, setPreviousCommitRef] = useState<
    string | undefined
  >()

  const [isConfigurationLoaded, setIsConfigurationLoadedState] = useRecoilState(
    isConfigurationLoadedState
  )
  const [indexerConfiguration, setIndexerConfiguration] = useRecoilState(
    indexerConfigurationState
  )
  const [searchConfiguration, setSearchConfiguration] = useRecoilState(
    searchConfigurationState
  )
  const [searchConfigurationUpdates, setSearchConfigurationUpdates] =
    useRecoilState(searchConfigurationUpdatesState)
  const [indexerConfigurationUpdates, setIndexerConfigurationUpdates] =
    useRecoilState(indexerConfigurationUpdatesState)
  const isSearchConfigurationDirty = useRecoilValue(
    isSearchConfigurationDirtyState
  )
  const isIndexerConfigurationDirty = useRecoilValue(
    isIndexerConfigurationDirtyState
  )

  const { resetCommits } = useCommitHistory({
    indexerConfigFilePath,
    searchConfigFilePath,
  })
  const pathname = usePathname()

  const [selectedService, setSelectedService] = useState<
    "indexer" | "search" | undefined
  >(
    pathname.includes("/indexer")
      ? "indexer"
      : pathname.includes("/search")
      ? "search"
      : undefined
  )

  useEffect(() => {
    const previousSelectedService = selectedService
    const newSelectedService: "indexer" | "search" | undefined =
      pathname.includes("/indexer")
        ? "indexer"
        : pathname.includes("/search")
        ? "search"
        : undefined
    setSelectedService(newSelectedService)

    // Check if selected service was changed
    if (
      session.data !== null &&
      (session.data?.user as any)?.login !== undefined &&
      newSelectedService &&
      newSelectedService !== previousSelectedService
    ) {
      resetCommits(newSelectedService)
    }
  }, [pathname])

  useEffect(() => {
    if (
      session.data !== null &&
      (session.data?.user as any)?.login !== undefined &&
      (previousRepo !== repo || previousCommitRef !== commitRef)
    ) {
      setIndexerConfigurationUpdates(0)
      setSearchConfigurationUpdates(0)

      setIsConfigurationLoadedState(false)
      const initialize = async () => {
        // Load commits
        resetCommits(selectedService)

        let accessToken = (session as any).data?.accessToken

        // Load indexer configuration
        const indexerConfigurationYAML = await fetchIndexerConfiguration(
          owner,
          repo,
          indexerConfigFilePath,
          accessToken,
          commitRef
        )
        const indexerConfigurationJSON = YAML.parse(indexerConfigurationYAML)
        setIndexerConfiguration(indexerConfigurationJSON)

        // Load search configuration
        const searchConfigurationYAML = await fetchSearchConfiguration(
          owner,
          repo,
          searchConfigFilePath,
          accessToken,
          commitRef
        )
        const searchConfigurationJSON = YAML.parse(searchConfigurationYAML)
        setSearchConfiguration(searchConfigurationJSON)

        // Finish up
        setIsConfigurationLoadedState(true)
        setPreviousRepo(repo)
        setPreviousCommitRef(commitRef)
        setIndexerConfigurationUpdates(1)
        setSearchConfigurationUpdates(1)
      }
      initialize()
    }
  }, [session, commitRef, repo])

  useEffect(() => {
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = true
    }
    if (isSearchConfigurationDirty || isIndexerConfigurationDirty) {
      window.addEventListener("beforeunload", handler)
      return () => {
        window.removeEventListener("beforeunload", handler)
      }
    }
    return () => {}
  }, [isSearchConfigurationDirty, isIndexerConfigurationDirty])

  useEffect(() => {
    if (Object.keys(searchConfiguration).length > 0) {
      setSearchConfigurationUpdates((oldValue) => oldValue + 1)
    }
  }, [searchConfiguration])

  useEffect(() => {
    if (Object.keys(indexerConfiguration).length > 0) {
      setIndexerConfigurationUpdates((oldValue) => oldValue + 1)
    }
  }, [indexerConfiguration])

  return <></>
}
