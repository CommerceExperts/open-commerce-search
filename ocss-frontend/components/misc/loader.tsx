import { Icons } from "@/components/misc/icons"

export default function Loader() {
  return (
    <div className="my-[20vh] flex items-center justify-center">
      <Icons.loader className="h-12 w-12 animate-spin" />
    </div>
  )
}
