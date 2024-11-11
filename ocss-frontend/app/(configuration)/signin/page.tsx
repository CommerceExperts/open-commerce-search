import { notFound } from "next/navigation"
import { env } from "@/env.mjs"

import SignInButton from "@/components/configuration/sign-in-button"

export default function SignIn() {
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
      <div className="flex w-full flex-col items-center gap-2">
        <h1 className="text-2xl font-bold">Configuration</h1>
        <p>Please sign in using your Github account to configure your OCSS.</p>
        <div className="w-60">
          <SignInButton />
        </div>
      </div>
    </section>
  )
}
