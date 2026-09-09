import { redirect } from "next/navigation"

type ConfigurationPageProps = {
  params: Promise<{ repo: string; owner: string }>
}

export default async function ConfigurationPage(props: ConfigurationPageProps) {
  const params = await props.params

  const { repo, owner } = params

  return redirect(
    `/config/${owner}/${repo}/search/query-processing-configuration`
  )
}
