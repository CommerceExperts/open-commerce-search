"use client"

import { signIn, signOut } from "next-auth/react"

import { Button } from "../ui/button"

export const LoginButton = () => {
  return (
    <Button onClick={() => signIn("github", undefined, {})}>Sign in</Button>
  )
}

export const LogoutButton = () => {
  return <Button onClick={() => signOut()}>Sign Out</Button>
}
