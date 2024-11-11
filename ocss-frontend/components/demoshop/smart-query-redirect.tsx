"use client"

import { useEffect, useState } from "react"
import Link from "next/link"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

type SmartQueryRedirectProps = {
  meta: { [key: string]: object }
}

export default function SmartQueryRedirect({ meta }: SmartQueryRedirectProps) {
  const smartQueryRedirectURL: string | undefined =
    (meta.searchhub_redirect_url as unknown as string) ?? undefined
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (smartQueryRedirectURL) {
      setOpen(true)
    }
  }, [])

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Redirect to searchhub smartQuery?</AlertDialogTitle>
          <AlertDialogDescription>
            Do you want to redirect to the searchhub smartQuery{" "}
            {`"${smartQueryRedirectURL}"`}?
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <Link target="_blank" href={smartQueryRedirectURL}>
            <AlertDialogAction>Redirect</AlertDialogAction>
          </Link>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
