import { Suggestion } from "@/types/api"

export async function suggest(
  tenant: string,
  query: string,
  limit: number,
  basepath: string
) {
  const res = await fetch(
    `${basepath}/suggest-api/v1/${tenant}/suggest?userQuery=${query}&limit=${limit}`,
    { cache: "no-store" }
  )

  if (!res.ok) {
    return []
  }

  const data = (await res.json()) as Suggestion[]

  return data
}
