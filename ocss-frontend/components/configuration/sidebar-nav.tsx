"use client"

import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"

import { SearchParamsMap } from "@/types/searchParams"
import { cn } from "@/lib/utils"
import { buttonVariants } from "@/components/ui/button"

interface SidebarNavProps extends React.HTMLAttributes<HTMLElement> {
  items: {
    href: string
    title: string
  }[]
}

export function SidebarNav({ className, items, ...props }: SidebarNavProps) {
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const configParam = searchParams.get(SearchParamsMap.config)
  const commitParam = searchParams.get(SearchParamsMap.commit)

  return (
    <nav
      className={cn(
        "flex space-x-2 lg:flex-col lg:space-x-0 lg:space-y-1",
        className
      )}
      {...props}
    >
      {items.map((item) => (
        <Link
          key={item.href}
          href={
            item.href +
            (!configParam && !commitParam
              ? ""
              : "/?" +
                (configParam && commitParam
                  ? new URLSearchParams([
                      [SearchParamsMap.config, configParam],
                      [SearchParamsMap.commit, commitParam],
                    ])
                  : configParam && !commitParam
                  ? new URLSearchParams([[SearchParamsMap.config, configParam]])
                  : !configParam && commitParam
                  ? new URLSearchParams([[SearchParamsMap.commit, commitParam]])
                  : ""))
          }
          className={cn(
            buttonVariants({ variant: "ghost" }),
            pathname === item.href
              ? "bg-muted hover:bg-muted"
              : "hover:bg-transparent hover:underline",
            "justify-start"
          )}
        >
          {item.title}
        </Link>
      ))}
    </nav>
  )
}
