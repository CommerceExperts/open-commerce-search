import { IconDeviceFloppy } from "@tabler/icons-react";
import { Button } from "../ui/button";

export function HistoryButton(props: React.ComponentProps<"button">) {
  return (
    <Button disabled={true} className="w-min" {...props}>
      <IconDeviceFloppy />
      <span className="hidden lg:inline">Save changes</span>
    </Button>
  );
}
