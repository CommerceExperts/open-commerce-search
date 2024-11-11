import { notFound } from "next/navigation"
import { env } from "@/env.mjs"

import RepoSelection from "@/components/configuration/repo-selection"

export default async function RepoSelectionPage() {
  if (
    !(
      env.GITHUB_OAUTH_ID &&
      env.GITHUB_OAUTH_SECRET &&
      env.NEXTAUTH_URL &&
      env.NEXTAUTH_SECRET
    )
  ) {
    notFound()
  }

  return (
    <section className="container grid items-center gap-6 pb-8 pt-6 md:py-10">
      {/* @ts-ignore Async server component */}
      <RepoSelection />
    </section>
  )
}
