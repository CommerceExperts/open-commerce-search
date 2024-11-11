import Link from "next/link"
import { env } from "@/env.mjs"
import { Settings } from "lucide-react"

export default function ConfigButton() {
  let href = "/config"
  if (env.DEFAULT_CONFIG_REPO_OWNER && env.DEFAULT_CONFIG_REPO_NAME) {
    href = `/config/${env.DEFAULT_CONFIG_REPO_OWNER}/${env.DEFAULT_CONFIG_REPO_NAME}`
  }

  return (
    <>
      {env.GITHUB_OAUTH_ID &&
        env.GITHUB_OAUTH_SECRET &&
        env.NEXTAUTH_URL &&
        env.NEXTAUTH_SECRET && (
          <Link href={href}>
            <p className="text-md flex items-center gap-2 font-semibold text-muted-foreground sm:text-sm">
              <Settings className="h-5 w-5 sm:h-4 sm:w-4" />
              <span className="hidden sm:block">Config</span>
            </p>
          </Link>
        )}
    </>
  )
}
