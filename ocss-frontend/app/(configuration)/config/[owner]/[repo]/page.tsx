import { redirect } from "next/navigation"

type ConfigurationPageProps = {
  params: { repo: string; owner: string }
}

export default async function ConfigurationPage({
  params: { repo, owner },
}: ConfigurationPageProps) {
  return redirect(
    `/config/${owner}/${repo}/search/query-processing-configuration`
  )
}
