import { Icons } from "@/components/misc/icons"

export default function Loader() {
  return (
    <div className="my-[20vh] flex items-center justify-center">
      <Icons.loader className="size-12 animate-spin" />
    </div>
  )
}
