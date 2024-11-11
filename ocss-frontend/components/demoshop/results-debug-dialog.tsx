import { Info } from "lucide-react"

import { SearchResult } from "@/types/api"
import { cn } from "@/lib/utils"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

import { buttonVariants } from "../ui/button"
import { Textarea } from "../ui/textarea"

type ResultsDebugDialogProps = {
  meta: { [key: string]: object }
  searchResult?: SearchResult
  className?: string
}

export default function ResultsDebugDialogButton({
  meta,
  searchResult,
  className,
}: ResultsDebugDialogProps) {
  return (
    <Dialog>
      <DialogTrigger>
        <div className={cn(buttonVariants(), "h-10 w-10 p-1", className)}>
          <Info className="h-4 w-4" />
        </div>
      </DialogTrigger>
      <DialogContent className="max-h-screen overflow-y-scroll lg:max-w-screen-sm">
        <DialogHeader>
          <DialogDescription>
            <h3 className="text-lg font-bold text-primary">Meta</h3>

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[100px]">Field</TableHead>
                  <TableHead>Data</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {Object.keys(meta).map((key) => (
                  <TableRow>
                    <TableCell className="font-medium">{key}</TableCell>
                    <TableCell className="break-all">
                      {JSON.stringify(meta[key] as any)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <h3 className="mt-4 text-lg font-bold text-primary">
              Raw response
            </h3>
            <Textarea className="h-[250px]" readOnly>
              {JSON.stringify(searchResult, null, 2)}
            </Textarea>
          </DialogDescription>
        </DialogHeader>
      </DialogContent>
    </Dialog>
  )
}
