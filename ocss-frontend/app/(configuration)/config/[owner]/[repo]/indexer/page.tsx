import { redirect } from "next/navigation"

type IndexerConfigurationPageProps = {
  params: Promise<{ repo: string; owner: string }>
}

export default async function IndexerConfigurationPage(
  props: IndexerConfigurationPageProps
) {
  const params = await props.params

  const { repo, owner } = params

  return redirect(`/config/${owner}/${repo}/indexer/general`)
}
