import { redirect } from "next/navigation"

type SearchConfigurationPageProps = {
  params: { repo: string; owner: string }
}

export default async function SearchConfigurationPage({
  params: { repo, owner },
}: SearchConfigurationPageProps) {
  return redirect(
    `/config/${owner}/${repo}/search/query-processing-configuration`
  )
}
