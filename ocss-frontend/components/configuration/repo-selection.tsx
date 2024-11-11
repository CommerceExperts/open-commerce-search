import Link from "next/link"
import { getServerSession } from "next-auth"

import { Repository } from "@/types/github"
import { authOptions } from "@/lib/auth"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"

import SignOutButton from "./sign-out-button"

export const OCSS_CONFIG_REPO_PREFIX = "ocss-config"

export default async function RepoSelection() {
  const session = await getServerSession(authOptions)
  const accessToken = (session as any)?.accessToken

  if (!accessToken) {
    return <p>No access token found.</p>
  }

  const res = await fetch(`https://api.github.com/user/repos?per_page=100`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    cache: "no-store",
  })
  const repositories = (await res.json()) as Repository[]

  return (
    <div className="flex w-full flex-col items-center gap-2">
      <h1 className="text-2xl font-bold">Configuration</h1>
      <p>Please select your OCSS Github repository.</p>
      <div className="mx-auto">
        <div className="my-4 space-y-4">
          <ul className="grid w-full grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {repositories
              .filter((repository) =>
                repository.name.startsWith(OCSS_CONFIG_REPO_PREFIX)
              )
              .map((repository) => (
                <li key={repository.id}>
                  <Link
                    href={`/config/${repository.owner.login}/${repository.name}`}
                  >
                    <Card>
                      <CardHeader className="flex flex-row items-center gap-4">
                        <Avatar>
                          <AvatarImage src={repository.owner.avatar_url} />
                          <AvatarFallback>CN</AvatarFallback>
                        </Avatar>

                        <div>
                          <CardTitle className="max-w-[200px] truncate text-lg">
                            {repository.name}
                          </CardTitle>
                          <CardDescription className="max-w-[200px] truncate">
                            {repository.owner.login}
                          </CardDescription>
                        </div>
                      </CardHeader>
                    </Card>
                  </Link>
                </li>
              ))}
          </ul>
          <Separator />
          <SignOutButton className="mx-auto max-w-sm" />
        </div>
      </div>
    </div>
  )
}
