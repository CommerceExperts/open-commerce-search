"use client"

import { RecoilRoot } from "recoil"

import { NextAuthProvider } from "./next-auth-provider"
import { ThemeProvider } from "./theme-provider"

type ProvidersProps = {
  children: React.ReactNode
}

export default function Providers({ children }: ProvidersProps) {
  return (
    <NextAuthProvider>
      <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
        <RecoilRoot>{children}</RecoilRoot>
      </ThemeProvider>
    </NextAuthProvider>
  )
}
