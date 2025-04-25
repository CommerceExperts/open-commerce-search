import { IconSearch } from "@tabler/icons-react";
import { Input } from "../ui/input";
import { cn } from "@/lib/utils";

export function SearchInput(props: React.ComponentProps<"div">) {
  return (
    <div
      {...props}
      className={cn(
        props.className,
        "relative"
      )}
    >
      <div className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground">
        <IconSearch className="h-4 w-4" />
      </div>
      <Input
        id="search"
        type="search"
        placeholder="Enter search"
        className="w-full bg-background pl-8"
      />
    </div>
  );
}
