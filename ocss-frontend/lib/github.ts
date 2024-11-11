"use client"

import { toast } from "sonner"
import YAML from "yaml"

import { IndexerConfiguration, SearchConfiguration } from "@/types/config"
import { Commit, ContentFile } from "@/types/github"

import {
  defaultIndexerServiceConfig,
  defaultSearchServiceConfig,
} from "./default-configs"
import { decodeBase64, encodeBase64 } from "./utils"

export async function saveIndexerConfiguration(
  repo: string,
  indexerConfigFilePath: string,
  indexerConfiguration: IndexerConfiguration,
  login: string,
  accessToken: string,
  commitOptions: {
    message: string
    name: string
    email: string
  }
) {
  // Stringify configuration to yaml from json object
  const stringifiedConfiguration = YAML.stringify(indexerConfiguration, {
    lineWidth: 0,
  })

  // Encode yaml string to base64
  const content = encodeBase64(stringifiedConfiguration)

  // Get sha to update file if file already exists
  let sha: string | undefined = undefined

  const res1 = await fetch(
    `https://api.github.com/repos/${login}/${repo}/contents/${indexerConfigFilePath}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  )
  if (res1.ok) {
    const data1 = (await res1.json()) as ContentFile

    sha = data1.sha
  }

  // Update file
  const res2 = await fetch(
    `https://api.github.com/repos/${login}/${repo}/contents/${indexerConfigFilePath}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        message: commitOptions.message,
        committer: {
          name: commitOptions.name,
          email: commitOptions.email,
        },
        content,
        sha,
      }),
      method: "PUT",
    }
  )

  if (!res2.ok) {
    if (res2.status == 409) {
      return toast.error("Failure", {
        description: `File content of "${indexerConfigFilePath}" in "${repo}" was changed. Please reload the configurator.`,
      })
    } else {
      return toast.error("Failure", {
        description: `Something went wrong while updating file content of "${indexerConfigFilePath}" in "${repo}".`,
      })
    }
  }

  // Success
  return toast.success("Success", {
    description: `You have successfully saved your changes to the indexer configuration.`,
  })
}

export async function saveSearchConfiguration(
  repo: string,
  searchConfigFilePath: string,
  searchConfiguration: SearchConfiguration,
  login: string,
  accessToken: string,
  commitOptions: {
    message: string
    name: string
    email: string
  }
) {
  // Stringify configuration to yaml from json object
  const stringifiedConfiguration = YAML.stringify(searchConfiguration, {
    lineWidth: 0,
  })

  // Encode yaml string to base64
  const content = encodeBase64(stringifiedConfiguration)

  // Get sha to update file if file already exists
  let sha: string | undefined = undefined

  const res1 = await fetch(
    `https://api.github.com/repos/${login}/${repo}/contents/${searchConfigFilePath}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  )
  if (res1.ok) {
    const data1 = (await res1.json()) as ContentFile

    sha = data1.sha
  }

  // Update file
  const res2 = await fetch(
    `https://api.github.com/repos/${login}/${repo}/contents/${searchConfigFilePath}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        message: commitOptions.message,
        committer: {
          name: commitOptions.name,
          email: commitOptions.email,
        },
        content,
        sha,
      }),
      method: "PUT",
    }
  )

  if (!res2.ok) {
    if (res2.status == 409) {
      return toast.error("Failure", {
        description: `File content of "${searchConfigFilePath}" in "${repo}" was changed. Please reload the configurator.`,
      })
    } else {
      return toast.error("Failure", {
        description: `Something went wrong while updating file content of "${searchConfigFilePath}" in "${repo}".`,
      })
    }
  }

  // Success
  return toast.success("Success", {
    description: `You have successfully saved your changes to the search configuration.`,
  })
}

export async function fetchIndexerConfiguration(
  login: string,
  repo: string,
  indexerConfigFilePath: string,
  accessToken: string,
  ref?: string
) {
  const res = await fetch(
    `https://api.github.com/repos/${login}/${repo}/contents/${indexerConfigFilePath}${
      ref ? `?ref=${ref}` : ""
    }`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  )

  let content = ""

  if (!res.ok) {
    // Set default config if file not found
    toast.error("Failure", {
      description: `Could not find file "${indexerConfigFilePath}" in "${repo}". Using default configuration instead.`,
    })
    content = defaultIndexerServiceConfig
  } else {
    const data = (await res.json()) as ContentFile
    content = decodeBase64(data.content)
  }

  return content
}

export async function fetchSearchConfiguration(
  login: string,
  repo: string,
  searchConfigFilePath: string,
  accessToken: string,
  ref?: string
) {
  const res = await fetch(
    `https://api.github.com/repos/${login}/${repo}/contents/${searchConfigFilePath}${
      ref ? `?ref=${ref}` : ""
    }`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  )

  let content = ""

  if (!res.ok) {
    // Set default config if file not found
    toast.error("Failure", {
      description: `Could not find file "${searchConfigFilePath}" in "${repo}". Using default configuration instead.`,
    })
    content = defaultSearchServiceConfig
  } else {
    const data = (await res.json()) as ContentFile
    content = decodeBase64(data.content)
  }

  return content
}

export async function fetchCommits(
  login: string,
  repo: string,
  accessToken: string,
  perPage: number = 10,
  page: number = 1,
  filePath?: string
) {
  const res = await fetch(
    `https://api.github.com/repos/${login}/${repo}/commits?per_page=${perPage}&page=${page}${
      filePath ? `&path=${filePath}` : ""
    }`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      cache: "no-store",
    }
  )
  const data = await res.json()

  if (!res.ok) {
    toast.error("Failure", {
      description: `Something went wrong while fetching the commits of "${repo}".`,
    })
    return null
  }

  return data as Commit[]
}
