import { createEnv } from "@t3-oss/env-nextjs"
import { z } from "zod"

export const env = createEnv({
  server: {
    SUGGEST_API_AUTH: z.optional(z.string()),
    SUGGEST_API_URL: z.optional(z.string()),

    SEARCH_API_AUTH: z.optional(z.string()),
    SEARCH_API_URL: z.optional(z.string()),

    ENABLE_SUGGEST_API: z.optional(z.string()).default("false"),

    DEFAULT_TENANT: z.optional(z.string()).default(""),
    BASEPATH: z.optional(z.string()).default(""),

    GITHUB_OAUTH_ID: z.optional(z.string()).default(""),
    GITHUB_OAUTH_SECRET: z.optional(z.string()).default(""),
    DEFAULT_CONFIG_REPO_OWNER: z.optional(z.string()).default(""),
    DEFAULT_CONFIG_REPO_NAME: z.optional(z.string()).default(""),
    INDEXER_CONFIG_FILE_PATH: z.optional(z.string()).default("indexer-service.yml"),
    SEARCH_CONFIG_FILE_PATH: z.optional(z.string()).default("search-service.yml"),

    NEXTAUTH_SECRET: z.optional(z.string()).default(""),
    NEXTAUTH_URL: z.optional(z.string()).default("http://localhost:3000"),

    PRODUCTSETSERVICE_BASEURL: z.optional(z.string()),
  },
  runtimeEnv: {
    SUGGEST_API_AUTH: process.env.SUGGEST_API_AUTH,
    SUGGEST_API_URL: process.env.SUGGEST_API_URL,

    SEARCH_API_AUTH: process.env.SEARCH_API_AUTH,
    SEARCH_API_URL: process.env.SEARCH_API_URL,

    ENABLE_SUGGEST_API: process.env.ENABLE_SUGGEST_API,

    DEFAULT_TENANT: process.env.DEFAULT_TENANT,
    BASEPATH: process.env.BASEPATH,

    GITHUB_OAUTH_ID: process.env.GITHUB_OAUTH_ID,
    GITHUB_OAUTH_SECRET: process.env.GITHUB_OAUTH_SECRET,
    DEFAULT_CONFIG_REPO_OWNER: process.env.DEFAULT_CONFIG_REPO_OWNER,
    DEFAULT_CONFIG_REPO_NAME: process.env.DEFAULT_CONFIG_REPO_NAME,
    INDEXER_CONFIG_FILE_PATH: process.env.INDEXER_CONFIG_FILE_PATH,
    SEARCH_CONFIG_FILE_PATH: process.env.SEARCH_CONFIG_FILE_PATH,

    NEXTAUTH_SECRET: process.env.NEXTAUTH_SECRET,
    NEXTAUTH_URL: process.env.NEXTAUTH_URL,

    PRODUCTSETSERVICE_BASEURL: process.env.PRODUCTSETSERVICE_BASEURL,
  },
})
