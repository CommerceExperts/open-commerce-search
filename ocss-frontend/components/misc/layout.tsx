import { SiteHeader } from "@/components/misc/site-header"

type LayoutProps = {
  children: React.ReactNode
}

export function Layout({ children }: LayoutProps) {
  return (
    <>
      <SiteHeader />
      <main>{children}</main>
    </>
  )
}
