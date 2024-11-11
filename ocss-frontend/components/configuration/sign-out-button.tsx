"use client"

import { LogOutIcon } from "lucide-react"
import { signOut } from "next-auth/react"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"

type SignOutButtonProps = {
  className: string
}

export default function SignOutButton({ className }: SignOutButtonProps) {
  return (
    <Button
      variant="destructive"
      className={cn("my-4 flex w-full gap-2", className)}
      onClick={() => signOut()}
    >
      <LogOutIcon className="w-4" />
      Sign out
    </Button>
  )
}
