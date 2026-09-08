import { redirect } from "next/navigation"

type SearchConfigurationPageProps = {
  params: Promise<{ repo: string; owner: string }>
}

export default async function SearchConfigurationPage(
  props: SearchConfigurationPageProps
) {
  const params = await props.params

  const { repo, owner } = params

  return redirect(
    `/config/${owner}/${repo}/search/query-processing-configuration`
  )
}
