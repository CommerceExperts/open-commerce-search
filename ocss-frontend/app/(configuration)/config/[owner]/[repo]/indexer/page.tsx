import { redirect } from "next/navigation"

type IndexerConfigurationPageProps = {
  params: { repo: string; owner: string }
}

export default async function IndexerConfigurationPage({
  params: { repo, owner },
}: IndexerConfigurationPageProps) {
  return redirect(`/config/${owner}/${repo}/indexer/general`)
}
