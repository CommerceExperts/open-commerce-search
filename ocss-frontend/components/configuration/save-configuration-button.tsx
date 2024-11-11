"use client"

import { useState } from "react"
import { usePathname } from "next/navigation"
import { UploadCloud } from "lucide-react"
import { SessionContextValue, useSession } from "next-auth/react"
import { useRecoilState, useRecoilValue } from "recoil"

import { saveIndexerConfiguration, saveSearchConfiguration } from "@/lib/github"
import {
  indexerConfigurationState,
  indexerConfigurationUpdatesState,
  isIndexerConfigurationDirtyState,
  isSearchConfigurationDirtyState,
  searchConfigurationState,
  searchConfigurationUpdatesState,
} from "@/lib/global-state"
import { cn } from "@/lib/utils"
import useCommitHistory from "@/hooks/use-commit-history"
import { buttonVariants } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"

import { SaveConfigurationForm } from "./save-configuration-form"

type SaveConfigurationButtonProps = {
  repo: string
  owner: string
  indexerConfigFilePath: string
  searchConfigFilePath: string
}

export default function SaveConfigurationButton({
  repo,
  owner,
  indexerConfigFilePath,
  searchConfigFilePath,
}: SaveConfigurationButtonProps) {
  const session = useSession()
  const [indexerConfiguration, setIndexerConfiguration] = useRecoilState(
    indexerConfigurationState
  )
  const isSearchConfigurationDirty = useRecoilValue(
    isSearchConfigurationDirtyState
  )
  const isIndexerConfigurationDirty = useRecoilValue(
    isIndexerConfigurationDirtyState
  )
  const [searchConfiguration, setSearchConfiguration] = useRecoilState(
    searchConfigurationState
  )
  const [dialogOpen, setDialogOpen] = useState(false)
  const pathname = usePathname()
  const { resetCommits } = useCommitHistory({
    indexerConfigFilePath,
    searchConfigFilePath,
  })
  const [searchConfigurationUpdates, setSearchConfigurationUpdates] =
    useRecoilState(searchConfigurationUpdatesState)
  const [indexerConfigurationUpdates, setIndexerConfigurationUpdates] =
    useRecoilState(indexerConfigurationUpdatesState)

  let selectedService: "indexer" | "search" | undefined = pathname.includes(
    "/indexer"
  )
    ? "indexer"
    : pathname.includes("/search")
    ? "search"
    : undefined

  if (!session) {
    return <></>
  }

  const accessToken = (session as any).data?.accessToken
  const login = (session.data?.user as SessionContextValue & { login?: string })
    ?.login

  return (
    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
      <DialogTrigger
        className={cn(
          buttonVariants({
            variant:
              (isSearchConfigurationDirty && selectedService === "search") ||
              (isIndexerConfigurationDirty && selectedService === "indexer")
                ? "default"
                : "secondary",
          }),
          "flex gap-2 font-bold"
        )}
      >
        <UploadCloud className="h-4 w-4" />
        Save & commit
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogTitle>
            Save changes for{" "}
            <span className="font-extrabold">{selectedService} service</span>
          </DialogTitle>
          <DialogDescription className="pb-3">
            This action will commit and push your changes to the {repo}{" "}
            repository.
          </DialogDescription>
          <SaveConfigurationForm
            configuration={
              selectedService === "indexer"
                ? indexerConfiguration
                : searchConfiguration
            }
            onSubmit={async (values) => {
              setDialogOpen(false)

              if (selectedService == "indexer") {
                await saveIndexerConfiguration(
                  repo,
                  indexerConfigFilePath,
                  indexerConfiguration,
                  owner,
                  accessToken,
                  {
                    name: login ?? owner,
                    email:
                      session.data?.user?.email ?? "ocss-frontend@example.com",
                    message: values.message,
                  }
                )
                setIndexerConfigurationUpdates(0)
              } else if (selectedService == "search") {
                await saveSearchConfiguration(
                  repo,
                  searchConfigFilePath,
                  searchConfiguration,
                  owner,
                  accessToken,
                  {
                    name: login ?? owner,
                    email:
                      session.data?.user?.email ?? "ocss-frontend@example.com",
                    message: values.message,
                  }
                )
                setSearchConfigurationUpdates(2)
              }
              await resetCommits(selectedService)
            }}
          />
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}
