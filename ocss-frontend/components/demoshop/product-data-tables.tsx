import { Product } from "@/types/api"
import { Separator } from "@/components/ui/separator"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

interface ProductDataTablesProps {
  product: Product
}

export default function ProductDataTables({ product }: ProductDataTablesProps) {
  return (
    <>
      <Separator />
      <h2 className="text-lg font-bold">Data ({product.id})</h2>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[100px]">Field</TableHead>
            <TableHead>Data</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {Object.keys(product.data).map((key) => (
            <TableRow>
              <TableCell className="font-medium">{key}</TableCell>
              <TableCell className="break-all">
                {JSON.stringify(product.data[key])}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Separator />

      {product.variants &&
        product.variants.map((variant, i) => (
          <>
            <h2 className="text-lg font-bold">
              {i + 1}. Variant data ({variant.id})
            </h2>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[100px]">Field</TableHead>
                  <TableHead>Data</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {Object.keys(variant.data).map((key) => (
                  <TableRow>
                    <TableCell className="font-medium">{key}</TableCell>
                    <TableCell className="break-all">
                      {JSON.stringify(variant.data[key] as any)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Separator />
          </>
        ))}
    </>
  )
}
