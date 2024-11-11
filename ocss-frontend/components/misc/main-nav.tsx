import Image from "next/image"
import Link from "next/link"

import { siteConfig } from "@/config/site"
import { getTenants } from "@/lib/search-api"
import { Icons } from "@/components/misc/icons"

import DemoshopButton from "../demoshop/demoshop-button"
import ConfigButton from "./config-button"

export async function MainNav() {
  const tenants = await getTenants()

  return (
    <div className="flex gap-6 md:gap-10">
      <Link href="/" className="flex items-center space-x-2">
        <Image src={Icons.logoUrl} alt="OCSS Logo" width={40} height={40} />
        <span className="hidden select-none font-extrabold sm:inline-block">
          {siteConfig.name}
        </span>
      </Link>
      <nav className="flex items-center gap-6">
        <DemoshopButton tenants={tenants} />
        <ConfigButton />
      </nav>
    </div>
  )
}
