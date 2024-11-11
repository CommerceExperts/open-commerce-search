import { useParams } from "next/navigation"
import { useSession } from "next-auth/react"
import { useRecoilState } from "recoil"

import { fetchCommits } from "@/lib/github"
import { commitsPageState, commitsState } from "@/lib/global-state"

type useCommitHistoryProps = {
  indexerConfigFilePath: string
  searchConfigFilePath: string
}

export default function useCommitHistory({
  indexerConfigFilePath,
  searchConfigFilePath,
}: useCommitHistoryProps) {
  const [commits, setCommits] = useRecoilState(commitsState)
  const [commitsPage, setCommitsPage] = useRecoilState(commitsPageState)
  const params = useParams()
  const session = useSession()
  const repo = params.repo
  const owner = params.owner as string
  const commitsPerPage = 10

  async function loadNextCommits(selectedService?: "indexer" | "search") {
    if (typeof repo !== "string") return

    let accessToken = (session as any).data?.accessToken

    const commits = await fetchCommits(
      owner,
      repo,
      accessToken,
      commitsPerPage,
      commitsPage + 1,
      selectedService === "indexer"
        ? indexerConfigFilePath
        : selectedService === "search"
        ? searchConfigFilePath
        : undefined
    )

    setCommitsPage((previousCommitsPage) => previousCommitsPage + 1)

    if (commits) {
      setCommits((previousCommits) => previousCommits.concat(commits))
    }
  }

  async function resetCommits(selectedService?: "indexer" | "search") {
    if (typeof repo !== "string") return
    setCommits([])

    let accessToken = (session as any).data?.accessToken

    setCommitsPage(1)

    const commits = await fetchCommits(
      owner,
      repo,
      accessToken,
      commitsPerPage,
      1,
      selectedService === "indexer"
        ? indexerConfigFilePath
        : selectedService === "search"
        ? searchConfigFilePath
        : undefined
    )

    if (commits) {
      setCommits(commits)
    }
  }

  return { loadNextCommits, resetCommits }
}
