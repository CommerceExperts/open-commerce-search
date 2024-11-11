"use client"

import Link from "next/link"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { ExternalLink, Plus, RefreshCw } from "lucide-react"
import { useRecoilState, useRecoilValue } from "recoil"

import { SearchParamsMap } from "@/types/searchParams"
import {
  commitsState,
  isIndexerConfigurationDirtyState,
  isSearchConfigurationDirtyState,
} from "@/lib/global-state"
import { cn } from "@/lib/utils"
import useCommitHistory from "@/hooks/use-commit-history"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"

import { Button, buttonVariants } from "../ui/button"

type CommitHistoryProps = {
  indexerConfigFilePath: string
  searchConfigFilePath: string
}

export default function CommitHistory({
  indexerConfigFilePath,
  searchConfigFilePath,
}: CommitHistoryProps) {
  const [commits, setCommits] = useRecoilState(commitsState)
  const searchParams = useSearchParams()
  const commitParam = searchParams.get(SearchParamsMap.commit)
  const configParam = searchParams.get(SearchParamsMap.config)
  const pathname = usePathname()
  const { loadNextCommits, resetCommits } = useCommitHistory({
    indexerConfigFilePath,
    searchConfigFilePath,
  })
  const router = useRouter()
  const isSearchConfigurationDirty = useRecoilValue(
    isSearchConfigurationDirtyState
  )
  const isIndexerConfigurationDirty = useRecoilValue(
    isIndexerConfigurationDirtyState
  )

  let selectedService: "indexer" | "search" | undefined = pathname.includes(
    "/indexer"
  )
    ? "indexer"
    : pathname.includes("/search")
    ? "search"
    : undefined

  return (
    <div className="w-full space-y-2">
      {commits.length == 0 ? (
        <></>
      ) : (
        <>
          <h4 className="text-md flex items-center gap-2 font-bold">
            Commit history{" "}
            <RefreshCw
              onClick={() => resetCommits(selectedService)}
              className="h-4 w-4 cursor-pointer transition-transform hover:rotate-90"
            />
          </h4>
          <ul className="flex flex-col gap-2">
            {commits.map((commit) => (
              <li
                key={commit.sha}
                className={cn(
                  buttonVariants({ variant: "outline" }),
                  "h-9 w-full justify-between",
                  commit.sha === commitParam ? "bg-secondary" : ""
                )}
              >
                <div className="flex grow gap-2">
                  <Link
                    href={commit?.committer?.html_url ?? ""}
                    target="_blank"
                  >
                    <Avatar className="h-6 w-6">
                      <AvatarImage src={commit?.committer?.avatar_url} />
                      <AvatarFallback>?</AvatarFallback>
                    </Avatar>
                  </Link>
                  <span
                    className="w-full cursor-pointer"
                    onClick={() => {
                      if (
                        isIndexerConfigurationDirty ||
                        isSearchConfigurationDirty
                      ) {
                        if (
                          confirm(
                            "Changes you have not saved will be lost. Are you sure you want to proceed?"
                          )
                        ) {
                          router.push(
                            pathname +
                              (commit.sha === commitParam
                                ? ""
                                : "/?" +
                                  new URLSearchParams(
                                    configParam
                                      ? [
                                          [SearchParamsMap.commit, commit.sha],
                                          [SearchParamsMap.config, configParam],
                                        ]
                                      : [[SearchParamsMap.commit, commit.sha]]
                                  ))
                          )
                        }
                      } else {
                        resetCommits()
                        router.push(
                          pathname +
                            (commit.sha === commitParam
                              ? ""
                              : "/?" +
                                new URLSearchParams(
                                  configParam
                                    ? [
                                        [SearchParamsMap.commit, commit.sha],
                                        [SearchParamsMap.config, configParam],
                                      ]
                                    : [[SearchParamsMap.commit, commit.sha]]
                                ))
                        )
                      }
                    }}
                  >
                    {commit.commit.message === "" ? (
                      <p className="max-w-[150px] truncate font-normal xl:max-w-[200px]">
                        No commit message
                      </p>
                    ) : (
                      <p className="max-w-[150px] truncate xl:max-w-[200px]">
                        {commit.commit.message}
                      </p>
                    )}
                  </span>
                </div>
                <Link
                  href={commit.html_url + "?w=1" /* w=1 to ignore whitespace */}
                  target="_blank"
                >
                  <ExternalLink className="h-4 w-4" />
                </Link>
              </li>
            ))}
            {commits.length > 0 && (
              <li>
                <Button
                  onClick={async () => {
                    await loadNextCommits(selectedService)
                  }}
                  variant="outline"
                  className="h-9 w-full justify-center gap-2"
                >
                  <Plus className="h-4 w-4" />
                  <p>Load more</p>
                </Button>
              </li>
            )}
          </ul>
        </>
      )}
    </div>
  )
}
