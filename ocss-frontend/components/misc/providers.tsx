"use client"

import { Provider as JotaiProvider } from "jotai"

import { NextAuthProvider } from "./next-auth-provider"
import { ThemeProvider } from "./theme-provider"

type ProvidersProps = {
  children: React.ReactNode
}

export default function Providers({ children }: ProvidersProps) {
  return (
    <NextAuthProvider>
      <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
        <JotaiProvider>{children}</JotaiProvider>
      </ThemeProvider>
    </NextAuthProvider>
  )
}
