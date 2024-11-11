"use client"

import { signIn } from "next-auth/react"

import { Button } from "@/components/ui/button"
import { Icons } from "@/components/misc/icons"

export default function SignInButton() {
  return (
    <Button
      className="my-4 flex w-full gap-2"
      onClick={() =>
        signIn("github", {
          callbackUrl: "/",
        })
      }
    >
      <Icons.github className="w-2" />
      Sign in with Github
    </Button>
  )
}
