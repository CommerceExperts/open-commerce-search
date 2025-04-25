import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../ui/select";

export function SortSelect(props: React.ComponentProps<"button">) {
  return (
    <Select defaultValue="relevance">
      <SelectTrigger size="sm" className="w-[180px]" {...props}>
        <SelectValue placeholder="Select sort" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="relevance">Relevance</SelectItem>
        <SelectItem value="price">Price ascending</SelectItem>
        <SelectItem value="-price">Price descending</SelectItem>
      </SelectContent>
    </Select>
  );
}
