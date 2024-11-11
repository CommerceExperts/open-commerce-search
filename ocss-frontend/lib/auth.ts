import { env } from "@/env.mjs"
import type { NextAuthOptions } from "next-auth"
import GithubProvider from "next-auth/providers/github"

export const authOptions: NextAuthOptions = {
  providers: [
    GithubProvider({
      clientId: env.GITHUB_OAUTH_ID,
      clientSecret: env.GITHUB_OAUTH_SECRET,
      authorization: {
        url: "https://github.com/login/oauth/authorize",
        params: { scope: "user repo repo_deployment" },
      },
    }),
  ],
  pages: {
    signIn: "/signin",
  },
  callbacks: {
    async session({ session, token }) {
      // @ts-ignore
      session.user.id = token.id
      // @ts-ignore
      session.accessToken = token.accessToken

      const res = await fetch(`https://api.github.com/user/${token.id}`, {
        headers: {
          Authorization: `Bearer ${token.accessToken}`,
        },
      })
      const data = await res.json()
      // @ts-ignore
      session.user.login = data.login

      return session
    },
    async jwt({ token, user, account }) {
      if (user) {
        token.id = user.id
      }
      if (account) {
        token.accessToken = account.access_token
      }
      return token
    },
  },
}
