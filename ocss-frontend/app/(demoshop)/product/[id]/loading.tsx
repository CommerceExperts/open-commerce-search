import { Icons } from "@/components/misc/icons"

export default function ProductPageLoading() {
  return (
    <div className="mt-[40vh] flex items-center justify-center">
      <Icons.loader className="size-12 animate-spin" />
    </div>
  )
}
